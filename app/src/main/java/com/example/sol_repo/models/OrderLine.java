package com.example.sol_repo.models;

public class OrderLine {
    private final String itemName;
    private final String imageUrl;
    private final int quantity;
    private final double unitPrice;

    public OrderLine(String itemName, String imageUrl, int quantity, double unitPrice) {
        this.itemName = itemName;
        this.imageUrl = imageUrl;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getItemName() {
        return itemName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getLineTotal() {
        return quantity * unitPrice;
    }
}
