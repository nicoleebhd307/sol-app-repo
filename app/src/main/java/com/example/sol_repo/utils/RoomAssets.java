package com.example.sol_repo.utils;

import com.example.sol_repo.R;

import java.util.Locale;

public final class RoomAssets {

    private RoomAssets() {
    }

    public static final int ROOM_PLACEHOLDER = R.drawable.bg_room_thumb;
    public static final int MENU_PLACEHOLDER = R.drawable.bg_menu_warm;

    public static int badgeLabelFor(String viewType) {
        if ("garden".equals(viewType)) {
            return R.string.badge_garden_view;
        }
        if ("pool".equals(viewType)) {
            return R.string.badge_pool_side;
        }
        if ("street".equals(viewType)) {
            return R.string.badge_street_view;
        }
        return R.string.badge_ocean_view;
    }

    /** Picks a representative room photo from the room/type name for list thumbnails. */
    public static int roomImageForName(String roomName) {
        String name = roomName == null ? "" : roomName.toLowerCase(Locale.US);
        if (name.contains("garden")) {
            return R.drawable.bg_room_garden;
        }
        if (name.contains("pool")) {
            return R.drawable.bg_room_pool;
        }
        if (name.contains("beach") || name.contains("sea") || name.contains("ocean")
                || name.contains("villa") || name.contains("suite")) {
            return R.drawable.bg_room_beach;
        }
        return R.drawable.bg_room_thumb;
    }

    public static int amenityIconFor(String amenityName) {
        String name = amenityName == null ? "" : amenityName.toLowerCase(Locale.US);
        if (name.contains("wi-fi") || name.contains("wifi")) {
            return R.drawable.ic_wifi;
        }
        if (name.contains("breakfast")) {
            return R.drawable.ic_restaurant;
        }
        if (name.contains("room service")) {
            return R.drawable.ic_roomservice;
        }
        if (name.contains("bathtub") || name.contains("shower") || name.contains("pool")) {
            return R.drawable.ic_wellness;
        }
        if (name.contains("balcony") || name.contains("terrace")) {
            return R.drawable.ic_home;
        }
        if (name.contains("butler")) {
            return R.drawable.ic_profile;
        }
        if (name.contains("daybed") || name.contains("bed")) {
            return R.drawable.ic_bed;
        }
        return R.drawable.ic_check;
    }
}
