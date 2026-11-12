package com.onlinemedic.model;

public record PharmacyProfile(String name, String imageUrl) {
    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
}
