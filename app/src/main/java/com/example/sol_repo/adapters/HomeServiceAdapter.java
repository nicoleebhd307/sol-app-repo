package com.example.sol_repo.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.sol_repo.R;
import com.example.sol_repo.models.HomeServiceItem;

import java.util.List;

public class HomeServiceAdapter {

    public interface OnServiceClickListener {
        void onServiceClick(HomeServiceItem service);
    }

    private final Context context;
    private final List<HomeServiceItem> services;
    private OnServiceClickListener clickListener;

    public HomeServiceAdapter(Context context, List<HomeServiceItem> services) {
        this.context = context;
        this.services = services;
    }

    public HomeServiceAdapter setOnServiceClickListener(OnServiceClickListener listener) {
        this.clickListener = listener;
        return this;
    }

    public void renderInto(LinearLayout container) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (HomeServiceItem service : services) {
            View itemView = inflater.inflate(R.layout.item_home_service, container, false);
            ImageView iconView = itemView.findViewById(R.id.imgServiceIcon);
            TextView titleView = itemView.findViewById(R.id.txtServiceTitle);
            TextView subtitleView = itemView.findViewById(R.id.txtServiceSubtitle);
            TextView statusView = itemView.findViewById(R.id.txtServiceStatus);

            iconView.setImageResource(resolveIcon(service.getIconType()));
            titleView.setText(service.getTitle());
            subtitleView.setText(service.getSubtitle());
            statusView.setText(service.getStatus());
            applyStatusStyle(statusView, service.getStatus());

            if (clickListener != null) {
                itemView.setClickable(true);
                itemView.setFocusable(true);
                itemView.setOnClickListener(view -> clickListener.onServiceClick(service));
            }

            container.addView(itemView);
        }
    }

    private int resolveIcon(String iconType) {
        if ("transfer".equals(iconType)) {
            return R.drawable.ic_transfer;
        }
        if ("wellness".equals(iconType)) {
            return R.drawable.ic_wellness;
        }
        if ("restaurant".equals(iconType)) {
            return R.drawable.ic_restaurant;
        }
        if ("roomservice".equals(iconType)) {
            return R.drawable.ic_roomservice;
        }
        if ("souvenir".equals(iconType)) {
            return R.drawable.ic_bag;
        }
        return R.drawable.ic_service;
    }

    private void applyStatusStyle(TextView statusView, String status) {
        String normalized = status == null ? "" : status.toLowerCase();
        if (normalized.contains("confirmed")) {
            statusView.setBackgroundResource(R.drawable.bg_status_success);
            statusView.setTextColor(context.getColor(R.color.color_icon_success));
        } else if (normalized.contains("pending")) {
            statusView.setBackgroundResource(R.drawable.bg_status_warning);
            statusView.setTextColor(context.getColor(R.color.color_icon_warning));
        } else {
            statusView.setBackgroundResource(R.drawable.bg_status_primary);
            statusView.setTextColor(context.getColor(R.color.sol_gold_dark));
        }
    }
}
