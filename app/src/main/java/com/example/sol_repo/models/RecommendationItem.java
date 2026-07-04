package com.example.sol_repo.models;

public class RecommendationItem {
    private String title;
    private String description;
    private String type;

    public RecommendationItem() {
    }

    public RecommendationItem(String title, String description, String type) {
        this.title = title;
        this.description = description;
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "RecommendationItem{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
