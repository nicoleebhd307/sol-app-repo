package com.example.sol_repo.models;

public class RoomType {
    private final String roomTypeId;
    private final String typeName;
    private final String description;
    private final double basePrice;
    private final int maxOccupancy;
    private final String category;
    private final String viewType;
    private final int sizeSqft;
    private final String bedType;
    private final String amenities;
    private final String imageUrl;
    private final int availableRooms;

    public RoomType(String roomTypeId, String typeName, String description, double basePrice,
                    int maxOccupancy, String category, String viewType, int sizeSqft,
                    String bedType, String amenities, String imageUrl, int availableRooms) {
        this.roomTypeId = roomTypeId;
        this.typeName = typeName;
        this.description = description;
        this.basePrice = basePrice;
        this.maxOccupancy = maxOccupancy;
        this.category = category;
        this.viewType = viewType;
        this.sizeSqft = sizeSqft;
        this.bedType = bedType;
        this.amenities = amenities;
        this.imageUrl = imageUrl;
        this.availableRooms = availableRooms;
    }

    public String getRoomTypeId() {
        return roomTypeId;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getDescription() {
        return description;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public int getMaxOccupancy() {
        return maxOccupancy;
    }

    public String getCategory() {
        return category;
    }

    public String getViewType() {
        return viewType;
    }

    public int getSizeSqft() {
        return sizeSqft;
    }

    public String getBedType() {
        return bedType;
    }

    public String getAmenities() {
        return amenities;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getAvailableRooms() {
        return availableRooms;
    }
}
