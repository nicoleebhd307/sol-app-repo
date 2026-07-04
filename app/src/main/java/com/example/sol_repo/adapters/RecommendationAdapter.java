package com.example.sol_repo.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.sol_repo.R;
import com.example.sol_repo.models.RecommendationItem;

import java.util.List;

public class RecommendationAdapter {
    private final Context context;
    private final List<RecommendationItem> recommendations;

    public RecommendationAdapter(Context context, List<RecommendationItem> recommendations) {
        this.context = context;
        this.recommendations = recommendations;
    }

    public void renderInto(LinearLayout container) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (RecommendationItem item : recommendations) {
            View itemView = inflater.inflate(R.layout.item_recommendation, container, false);
            ImageView iconView = itemView.findViewById(R.id.imgRecommendationIcon);
            TextView titleView = itemView.findViewById(R.id.txtRecommendationTitle);
            TextView descriptionView = itemView.findViewById(R.id.txtRecommendationDescription);

            iconView.setImageResource(resolveIcon(item.getType()));
            titleView.setText(item.getTitle());
            descriptionView.setText(item.getDescription());

            container.addView(itemView);
        }
    }

    private int resolveIcon(String type) {
        if ("room".equals(type)) {
            return R.drawable.ic_roomservice;
        }
        if ("wellness".equals(type)) {
            return R.drawable.ic_wellness;
        }
        return R.drawable.ic_restaurant;
    }
}
