package com.example.sol_repo.utils;

import com.example.sol_repo.models.MenuItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory cart shared by the room service screens. Cleared after an order is placed. */
public final class RoomServiceCart {
    public static final double SERVICE_CHARGE_RATE = 0.10;

    public static class Entry {
        public final MenuItem item;
        public int quantity;

        Entry(MenuItem item, int quantity) {
            this.item = item;
            this.quantity = quantity;
        }
    }

    private static final Map<String, Entry> entries = new LinkedHashMap<>();
    private static String kitchenNote = "";
    private static String paymentMethod = "room_bill";
    private static String bookingId = null;

    private RoomServiceCart() {
    }

    public static void startFor(String newBookingId) {
        if (!newBookingId.equals(bookingId)) {
            clear();
            bookingId = newBookingId;
        }
    }

    public static String getBookingId() {
        return bookingId;
    }

    public static int getQuantity(String menuItemId) {
        Entry entry = entries.get(menuItemId);
        return entry == null ? 0 : entry.quantity;
    }

    public static void setQuantity(MenuItem item, int quantity) {
        if (quantity <= 0) {
            entries.remove(item.getMenuItemId());
        } else {
            Entry entry = entries.get(item.getMenuItemId());
            if (entry == null) {
                entries.put(item.getMenuItemId(), new Entry(item, quantity));
            } else {
                entry.quantity = quantity;
            }
        }
    }

    public static List<Entry> getEntries() {
        return new ArrayList<>(entries.values());
    }

    public static int getItemCount() {
        int count = 0;
        for (Entry entry : entries.values()) {
            count += entry.quantity;
        }
        return count;
    }

    public static boolean isEmpty() {
        return entries.isEmpty();
    }

    public static double getSubtotal() {
        double subtotal = 0;
        for (Entry entry : entries.values()) {
            subtotal += entry.item.getPrice() * entry.quantity;
        }
        return subtotal;
    }

    public static double getServiceCharge() {
        return getSubtotal() * SERVICE_CHARGE_RATE;
    }

    public static double getTotal() {
        return getSubtotal() + getServiceCharge();
    }

    public static String getKitchenNote() {
        return kitchenNote;
    }

    public static void setKitchenNote(String note) {
        kitchenNote = note == null ? "" : note;
    }

    public static String getPaymentMethod() {
        return paymentMethod;
    }

    public static void setPaymentMethod(String method) {
        paymentMethod = method;
    }

    public static void clear() {
        entries.clear();
        kitchenNote = "";
        paymentMethod = "room_bill";
        bookingId = null;
    }
}
