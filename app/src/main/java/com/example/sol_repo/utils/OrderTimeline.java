package com.example.sol_repo.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.sol_repo.R;

public final class OrderTimeline {

    private OrderTimeline() {
    }

    public static int stepIndexFor(String status) {
        switch (status == null ? "" : status) {
            case "preparing":
                return 1;
            case "on_the_way":
                return 2;
            case "delivered":
                return 3;
            default:
                return 0;
        }
    }

    public static int[] stepLabels() {
        return new int[]{
                R.string.rs_status_confirmed,
                R.string.rs_status_preparing,
                R.string.rs_status_on_the_way,
                R.string.rs_status_delivered
        };
    }

    /**
     * Renders the 4 order steps into the given horizontal row.
     * A step before the current one shows a gold check, the current one a gold dot.
     */
    public static void render(Context context, LinearLayout row, int currentIndex, String[] times) {
        row.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        int[] labels = stepLabels();

        for (int i = 0; i < labels.length; i++) {
            View stepView = inflater.inflate(R.layout.item_timeline_step, row, false);
            TextView labelView = stepView.findViewById(R.id.txtStepLabel);
            TextView timeView = stepView.findViewById(R.id.txtStepTime);
            ImageView checkView = stepView.findViewById(R.id.imgStepCheck);
            View currentDot = stepView.findViewById(R.id.dotStepCurrent);
            View dotFrame = stepView.findViewById(R.id.frameStepDot);

            labelView.setText(labels[i]);
            if (times != null && i < times.length && times[i] != null && !times[i].isEmpty()) {
                timeView.setText(times[i]);
                timeView.setVisibility(View.VISIBLE);
            }

            if (i < currentIndex) {
                dotFrame.setBackgroundResource(R.drawable.bg_circle_gold);
                checkView.setVisibility(View.VISIBLE);
            } else if (i == currentIndex) {
                dotFrame.setBackgroundResource(R.drawable.bg_circle_ring);
                currentDot.setVisibility(View.VISIBLE);
                labelView.setTextColor(context.getColor(R.color.sol_gold_dark));
                labelView.setTypeface(labelView.getTypeface(), android.graphics.Typeface.BOLD);
            }

            row.addView(stepView);
        }
    }
}
