package com.campus.universeapp.model;

import java.util.Map;

/**
 * Sponsored Ad Item extending FeedItem
 * Represents a sponsored advertisement in the feed with time-based activation
 */
public class AdItem extends FeedItem {
	private final String adId;
	private final String status;
	private final long startAt;
	private final long endAt;
	private final Map<String, Object> rawData;

	public AdItem(String adId, Map<String, Object> map) {
		this.adId = adId;
		this.status = String.valueOf(map.get("status"));

		Object start = map.get("startAt");
		this.startAt = (start instanceof Long) ? (Long) start : 0L;

		Object end = map.get("endAt");
		this.endAt = (end instanceof Long) ? (Long) end : 0L;

		this.rawData = map;
	}

	@Override
	public String getId() {
		return adId;
	}

	@Override
	public int getItemType() {
		return TYPE_SPONSORED_AD;
	}

	/**
	 * Check if ad is currently active based on time window and status
	 */
	public boolean isActive(long now) {
		return "active".equalsIgnoreCase(status) && startAt <= now && endAt >= now;
	}

	public Map<String, Object> getRawData() {
		return rawData;
	}
}
