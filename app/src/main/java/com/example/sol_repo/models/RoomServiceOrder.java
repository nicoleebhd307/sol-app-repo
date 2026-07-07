package com.example.sol_repo.models;

public class RoomServiceOrder {
    private final String orderId;
    private final String orderCode;
    private final String bookingId;
    private final double totalAmount;
    private final String status;
    private final String orderedAt;
    private final int itemCount;
    private final String kitchenNote;

    public RoomServiceOrder(String orderId, String orderCode, String bookingId, double totalAmount,
                            String status, String orderedAt, int itemCount, String kitchenNote) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.bookingId = bookingId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.orderedAt = orderedAt;
        this.itemCount = itemCount;
        this.kitchenNote = kitchenNote;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public String getOrderedAt() {
        return orderedAt;
    }

    public int getItemCount() {
        return itemCount;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getKitchenNote() {
        return kitchenNote;
    }
}
