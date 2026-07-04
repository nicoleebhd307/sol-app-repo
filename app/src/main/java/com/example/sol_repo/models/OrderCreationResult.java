package com.example.sol_repo.models;

public class OrderCreationResult {
    private final String orderId;
    private final String orderCode;

    public OrderCreationResult(String orderId, String orderCode) {
        this.orderId = orderId;
        this.orderCode = orderCode;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getOrderCode() {
        return orderCode;
    }
}
