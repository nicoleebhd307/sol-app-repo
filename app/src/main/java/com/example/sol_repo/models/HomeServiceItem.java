package com.example.sol_repo.models;

public class HomeServiceItem {
    private String title;
    private String subtitle;
    private String status;
    private String iconType;
    private String createdAt;

    public HomeServiceItem() {
    }

    public HomeServiceItem(String title, String subtitle, String status, String iconType) {
        this(title, subtitle, status, iconType, null);
    }

    public HomeServiceItem(String title, String subtitle, String status, String iconType,
                           String createdAt) {
        this.title = title;
        this.subtitle = subtitle;
        this.status = status;
        this.iconType = iconType;
        this.createdAt = createdAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIconType() {
        return iconType;
    }

    public void setIconType(String iconType) {
        this.iconType = iconType;
    }

    @Override
    public String toString() {
        return "HomeServiceItem{" +
                "title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", status='" + status + '\'' +
                ", iconType='" + iconType + '\'' +
                '}';
    }
}
