package com.example.sol_repo.utils;

import android.content.Intent;
import android.graphics.Typeface;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.sol_repo.R;
import com.example.sol_repo.activities.AccountActivity;
import com.example.sol_repo.activities.MainActivity;

/** Wires the shared bottom navigation bar: highlights the active tab and handles tab switches. */
public final class BottomNavHelper {

    public enum Tab {HOME, STAY, SERVICES, PROFILE}

    private BottomNavHelper() {
    }

    public static void setup(AppCompatActivity activity, Tab activeTab) {
        style(activity, R.id.imgNavHome, R.id.txtNavHome, activeTab == Tab.HOME);
        style(activity, R.id.imgNavStay, R.id.txtNavStay, activeTab == Tab.STAY);
        style(activity, R.id.imgNavServices, R.id.txtNavServices, activeTab == Tab.SERVICES);
        style(activity, R.id.imgNavProfile, R.id.txtNavProfile, activeTab == Tab.PROFILE);

        bind(activity, R.id.navHome, activeTab == Tab.HOME, () -> openStaySession(activity));
        bind(activity, R.id.navStay, activeTab == Tab.STAY, () -> openStaySession(activity));
        bind(activity, R.id.navServices, activeTab == Tab.SERVICES, () ->
                Toast.makeText(activity, R.string.nav_coming_soon, Toast.LENGTH_SHORT).show());
        bind(activity, R.id.navProfile, activeTab == Tab.PROFILE, () -> openProfile(activity));
    }

    private static void style(AppCompatActivity activity, int iconId, int labelId, boolean active) {
        ImageView icon = activity.findViewById(iconId);
        TextView label = activity.findViewById(labelId);
        if (icon == null || label == null) {
            return;
        }
        int color = ContextCompat.getColor(activity,
                active ? R.color.color_icon_active : R.color.color_icon_inactive);
        icon.setColorFilter(color);
        label.setTextColor(color);
        label.setTypeface(ResourcesCompat.getFont(activity, R.font.plus_jakarta_sans),
                active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private static void bind(AppCompatActivity activity, int rootId, boolean active, Runnable action) {
        android.view.View root = activity.findViewById(rootId);
        if (root == null) {
            return;
        }
        root.setOnClickListener(view -> {
            if (active) {
                return;
            }
            action.run();
        });
    }

    /** Home & Stay both open the in-stay dashboard for the currently selected booking session. */
    private static void openStaySession(AppCompatActivity activity) {
        String bookingId = new SessionManager(activity).getSelectedBookingId();
        if (bookingId == null) {
            Toast.makeText(activity, R.string.nav_no_active_stay, Toast.LENGTH_LONG).show();
            openProfile(activity);
            return;
        }
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_BOOKING_ID, bookingId);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
    }

    private static void openProfile(AppCompatActivity activity) {
        Intent intent = new Intent(activity, AccountActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
    }
}
