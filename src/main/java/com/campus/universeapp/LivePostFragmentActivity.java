package com.campus.universeapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import com.campus.universeapp.engine.FeedRankingEngine;
import com.campus.universeapp.model.AdItem;
import com.campus.universeapp.model.FeedItem;
import com.campus.universeapp.model.PostItem;
import com.campus.universeapp.model.NativeAdItem;
import com.campus.universeapp.StoryRepository.UserStoryGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Refactored LivePostFragmentActivity with single RecyclerView architecture
 * - Uses unified FeedAdapter with multiple ViewTypes
 * - Implements RecyclerView.OnScrollListener for cursor-based pagination
 * - Proper scroll position preservation
 * - No more NestedScrollView or dual RecyclerViews
 * - Stories as first feed item with horizontal RecyclerView inside
 */
public class LivePostFragmentActivity extends Fragment {

	private RecyclerView recyclerFeedMain;
	private SwipeRefreshLayout swipeRefresh;
	private TextView tvEmpty, txtTitle, tvNotificationCount;
	private View layoutNotification;

	private FloatingActionButton fabMain;
	private FloatingActionButton fabTextPost;
	private FloatingActionButton fabImagePost;
	private FloatingActionButton fabStoryText;
	private FloatingActionButton fabStoryImage;

	private LinearLayout layoutFabPostText;
	private LinearLayout layoutFabPostImage;
	private LinearLayout layoutFabStoryText;
	private LinearLayout layoutFabStoryImage;

	private Animation fabOpen, fabClose, rotateOpen, rotateClose;
	private boolean isFabExpanded = false;

	// Feed data
	private final List<FeedItem> masterFeedList = new ArrayList<>();
	private final List<PostItem> accumulatedPostPool = new ArrayList<>();
	private final List<AdItem> activeAdsPool = new ArrayList<>();
	private final List<UserStoryGroup> activeStoryGroups = new ArrayList<>();
	private final Set<String> trackLoadedIds = new HashSet<>();
	private FeedAdapter adapter;

	// Pagination state
	private DatabaseReference postRef;
	private DatabaseReference notifRef;
	private DatabaseReference adRef;
	private ValueEventListener notifListener;

	private String userUid = "";
	private String userName = "";

	private static final int PAGE_SIZE = 15;
	private boolean isLoading = false;
	private boolean hasMoreData = true;
	private long lastTimestampCursor = 0L;
	private String lastPostIdCursor = null;
	private long lastImagePostTime = 0;
	private boolean isPremium = false;

	// Background processing
	private final ExecutorService backgroundPool = Executors.newFixedThreadPool(2);
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	// Story repository
	private StoryRepository storyRepository;

	public LivePostFragmentActivity() {}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.live_post_fragment, container, false);
		initViews(view);
		setupDatabaseReferences();
		setupUserSession();
		setupUnifiedRecyclerView();
		attachInteractions();
		return view;
	}

	private void initViews(View view) {
		recyclerFeedMain = view.findViewById(R.id.recyclerFeedMain);
		swipeRefresh = view.findViewById(R.id.swipeRefresh);
		tvEmpty = view.findViewById(R.id.tvEmpty);
		txtTitle = view.findViewById(R.id.txtTitle);
		layoutNotification = view.findViewById(R.id.layoutNotification);
		tvNotificationCount = view.findViewById(R.id.tvNotificationCount);

		fabMain = view.findViewById(R.id.fabMain);
		fabTextPost = view.findViewById(R.id.fabPostText);
		fabImagePost = view.findViewById(R.id.fabPostImage);
		fabStoryText = view.findViewById(R.id.fabStoryText);
		fabStoryImage = view.findViewById(R.id.fabStoryImage);

		layoutFabPostText = view.findViewById(R.id.layoutFabPostText);
		layoutFabPostImage = view.findViewById(R.id.layoutFabPostImage);
		layoutFabStoryText = view.findViewById(R.id.layoutFabStoryText);
		layoutFabStoryImage = view.findViewById(R.id.layoutFabStoryImage);

		fabOpen = AnimationUtils.loadAnimation(getContext(), R.anim.fab_open);
		fabClose = AnimationUtils.loadAnimation(getContext(), R.anim.fab_close);
		rotateOpen = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_open);
		rotateClose = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_close);

		try {
			txtTitle.setTypeface(Typeface.createFromAsset(requireActivity().getAssets(), "fonts/ooo.ttf"), Typeface.BOLD);
		} catch (Exception ignored) {}
	}

	private void setupDatabaseReferences() {
		FirebaseDatabase database = FirebaseDatabase.getInstance();
		postRef = database.getReference("Posts");
		adRef = database.getReference("SponsoredAds");
		storyRepository = new StoryRepository();
	}

	private void setupUserSession() {
		FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
		if (user == null) {
			Toast.makeText(getContext(), "Session expired.", Toast.LENGTH_SHORT).show();
			return;
		}
		userUid = user.getUid();
		loadUserInfoSync();
	}

	/**
	 * Setup unified RecyclerView with single vertical layout and OnScrollListener pagination
	 */
	private void setupUnifiedRecyclerView() {
		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
		recyclerFeedMain.setLayoutManager(layoutManager);
		recyclerFeedMain.setHasFixedSize(false);

		adapter = new FeedAdapter(requireContext(), masterFeedList, userUid, userName);
		recyclerFeedMain.setAdapter(adapter);

		// Pagination trigger via OnScrollListener
		recyclerFeedMain.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);

				LinearLayoutManager llm = (LinearLayoutManager) recyclerView.getLayoutManager();
				if (llm != null) {
					int lastVisibleItem = llm.findLastVisibleItemPosition();
					int totalItemCount = adapter.getItemCount();

					// Trigger load more when within 3 items of end
					if (lastVisibleItem >= totalItemCount - 3 && !isLoading && hasMoreData) {
						Log.d("PAGINATION", "Loading next page at position: " + lastVisibleItem);
						fetchNextFeedBatch();
					}
				}
			}
		});

		// Set pagination callback on adapter
		adapter.setOnLoadMoreListener(() -> {
			if (!isLoading && hasMoreData) {
				fetchNextFeedBatch();
			}
		});
	}

	private void attachInteractions() {
		swipeRefresh.setOnRefreshListener(this::refreshFeedExecutionPipeline);

		fabMain.setOnClickListener(v -> toggleFabMenu());

		fabTextPost.setOnClickListener(v -> {
			toggleFabMenu();
			startActivity(new Intent(getActivity(), TextPostActivity.class));
		});

		fabImagePost.setOnClickListener(v -> {
			toggleFabMenu();
			startActivity(new Intent(getActivity(), ImagePostActivity.class));
		});

		fabStoryText.setOnClickListener(v -> {
			toggleFabMenu();
			startActivity(new Intent(getActivity(), StoryTextActivity.class));
		});

		fabStoryImage.setOnClickListener(v -> {
			toggleFabMenu();
			startActivity(new Intent(getActivity(), StoryImageActivity.class));
		});

		layoutNotification.setOnClickListener(v -> startActivity(new Intent(getActivity(), NotificationsActivity.class)));
		setupNotificationsPipeline();
	}

	private void toggleFabMenu() {
		if (isFabExpanded) {
			fabMain.startAnimation(rotateClose);

			layoutFabPostText.startAnimation(fabClose);
			layoutFabPostImage.startAnimation(fabClose);
			layoutFabStoryText.startAnimation(fabClose);
			layoutFabStoryImage.startAnimation(fabClose);

			layoutFabPostText.setVisibility(View.GONE);
			layoutFabPostImage.setVisibility(View.GONE);
			layoutFabStoryText.setVisibility(View.GONE);
			layoutFabStoryImage.setVisibility(View.GONE);

			isFabExpanded = false;
		} else {
			fabMain.startAnimation(rotateOpen);

			layoutFabPostText.setVisibility(View.VISIBLE);
			layoutFabPostImage.setVisibility(View.VISIBLE);
			layoutFabStoryText.setVisibility(View.VISIBLE);
			layoutFabStoryImage.setVisibility(View.VISIBLE);

			layoutFabPostText.startAnimation(fabOpen);
			layoutFabPostImage.startAnimation(fabOpen);
			layoutFabStoryText.startAnimation(fabOpen);
			layoutFabStoryImage.startAnimation(fabOpen);

			isFabExpanded = true;
		}
	}

	private void loadUserInfoSync() {
		FirebaseDatabase.getInstance().getReference("Users").child(userUid)
			.addListenerForSingleValueEvent(new ValueEventListener() {
				@Override
				public void onDataChange(@NonNull DataSnapshot snapshot) {
					if (!isAdded()) return;
					String name = snapshot.child("fullName").getValue(String.class);
					userName = (name != null && !name.isEmpty()) ? name : "User";

					Boolean premiumVal = snapshot.child("premium").getValue(Boolean.class);
					isPremium = (premiumVal != null) && premiumVal;

					Long lastImgTime = snapshot.child("lastImagePostTime").getValue(Long.class);
					lastImagePostTime = (lastImgTime != null) ? lastImgTime : 0;

					updateFabState();
					refreshFeedExecutionPipeline();
					fetchActiveAdsPool();
				}
				@Override public void onCancelled(@NonNull DatabaseError error) {}
			});
	}

	private synchronized void refreshFeedExecutionPipeline() {
		isLoading = true;
		hasMoreData = true;
		lastTimestampCursor = 0L;
		lastPostIdCursor = null;

		accumulatedPostPool.clear();
		masterFeedList.clear();
		trackLoadedIds.clear();
		activeStoryGroups.clear();

		tvEmpty.setVisibility(View.GONE);
		swipeRefresh.setRefreshing(true);

		// Load stories first
		loadStoriesData();
		// Then load posts
		fetchNextFeedBatch();
	}

	private void loadStoriesData() {
		storyRepository.loadFeedStories(userUid, new StoryRepository.OnStoriesLoadedListener() {
			@Override
			public void onLoaded(List<UserStoryGroup> groups) {
				if (!isAdded()) return;
				activeStoryGroups.clear();

				boolean hasCurrentUserGroup = false;
				for (UserStoryGroup g : groups) {
					if (g.userId.equals(userUid)) {
						hasCurrentUserGroup = true;
						break;
					}
				}

				if (!hasCurrentUserGroup) {
					UserStoryGroup mine = new UserStoryGroup();
					mine.userId = userUid;
					mine.userName = "Your Story";
					mine.stories = new ArrayList<>();
					mine.hasUnseen = false;
					activeStoryGroups.add(mine);
				}

				activeStoryGroups.addAll(groups);

				// Insert stories as first item if not already present
				if (masterFeedList.isEmpty() || masterFeedList.get(0).getItemType() != FeedItem.TYPE_STORIES) {
					FeedItem.StoriesItem storiesItem = new FeedItem.StoriesItem();
					storiesItem.stories = new ArrayList<>(activeStoryGroups);
					masterFeedList.add(0, storiesItem);
					adapter.notifyItemInserted(0);
				}
			}

			@Override
			public void onError(Exception e) {
				if (isAdded()) {
					Toast.makeText(getContext(), "Error loading stories: " + e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

	private void updateFabState() {
		long now = System.currentTimeMillis();

		long interval = isPremium
			? (24L * 60 * 60 * 1000)
			: (3L * 24 * 60 * 60 * 1000);

		boolean canPostImage = (now - lastImagePostTime) >= interval;

		if (canPostImage) {
			fabImagePost.setAlpha(1f);
			fabImagePost.setEnabled(true);
		} else {
			fabImagePost.setAlpha(0.4f);
			fabImagePost.setEnabled(false);
		}
	}

	/**
	 * Fetch next batch of posts using cursor-based pagination
	 */
	private void fetchNextFeedBatch() {
		Log.d("PAGINATION", "fetchNextFeedBatch called - isLoading: " + isLoading + ", hasMoreData: " + hasMoreData);
		isLoading = true;
		Query paginationQuery;

		if (lastPostIdCursor == null) {
			paginationQuery = postRef.orderByChild("timestamp").limitToLast(PAGE_SIZE);
		} else {
			paginationQuery = postRef.orderByChild("timestamp")
				.endAt(lastTimestampCursor, lastPostIdCursor)
				.limitToLast(PAGE_SIZE + 1);
		}

		paginationQuery.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot snapshot) {
				backgroundPool.execute(() -> processRawSnapshotInWorker(snapshot));
			}

			@Override
			public void onCancelled(@NonNull DatabaseError error) {
				mainHandler.post(() -> {
					isLoading = false;
					swipeRefresh.setRefreshing(false);
					Log.e("PAGINATION", "Query cancelled: " + error.getMessage());
				});
			}
		});
	}

	/**
	 * Process Firebase snapshot on background thread
	 */
	private void processRawSnapshotInWorker(DataSnapshot snapshot) {
		List<PostItem> batchList = new ArrayList<>();
		long earliestTimestamp = Long.MAX_VALUE;
		String earliestPostId = null;

		for (DataSnapshot ds : snapshot.getChildren()) {
			String pId = ds.getKey();
			if (pId == null) continue;

			// Skip if already loaded (pagination overlap)
			if (pId.equals(lastPostIdCursor) || trackLoadedIds.contains(pId)) {
				continue;
			}

			Object obj = ds.getValue();
			if (!(obj instanceof Map)) continue;

			@SuppressWarnings("unchecked")
			PostItem post = new PostItem(pId, (Map<String, Object>) obj);
			batchList.add(post);

			if (post.getTimestamp() < earliestTimestamp) {
				earliestTimestamp = post.getTimestamp();
				earliestPostId = post.getId();
			}
		}

		Log.d("PAGINATION", "Firebase snapshot children: " + snapshot.getChildrenCount());
		Log.d("PAGINATION", "Batch processed: " + batchList.size());
		Log.d("PAGINATION", "HasMore before: " + hasMoreData);

		if (batchList.isEmpty() || batchList.size() < PAGE_SIZE) {
			hasMoreData = false;
		}

		if (!batchList.isEmpty()) {
			lastTimestampCursor = earliestTimestamp;
			lastPostIdCursor = earliestPostId;
			accumulatedPostPool.addAll(batchList);
			for (PostItem p : batchList) trackLoadedIds.add(p.getId());
		}

		// Rank posts using FeedRankingEngine
		List<PostItem> rankedPosts = FeedRankingEngine.rankPosts(accumulatedPostPool, userUid);

		// Interweave posts with ads
		List<FeedItem> interwovenList = interweaveFeedComponents(rankedPosts, activeAdsPool);

		mainHandler.post(() -> renderCompiledFeed(interwovenList));
	}

	/**
	 * Interweave posts with native and sponsored ads
	 */
	private List<FeedItem> interweaveFeedComponents(List<PostItem> sortedPosts, List<AdItem> ads) {
		List<FeedItem> compiled = new ArrayList<>();

		// Add stories as first item if not already there
		if (masterFeedList.isEmpty() || masterFeedList.get(0).getItemType() != FeedItem.TYPE_STORIES) {
			FeedItem.StoriesItem storiesItem = new FeedItem.StoriesItem();
			storiesItem.stories = new ArrayList<>(activeStoryGroups);
			compiled.add(storiesItem);
		}

		int sponsoredIndex = 0;
		boolean showNativeNext = true;

		for (int i = 0; i < sortedPosts.size(); i++) {
			compiled.add(sortedPosts.get(i));

			// Insert ads every 6 posts
			if ((i + 1) % 6 == 0) {
				if (showNativeNext) {
					compiled.add(new NativeAdItem());
				} else {
					if (!ads.isEmpty()) {
						compiled.add(ads.get(sponsoredIndex % ads.size()));
						sponsoredIndex++;
					}
				}
				showNativeNext = !showNativeNext;
			}
		}

		return compiled;
	}

	/**
	 * Render compiled feed with proper notification handling
	 */
	private void renderCompiledFeed(List<FeedItem> updatedFeed) {
		if (!isAdded()) return;

		// Preserve stories item if exists
		int insertPosition = 0;
		if (!masterFeedList.isEmpty() && masterFeedList.get(0).getItemType() == FeedItem.TYPE_STORIES) {
			insertPosition = 1;
		}

		int oldSize = masterFeedList.size();
		masterFeedList.clear();
		masterFeedList.addAll(updatedFeed);

		// Use efficient notification instead of full refresh
		if (oldSize == 0) {
			adapter.notifyItemRangeInserted(0, masterFeedList.size());
		} else {
			adapter.notifyItemRangeInserted(insertPosition, updatedFeed.size() - insertPosition);
		}

		isLoading = false;
		swipeRefresh.setRefreshing(false);
		tvEmpty.setVisibility(masterFeedList.isEmpty() ? View.VISIBLE : View.GONE);

		Log.d("PAGINATION", "Feed rendered with " + masterFeedList.size() + " items");
	}

	/**
	 * Fetch active sponsored ads
	 */
	private void fetchActiveAdsPool() {
		adRef.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot snapshot) {
				backgroundPool.execute(() -> {
					long now = System.currentTimeMillis();
					List<AdItem> verifiedAds = new ArrayList<>();

					for (DataSnapshot ds : snapshot.getChildren()) {
						Object val = ds.getValue();
						if (!(val instanceof Map)) continue;

						@SuppressWarnings("unchecked")
						AdItem ad = new AdItem(ds.getKey(), (Map<String, Object>) val);
						if (ad.isActive(now)) {
							verifiedAds.add(ad);
						}
					}
					synchronized (activeAdsPool) {
						activeAdsPool.clear();
						activeAdsPool.addAll(verifiedAds);
					}
				});
			}
			@Override public void onCancelled(@NonNull DatabaseError error) {}
		});
	}

	/**
	 * Setup real-time notifications listener
	 */
	private void setupNotificationsPipeline() {
		notifRef = FirebaseDatabase.getInstance().getReference("Notifications").child(userUid);
		notifListener = new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot snapshot) {
				int count = 0;
				for (DataSnapshot ds : snapshot.getChildren()) {
					Boolean unread = ds.child("read").getValue(Boolean.class);
					if (unread != null && !unread) count++;
				}
				updateNotificationBadge(count);
			}
			@Override public void onCancelled(@NonNull DatabaseError error) {}
		};
		notifRef.addValueEventListener(notifListener);
	}

	private void updateNotificationBadge(int count) {
		if (count == 0) {
			tvNotificationCount.setVisibility(View.GONE);
		} else {
			tvNotificationCount.setVisibility(View.VISIBLE);
			tvNotificationCount.setText(count > 99 ? "99+" : String.valueOf(count));
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (notifRef != null && notifListener != null) {
			notifRef.removeEventListener(notifListener);
		}
		backgroundPool.shutdown();
	}
}
