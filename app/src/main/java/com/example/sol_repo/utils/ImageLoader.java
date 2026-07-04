package com.example.sol_repo.utils;

import android.widget.ImageView;

import com.bumptech.glide.Glide;

public final class ImageLoader {

    private ImageLoader() {
    }

    public static void load(ImageView imageView, String imageUrl, int placeholderRes) {
        Glide.with(imageView.getContext())
                .load(imageUrl)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .centerCrop()
                .into(imageView);
    }

    public static void loadCircle(ImageView imageView, String imageUrl, int placeholderRes) {
        Glide.with(imageView.getContext())
                .load(imageUrl)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .circleCrop()
                .into(imageView);
    }
}
