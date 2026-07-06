package com.example.sol_repo.models;

public class DiningTable {
    private final String tableId;
    private final String code;
    private final int capacity;
    private final String shape;      // round | square | rect
    private final int sortOrder;

    public DiningTable(String tableId, String code, int capacity, String shape, int sortOrder) {
        this.tableId = tableId;
        this.code = code;
        this.capacity = capacity;
        this.shape = shape;
        this.sortOrder = sortOrder;
    }

    public String getTableId() {
        return tableId;
    }

    public String getCode() {
        return code;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getShape() {
        return shape;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isRound() {
        return "round".equals(shape);
    }

    public boolean isLarge() {
        return capacity >= 6;
    }
}
