package com.onlinemedic.model;
import java.math.BigDecimal;
public record Medicine(long id, String name, String category, String manufacturer, String description, BigDecimal price, boolean prescriptionRequired, int stock, int minStock, String imageUrl) {
    public long getId(){return id;} public String getName(){return name;} public String getCategory(){return category;} public String getManufacturer(){return manufacturer;} public String getDescription(){return description;} public BigDecimal getPrice(){return price;} public boolean isPrescriptionRequired(){return prescriptionRequired;} public int getStock(){return stock;} public int getMinStock(){return minStock;} public String getImageUrl(){return imageUrl;}
}
