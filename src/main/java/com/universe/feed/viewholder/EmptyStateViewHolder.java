package com.universe.feed.viewholder;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.universe.feed.R;

public class EmptyStateViewHolder extends RecyclerView.ViewHolder {

    private ImageView emptyIcon;
    private TextView emptyTitle;
    private TextView emptyMessage;

    public EmptyStateViewHolder(@NonNull View itemView) {
        super(itemView);
        emptyIcon = itemView.findViewById(R.id.empty_icon);
        emptyTitle = itemView.findViewById(R.id.empty_title);
        emptyMessage = itemView.findViewById(R.id.empty_message);
    }

    public void bind() {
        emptyTitle.setText("No posts available");
        emptyMessage.setText("Be the first to post something!");
    }

    public void setCustomMessage(String title, String message) {
        emptyTitle.setText(title);
        emptyMessage.setText(message);
    }
}
