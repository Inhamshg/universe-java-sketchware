package com.campus.universeapp.model;

import android.graphics.Color;

import java.util.Map;

/**
 * Post model extending FeedItem
 * Represents a user post in the feed with all metadata
 */
public class PostItem extends FeedItem {
	private final String postId;
	private final String uid;
	private final String userName;
	private final String content;
	private final String caption;
	private final String type;
	private final String imageUrl;
	private final String postUserImage;
	private final String background;
	private final int textColor;
	private final int fontStyle;
	private final long timestamp;
	private final int likes;
	private final double randomSeed;
	private final boolean edit;
	private final boolean isRepost;
	private double calculatedScore;

	public PostItem(String postId, Map<String, Object> map) {
		this.postId = postId;
		this.uid = (String) map.get("uid");
		this.userName = (String) map.get("userName");
		this.content = (String) map.get("content");
		this.caption = (String) map.get("caption");
		this.type = (String) map.get("type");
		this.imageUrl = (String) map.get("imageUrl");
		this.postUserImage = (String) map.get("postUserImage");

		this.background = map.get("background") != null
			? String.valueOf(map.get("background"))
			: "default";

		Object tc = map.get("textColor");
		this.textColor = tc instanceof Number
			? ((Number) tc).intValue()
			: Color.BLACK;

		Object fs = map.get("fontStyle");
		this.fontStyle = fs instanceof Number
			? ((Number) fs).intValue()
			: 0;

		Object ts = map.get("timestamp");
		this.timestamp = (ts instanceof Long) ? (Long) ts : System.currentTimeMillis();

		Object lk = map.get("likes");
		this.likes = (lk instanceof Long) ? ((Long) lk).intValue() : 0;

		Object rd = map.get("random");
		this.randomSeed = (rd instanceof Double) ? (Double) rd : Math.random();
		
		Object ed = map.get("edit");
		this.edit = ed instanceof Boolean && (Boolean) ed;

		Object rp = map.get("isRepost");
		this.isRepost = rp instanceof Boolean && (Boolean) rp;
	}

	@Override
	public String getId() {
		return postId;
	}

	@Override
	public int getItemType() {
		return TYPE_POST;
	}

	public String getUid() {
		return uid;
	}

	public String getUserName() {
		return userName;
	}

	public String getContent() {
		return content;
	}

	public String getCaption() {
		return caption;
	}

	public String getType() {
		return type;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public String getPostUserImage() {
		return postUserImage;
	}

	public String getBackground() {
		return background;
	}

	public int getTextColor() {
		return textColor;
	}

	public int getFontStyle() {
		return fontStyle;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public int getLikes() {
		return likes;
	}

	public double getRandomSeed() {
		return randomSeed;
	}

	public boolean isEdit() {
		return edit;
	}

	public boolean isRepost() {
		return isRepost;
	}

	public double getCalculatedScore() {
		return calculatedScore;
	}

	public void setCalculatedScore(double score) {
		this.calculatedScore = score;
	}
}
