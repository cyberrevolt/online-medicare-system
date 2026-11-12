package com.onlinemedic.model;
public record User(long id, String fullName, String email, String phone, String role) {
    public long getId(){return id;} public String getFullName(){return fullName;} public String getEmail(){return email;} public String getPhone(){return phone;} public String getRole(){return role;}
}
