package com.campus.universeapp.model;

/**
 * Native Ad Item extending FeedItem
 * Represents a Google Native Ad in the feed
 */
public class NativeAdItem extends FeedItem {

	@Override
	public String getId() {
		return "native_ad_" + System.currentTimeMillis();
	}

	@Override
	public int getItemType() {
		return TYPE_NATIVE_AD;
	}
}
