package com.example.sol_repo.models;

public class StoreProduct {
    private final String productId;
    private final String productName;
    private final String category;
    private final double price;
    private final String description;
    private final String imageUrl;
    private final int stockQuantity;

    public StoreProduct(String productId, String productName, String category, double price,
                        String description, String imageUrl, int stockQuantity) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.price = price;
        this.description = description;
        this.imageUrl = imageUrl;
        this.stockQuantity = stockQuantity;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategory() {
        return category;
    }

    public double getPrice() {
        return price;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }
}
