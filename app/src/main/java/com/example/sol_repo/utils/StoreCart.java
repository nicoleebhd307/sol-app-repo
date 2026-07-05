package com.example.sol_repo.utils;

import com.example.sol_repo.models.StoreProduct;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory cart shared by the souvenir store screens. Cleared after an order is placed. */
public final class StoreCart {
    public static final double SERVICE_CHARGE_RATE = 0.05;
    public static final double TAX_RATE = 0.08;

    public static class Entry {
        public final StoreProduct product;
        public int quantity;

        Entry(StoreProduct product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }
    }

    private static final Map<String, Entry> entries = new LinkedHashMap<>();
    private static String bookingId = null;

    private StoreCart() {
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

    public static int getQuantity(String productId) {
        Entry entry = entries.get(productId);
        return entry == null ? 0 : entry.quantity;
    }

    public static void setQuantity(StoreProduct product, int quantity) {
        if (quantity <= 0) {
            entries.remove(product.getProductId());
        } else {
            Entry entry = entries.get(product.getProductId());
            if (entry == null) {
                entries.put(product.getProductId(), new Entry(product, quantity));
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
            subtotal += entry.product.getPrice() * entry.quantity;
        }
        return subtotal;
    }

    public static double getServiceCharge() {
        return getSubtotal() * SERVICE_CHARGE_RATE;
    }

    public static double getTax() {
        return getSubtotal() * TAX_RATE;
    }

    public static double getTotal() {
        return getSubtotal() + getServiceCharge() + getTax();
    }

    public static void clear() {
        entries.clear();
        bookingId = null;
    }
}
