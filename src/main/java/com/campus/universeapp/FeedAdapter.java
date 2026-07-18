package com.campus.universeapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.target.Target;

import android.graphics.drawable.Drawable;
import de.hdodenhof.circleimageview.CircleImageView;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.nativead.MediaView;

import com.campus.universeapp.model.AdItem;
import com.campus.universeapp.model.FeedItem;
import com.campus.universeapp.model.PostItem;
import com.campus.universeapp.model.NativeAdItem;
import com.campus.universeapp.AdManager;
import com.campus.universeapp.StoryRepository.UserStoryGroup;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Unified RecyclerView Adapter for feed with multiple ViewTypes:
 * - Stories (horizontal RecyclerView)
 * - Posts
 * - Native Ads
 * - Sponsored Ads
 * - Loading indicator
 * - Empty state
 * - Error state
 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

	private final Context context;
	private final List<FeedItem> feedList;
	private final String userUid;
	private final String userName;
	private final String currentUid;

	private final Set<String> likedPosts = new HashSet<>();
	private final Set<String> expandedPosts = new HashSet<>();

	private final DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("Posts");
	private final DatabaseReference likesRef = FirebaseDatabase.getInstance().getReference("Likes");
	private final DatabaseReference commentsRef = FirebaseDatabase.getInstance().getReference("Comments");

	// Callback for pagination trigger
	private OnLoadMoreListener loadMoreListener;

	public interface OnLoadMoreListener {
		void onLoadMore();
	}

	public FeedAdapter(Context context, List<FeedItem> feedList, String userUid, String userName) {
		this.context = context;
		this.feedList = feedList;
		this.userUid = userUid;
		this.userName = userName;
		this.currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
			? FirebaseAuth.getInstance().getCurrentUser().getUid()
			: "";
		loadUserLikesPipeline();
	}

	public void setOnLoadMoreListener(OnLoadMoreListener listener) {
		this.loadMoreListener = listener;
	}

	@Override
	public int getItemViewType(int position) {
		if (position >= 0 && position < feedList.size()) {
			return feedList.get(position).getItemType();
		}
		return FeedItem.TYPE_EMPTY;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(context);

		switch (viewType) {
			case FeedItem.TYPE_STORIES:
				return new StoriesViewHolder(
					inflater.inflate(R.layout.item_stories_container, parent, false)
				);
			case FeedItem.TYPE_NATIVE_AD:
				return new NativeAdViewHolder(
					inflater.inflate(R.layout.item_native_ad, parent, false)
				);
			case FeedItem.TYPE_SPONSORED_AD:
				return new SponsoredAdViewHolder(
					inflater.inflate(R.layout.item_custom_ad, parent, false)
				);
			case FeedItem.TYPE_LOADING:
				return new LoadingViewHolder(
					inflater.inflate(R.layout.item_loading, parent, false)
				);
			case FeedItem.TYPE_ERROR:
				return new ErrorViewHolder(
					inflater.inflate(R.layout.item_error, parent, false)
				);
			case FeedItem.TYPE_EMPTY:
				return new EmptyViewHolder(
					inflater.inflate(R.layout.item_empty_state, parent, false)
				);
			case FeedItem.TYPE_POST:
			default:
				return new PostViewHolder(
					inflater.inflate(R.layout.post_item, parent, false)
				);
		}
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		FeedItem item = feedList.get(position);

		// Trigger load more when near end
		if (position >= feedList.size() - 3 && loadMoreListener != null) {
			loadMoreListener.onLoadMore();
		}

		if (holder instanceof StoriesViewHolder) {
			((StoriesViewHolder) holder).bind((FeedItem.StoriesItem) item);
		} else if (holder instanceof NativeAdViewHolder) {
			((NativeAdViewHolder) holder).bind();
		} else if (holder instanceof SponsoredAdViewHolder) {
			((SponsoredAdViewHolder) holder).bind((AdItem) item);
		} else if (holder instanceof PostViewHolder) {
			((PostViewHolder) holder).bind((PostItem) item, position);
		} else if (holder instanceof ErrorViewHolder) {
			((ErrorViewHolder) holder).bind((FeedItem.ErrorItem) item);
		}
		// LoadingViewHolder and EmptyViewHolder need no binding
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
	                              @NonNull List<Object> payloads) {
		if (!payloads.isEmpty() && holder instanceof PostViewHolder && position < feedList.size()) {
			((PostViewHolder) holder).updateLikeUI((PostItem) feedList.get(position));
		} else {
			super.onBindViewHolder(holder, position, payloads);
		}
	}

	@Override
	public int getItemCount() {
		return feedList.size();
	}

	// ==================== STORIES VIEW HOLDER ====================
	private class StoriesViewHolder extends RecyclerView.ViewHolder {
		RecyclerView storiesRecycler;
		StoryAdapter storyAdapter;

		StoriesViewHolder(@NonNull View v) {
			super(v);
			storiesRecycler = v.findViewById(R.id.recyclerStories);
			storiesRecycler.setLayoutManager(
				new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
			);
		}

		void bind(FeedItem.StoriesItem storiesItem) {
			// Refresh adapter with current stories
			storyAdapter = new StoryAdapter(
				context,
				(List<UserStoryGroup>) (List<?>) storiesItem.stories,
				userUid,
				() -> {} // FAB menu toggle callback if needed
			);
			storiesRecycler.setAdapter(storyAdapter);
		}
	}

	// ==================== POST VIEW HOLDER ====================
	private class PostViewHolder extends RecyclerView.ViewHolder {
		final TextView tvUserName, tvTime, tvContent, tvLikes, tvCommentCount;
		final ImageButton btnLike, btnComment, btnMore, btnShare;
		final ImageView imgProfile, imgVerified, imgPostContent;
		final FrameLayout bgContainer;
		private ValueEventListener activeCommentListener;

		PostViewHolder(@NonNull View v) {
			super(v);
			tvUserName = v.findViewById(R.id.tvUserName);
			tvTime = v.findViewById(R.id.tvTime);
			tvContent = v.findViewById(R.id.tvContent);
			tvLikes = v.findViewById(R.id.tvLikes);
			tvCommentCount = v.findViewById(R.id.tvCommentCount);
			btnLike = v.findViewById(R.id.btnLike);
			btnComment = v.findViewById(R.id.btnComment);
			btnShare = v.findViewById(R.id.btnShare);
			btnMore = v.findViewById(R.id.btnMore);
			imgProfile = v.findViewById(R.id.imgProfile);
			imgVerified = v.findViewById(R.id.imgVerified);
			bgContainer = v.findViewById(R.id.bgContainer);
			imgPostContent = v.findViewById(R.id.imgPostContent);
		}

		void bind(PostItem post, int position) {
			resetViewState();
			tvUserName.setText(post.getUserName());
			updateLikeUI(post);

			// Load user verification status
			FirebaseDatabase.getInstance().getReference("Users").child(post.getUid())
				.addListenerForSingleValueEvent(new ValueEventListener() {
					@Override
					public void onDataChange(@NonNull DataSnapshot snapshot) {
						boolean isPremium = snapshot.child("premium").exists() && Boolean.TRUE.equals(snapshot.child("premium").getValue(Boolean.class));
						String email = snapshot.child("email").getValue(String.class);
						if ("inura282@gmail.com".equalsIgnoreCase(email)) {
							imgVerified.setVisibility(View.VISIBLE);
							imgVerified.setImageResource(R.drawable.ic_verified_black);
						} else if (isPremium) {
							imgVerified.setVisibility(View.VISIBLE);
							imgVerified.setImageResource(R.drawable.ic_verified);
						} else {
							imgVerified.setVisibility(View.GONE);
						}
					}
					@Override public void onCancelled(@NonNull DatabaseError error) {}
				});

			// Load profile image
			if (post.getPostUserImage() != null && !post.getPostUserImage().isEmpty()) {
				Glide.with(context)
					.load(post.getPostUserImage())
					.placeholder(R.drawable.ic_profile_placeholder)
					.error(R.drawable.ic_profile_placeholder)
					.into(imgProfile);
			} else {
				imgProfile.setImageResource(R.drawable.ic_profile_placeholder);
			}

			// Format time text
			tvTime.setText(getTimeAgo(post.getTimestamp()));

			boolean isEdited = post.isEdit();
			boolean isRepost = post.isRepost();

			String timeText = getTimeAgo(post.getTimestamp());

			if (isEdited && isRepost) {
				tvTime.setText("Reposted · Edited · " + timeText);
			} else if (isEdited) {
				tvTime.setText("Edited · " + timeText);
			} else if (isRepost) {
				tvTime.setText("Reposted · " + timeText);
			} else {
				tvTime.setText(timeText);
			}

			// Render post content
			if ("image".equalsIgnoreCase(post.getType())) {
				renderImagePost(post);
			} else {
				renderTextPost(post, position);
			}

			attachInteractionListeners(post, position);
			syncCommentsCount(post.getId());
		}

		private void resetViewState() {
			tvContent.setText("");
			tvLikes.setText("0 likes");
			tvCommentCount.setText("0 Comments");
			ViewGroup.LayoutParams lp = bgContainer.getLayoutParams();
			lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
			bgContainer.setLayoutParams(lp);
			bgContainer.setBackgroundColor(Color.WHITE);
			tvContent.setVisibility(View.VISIBLE);
			tvContent.setTypeface(Typeface.DEFAULT);
			tvContent.setTextSize(16);
			tvContent.setAutoLinkMask(0);
			tvContent.setMovementMethod(null);

			if (imgPostContent != null) {
				Glide.with(context).clear(imgPostContent);
				imgPostContent.setImageDrawable(null);
				imgPostContent.setVisibility(View.GONE);
			}
		}

		private void renderImagePost(PostItem post) {
			if (imgPostContent != null) {
				imgPostContent.setVisibility(View.VISIBLE);

				Glide.with(context)
					.load(post.getImageUrl())
					.placeholder(R.drawable.placeholder_image)
					.error(R.drawable.image_error)
					.into(imgPostContent);

				imgPostContent.setOnClickListener(v -> {
					Intent intent = new Intent(context, ViewImageActivity.class);
					intent.putExtra("imageUrl", post.getImageUrl());
					intent.putExtra("caption", post.getCaption());
					intent.putExtra("time", getTimeAgo(post.getTimestamp()));
					intent.putExtra("userName", post.getUserName());
					context.startActivity(intent);
				});
			}

			tvContent.setTextColor(Color.BLACK);
			tvContent.setTextSize(15);
			tvContent.setGravity(Gravity.START);

			String caption = post.getCaption() != null ? post.getCaption() : "";

			tvContent.setAutoLinkMask(
				Linkify.WEB_URLS |
				Linkify.EMAIL_ADDRESSES |
				Linkify.PHONE_NUMBERS
			);

			if (caption.isEmpty()) {
				tvContent.setVisibility(View.GONE);
			} else {
				tvContent.setVisibility(View.VISIBLE);

				if (caption.length() > 150) {
					if (expandedPosts.contains(post.getId())) {
						tvContent.setText(caption);
					} else {
						String preview = caption.substring(0, 150).trim() + "...";
						SpannableString spannable = new SpannableString(preview + " Read more");

						spannable.setSpan(
							new ForegroundColorSpan(Color.parseColor("#1B5E20")),
							preview.length(),
							spannable.length(),
							Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
						);

						spannable.setSpan(
							new StyleSpan(Typeface.BOLD),
							preview.length(),
							spannable.length(),
							Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
						);

						spannable.setSpan(
							new ClickableSpan() {
								@Override
								public void onClick(@NonNull View widget) {
									expandedPosts.add(post.getId());
									notifyItemChanged(getBindingAdapterPosition());
								}

								@Override
								public void updateDrawState(@NonNull TextPaint ds) {
									ds.setUnderlineText(false);
								}
							},
							preview.length(),
							spannable.length(),
							Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
						);

						tvContent.setText(spannable);
					}
				} else {
					tvContent.setText(caption);
				}

				tvContent.setMovementMethod(LinkMovementMethod.getInstance());
			}
		}

		private void renderTextPost(PostItem post, int position) {
			String content = post.getContent() != null ? post.getContent() : "";
			boolean isShort = content.length() <= 300;
			applyFontStyle(tvContent, post.getFontStyle());

			if (isShort) {
				ViewGroup.LayoutParams lp = bgContainer.getLayoutParams();
				lp.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, context.getResources().getDisplayMetrics());
				bgContainer.setLayoutParams(lp);
				int bgId = context.getResources().getIdentifier(
					post.getBackground(),
					"drawable",
					context.getPackageName()
				);

				if (bgId != 0) {
					bgContainer.setBackgroundResource(bgId);
				} else {
					bgContainer.setBackgroundResource(R.drawable.gradient_default);
				}

				tvContent.setTextColor(post.getTextColor());
				tvContent.setTextSize(20);
				tvContent.setGravity(Gravity.CENTER);
				tvContent.setText(content);
			} else {
				tvContent.setTextColor(Color.BLACK);
				tvContent.setTypeface(Typeface.DEFAULT);
				bgContainer.setBackgroundColor(Color.WHITE);
				tvContent.setTextSize(18);
				tvContent.setGravity(Gravity.START | Gravity.TOP);
				tvContent.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);

				if (expandedPosts.contains(post.getId())) {
					tvContent.setText(content);
				} else {
					String preview = content.substring(0, 200).trim() + "...";
					SpannableString spannable = new SpannableString(preview + " Read more");

					spannable.setSpan(
						new ForegroundColorSpan(Color.BLACK),
						0,
						preview.length(),
						Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
					);

					spannable.setSpan(
						new ForegroundColorSpan(Color.parseColor("#1B5E20")),
						preview.length(),
						spannable.length(),
						Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
					);

					spannable.setSpan(
						new StyleSpan(Typeface.BOLD),
						preview.length(),
						spannable.length(),
						Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
					);

					spannable.setSpan(new ClickableSpan() {
						@Override
						public void onClick(@NonNull View widget) {
							expandedPosts.add(post.getId());
							notifyItemChanged(getBindingAdapterPosition());
						}

						@Override
						public void updateDrawState(@NonNull TextPaint ds) {
							ds.setUnderlineText(false);
						}
					},
					preview.length(),
					spannable.length(),
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

					tvContent.setText(spannable);
				}
				tvContent.setMovementMethod(LinkMovementMethod.getInstance());
			}
		}

		void updateLikeUI(PostItem post) {
			btnLike.setImageResource(likedPosts.contains(post.getId()) ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
			tvLikes.setText(formatLikes(post.getLikes()) + " likes");
		}

		private void syncCommentsCount(String postId) {
			if (activeCommentListener != null) {
				commentsRef.child(postId).removeEventListener(activeCommentListener);
			}
			activeCommentListener = new ValueEventListener() {
				@Override
				public void onDataChange(@NonNull DataSnapshot snap) {
					tvCommentCount.setText(formatCount(snap.getChildrenCount()) + " Comments");
				}
				@Override public void onCancelled(@NonNull DatabaseError error) {}
			};
			commentsRef.child(postId).addValueEventListener(activeCommentListener);
		}

		private void attachInteractionListeners(PostItem post, int position) {
			tvUserName.setOnClickListener(v -> openUserProfile(post.getUid()));
			imgProfile.setOnClickListener(v -> openUserProfile(post.getUid()));

			android.view.GestureDetector gd = new android.view.GestureDetector(context, new android.view.GestureDetector.SimpleOnGestureListener() {
				@Override public boolean onDoubleTap(@NonNull MotionEvent e) {
					toggleLikePipeline(post, position);
					return true;
				}
			});
			bgContainer.setOnTouchListener((v, e) -> { gd.onTouchEvent(e); return true; });
			btnLike.setOnClickListener(v -> toggleLikePipeline(post, position));
			btnComment.setOnClickListener(v -> CommentsBottomSheet.newInstance(post.getId()).show(((FragmentActivity) context).getSupportFragmentManager(), "comments"));
			btnShare.setOnClickListener(v -> {
				Intent i = new Intent(Intent.ACTION_SEND);
				i.setType("text/plain");
				i.putExtra(Intent.EXTRA_TEXT, "🔥 Check out this post on Universe:\nhttps://app.universe.me/" + post.getId());
				context.startActivity(Intent.createChooser(i, "Share via"));
			});
			btnMore.setOnClickListener(v -> launchOverflowMenu(v, post, position));
		}

		private void launchOverflowMenu(View v, PostItem post, int position) {
			PopupMenu menu = new PopupMenu(context, v);
			menu.getMenu().add("Copy Text");
			menu.getMenu().add("Repost");
			if (!"image".equalsIgnoreCase(post.getType())) menu.getMenu().add("Save Image");
			if (post.getUid().equals(userUid)) {
				menu.getMenu().add("Edit Post");
				menu.getMenu().add("Delete Post");
			}

			menu.setOnMenuItemClickListener(item -> {
				String title = item.getTitle().toString();
				String textData = "image".equalsIgnoreCase(post.getType()) ? post.getCaption() : post.getContent();

				switch (title) {
					case "Copy Text":
						android.content.ClipboardManager cm = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
						if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Post", textData));
						AppSnackbar.success(itemView, "Text copied successfully");
						break;
					case "Repost":
						if ("image".equalsIgnoreCase(post.getType())) {
							Intent repost = new Intent(context, RepostImageActivity.class);
							repost.putExtra("repostText", post.getCaption());
							repost.putExtra("userName", post.getUserName());
							repost.putExtra("userUid", post.getUid());
							repost.putExtra("imageUrl", post.getImageUrl());
							context.startActivity(repost);
						} else {
							Intent repost = new Intent(context, RepostTextActivity.class);
							repost.putExtra("repostText", post.getContent());
							repost.putExtra("userName", post.getUserName());
							repost.putExtra("userUid", post.getUid());
							repost.putExtra("textColor", post.getTextColor());
							repost.putExtra("fontStyle", post.getFontStyle());
							repost.putExtra("background", post.getBackground());
							context.startActivity(repost);
						}
						break;
					case "Save Image":
						ImageHelper.savePostViewAsImage(bgContainer, context);
						break;
					case "Edit Post":
						if ("image".equalsIgnoreCase(post.getType())) {
							Intent i = new Intent(context, EditPostImageActivity.class);
							i.putExtra("postId", post.getId());
							i.putExtra("oldContent", post.getCaption());
							i.putExtra("imageUrl", post.getImageUrl());
							context.startActivity(i);
						} else {
							Intent i = new Intent(context, EditPostTextActivity.class);
							i.putExtra("postId", post.getId());
							i.putExtra("oldContent", post.getContent());
							i.putExtra("oldTextColor", post.getTextColor());
							i.putExtra("oldFontStyle", post.getFontStyle());
							i.putExtra("oldBackground", post.getBackground());
							context.startActivity(i);
						}
						break;
					case "Delete Post":
						new MaterialAlertDialogBuilder(context)
							.setTitle("Delete Post")
							.setMessage("Confirm permanent deletion?")
							.setPositiveButton("Delete", (d, w) -> postsRef.child(post.getId()).removeValue())
							.setNegativeButton("Cancel", null).show();
						AppSnackbar.success(itemView, "Post deleted successfully");
						break;
				}
				return true;
			});
			menu.show();
		}
	}

	// ==================== SPONSORED AD VIEW HOLDER ====================
	private class SponsoredAdViewHolder extends RecyclerView.ViewHolder {
		final TextView title, desc;
		final ImageView img;
		final Button btn;

		SponsoredAdViewHolder(@NonNull View v) {
			super(v);
			title = v.findViewById(R.id.adTitle);
			desc = v.findViewById(R.id.adDesc);
			img = v.findViewById(R.id.adImage);
			btn = v.findViewById(R.id.adButton);
		}

		void bind(AdItem adItem) {
			Map<String, Object> raw = adItem.getRawData();
			title.setText(String.valueOf(raw.get("title")));
			desc.setText(String.valueOf(raw.get("description")));
			btn.setText(String.valueOf(raw.get("buttonText")));
			Glide.with(context).load(raw.get("imageUrl")).into(img);

			btn.setOnClickListener(v -> {
				String actionType = String.valueOf(raw.get("actionType"));
				String actionValue = String.valueOf(raw.get("actionValue"));

				if ("whatsapp".equalsIgnoreCase(actionType)) {
					try {
						context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + actionValue.replaceAll("[+\\s]", ""))));
					} catch (Exception e) {
						Toast.makeText(context, "WhatsApp unavailable", Toast.LENGTH_SHORT).show();
					}
				} else if ("link".equalsIgnoreCase(actionType)) {
					context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(actionValue)));
				}
			});
		}
	}

	// ==================== NATIVE AD VIEW HOLDER ====================
	private class NativeAdViewHolder extends RecyclerView.ViewHolder {
		NativeAdView nativeAdView;
		MediaView media;
		TextView headline;
		TextView body;
		Button cta;
		CircleImageView icon;
		TextView advertiser;

		NativeAdViewHolder(@NonNull View itemView) {
			super(itemView);
			nativeAdView = (NativeAdView) itemView;
			media = itemView.findViewById(R.id.ad_media);
			headline = itemView.findViewById(R.id.ad_headline);
			body = itemView.findViewById(R.id.ad_body);
			cta = itemView.findViewById(R.id.ad_call_to_action);
			icon = itemView.findViewById(R.id.ad_icon);
			advertiser = itemView.findViewById(R.id.ad_advertiser);
		}

		void bind() {
			NativeAd ad = AdManager.getNativeAd(getBindingAdapterPosition());

			if (ad == null) return;

			nativeAdView.setMediaView(media);

			if (ad.getMediaContent() != null) {
				media.setMediaContent(ad.getMediaContent());
			}

			headline.setText(ad.getHeadline());
			nativeAdView.setHeadlineView(headline);

			if (ad.getBody() != null) {
				body.setVisibility(View.VISIBLE);
				body.setText(ad.getBody());
				nativeAdView.setBodyView(body);
			} else {
				body.setVisibility(View.GONE);
			}

			if (ad.getIcon() != null) {
				icon.setImageDrawable(ad.getIcon().getDrawable());
				nativeAdView.setIconView(icon);
			} else {
				icon.setImageResource(R.drawable.ic_ads_google);
			}

			if (ad.getHeadline() != null) {
				advertiser.setText(ad.getHeadline());
				nativeAdView.setAdvertiserView(advertiser);
			} else {
				advertiser.setText("UniVerse Ads");
			}

			if (ad.getCallToAction() != null) {
				cta.setVisibility(View.VISIBLE);
				cta.setText(ad.getCallToAction());
				nativeAdView.setCallToActionView(cta);
			} else {
				cta.setVisibility(View.GONE);
			}

			nativeAdView.setNativeAd(ad);
		}
	}

	// ==================== LOADING VIEW HOLDER ====================
	private static class LoadingViewHolder extends RecyclerView.ViewHolder {
		LoadingViewHolder(@NonNull View itemView) {
			super(itemView);
		}
	}

	// ==================== EMPTY VIEW HOLDER ====================
	private static class EmptyViewHolder extends RecyclerView.ViewHolder {
		EmptyViewHolder(@NonNull View itemView) {
			super(itemView);
		}
	}

	// ==================== ERROR VIEW HOLDER ====================
	private static class ErrorViewHolder extends RecyclerView.ViewHolder {
		TextView errorText;

		ErrorViewHolder(@NonNull View itemView) {
			super(itemView);
			errorText = itemView.findViewById(R.id.errorText);
		}

		void bind(FeedItem.ErrorItem errorItem) {
			errorText.setText(errorItem.errorMessage != null ? errorItem.errorMessage : "An error occurred");
		}
	}

	// ==================== HELPER METHODS ====================

	private void toggleLikePipeline(PostItem post, int position) {
		DatabaseReference userLikeRef = likesRef.child(currentUid).child(post.getId());
		userLikeRef.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot snapshot) {
				boolean targetState = !snapshot.exists();
				if (targetState) {
					userLikeRef.setValue(true);
					likedPosts.add(post.getId());
					updateLikesCounterTx(post.getId(), 1);
				} else {
					userLikeRef.removeValue();
					likedPosts.remove(post.getId());
					updateLikesCounterTx(post.getId(), -1);
				}
				notifyItemChanged(position, "like_update");
			}
			@Override public void onCancelled(@NonNull DatabaseError error) {}
		});
	}

	private void updateLikesCounterTx(String postId, int delta) {
		postsRef.child(postId).child("likes").runTransaction(new Transaction.Handler() {
			@NonNull
			@Override
			public Transaction.Result doTransaction(@NonNull MutableData currentData) {
				Integer current = currentData.getValue(Integer.class);
				if (current == null) current = 0;
				currentData.setValue(Math.max(0, current + delta));
				return Transaction.success(currentData);
			}
			@Override public void onComplete(DatabaseError e, boolean c, DataSnapshot s) {}
		});
	}

	private void loadUserLikesPipeline() {
		likesRef.child(currentUid).addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot snapshot) {
				likedPosts.clear();
				for (DataSnapshot child : snapshot.getChildren()) {
					if (child.getKey() != null) likedPosts.add(child.getKey());
				}
				notifyDataSetChanged();
			}
			@Override public void onCancelled(@NonNull DatabaseError error) {}
		});
	}

	private void openUserProfile(String postUid) {
		if (postUid.equals(userUid)) {
			context.startActivity(new Intent(context, MyProActivity.class));
		} else {
			Intent i = new Intent(context, UserProfileActivity.class);
			i.putExtra("uid", postUid);
			context.startActivity(i);
		}
	}

	private void applyFontStyle(TextView tv, int style) {
		tv.setAllCaps(false);
		tv.setTypeface(Typeface.DEFAULT);
		switch (style) {
			case 1: tv.setTypeface(Typeface.DEFAULT_BOLD); break;
			case 2: tv.setTypeface(Typeface.SERIF, Typeface.ITALIC); break;
			case 3: tv.setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC); break;
			case 4: tv.setTypeface(Typeface.MONOSPACE); break;
			case 5: tv.setTypeface(Typeface.SERIF); break;
			case 6: tv.setTypeface(Typeface.SANS_SERIF); break;
			case 7: tv.setTypeface(Typeface.create("cursive", Typeface.NORMAL)); break;
			case 8: tv.setAllCaps(true); break;
			case 9: tv.setTypeface(Typeface.SERIF, Typeface.BOLD); break;
		}
	}

	private String formatLikes(int likes) {
		if (likes >= 1_000_000) return String.format(Locale.US, "%.1fM", likes / 1_000_000.0);
		if (likes >= 1_000) return String.format(Locale.US, "%.1fK", likes / 1_000.0);
		return String.valueOf(likes);
	}

	private String formatCount(long count) {
		if (count >= 1_000_000) return String.format(Locale.US, "%.1fM", count / 1_000_000.0);
		if (count >= 1_000) return String.format(Locale.US, "%.1fK", count / 1_000.0);
		return String.valueOf(count);
	}

	private String getTimeAgo(long time) {
		long diff = System.currentTimeMillis() - time;
		long minutes = diff / (1000 * 60);
		long days = diff / (1000 * 60 * 60 * 24);

		if (minutes < 1) return "just now";
		if (minutes < 60) return minutes + "m";

		Calendar nowCal = Calendar.getInstance();
		Calendar msgCal = Calendar.getInstance();
		msgCal.setTimeInMillis(time);

		SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
		if (nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) && nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)) {
			return sdf.format(new Date(time));
		}
		if (days < 7) return new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(time));
		return new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date(time));
	}
}
