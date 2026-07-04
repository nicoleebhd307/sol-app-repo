package com.example.sol_repo.models;

public class MenuItem {
    private final String menuItemId;
    private final String itemName;
    private final String category;
    private final double price;
    private final String description;
    private final String imageUrl;

    public MenuItem(String menuItemId, String itemName, String category, double price,
                    String description, String imageUrl) {
        this.menuItemId = menuItemId;
        this.itemName = itemName;
        this.category = category;
        this.price = price;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public String getMenuItemId() {
        return menuItemId;
    }

    public String getItemName() {
        return itemName;
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
}
