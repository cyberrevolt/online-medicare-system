package com.onlinemedic.model;
import java.math.BigDecimal;
public record Doctor(long id, String fullName, String specialization, String qualification, int experienceYears, BigDecimal fee, String about, String clinic, BigDecimal rating, String consultationType, String profileImage, boolean demoProfile) {
    public long getId(){return id;} public String getFullName(){return fullName;} public String getSpecialization(){return specialization;} public String getQualification(){return qualification;} public int getExperienceYears(){return experienceYears;} public BigDecimal getFee(){return fee;} public String getAbout(){return about;} public String getClinic(){return clinic;} public BigDecimal getRating(){return rating;} public String getConsultationType(){return consultationType;} public String getProfileImage(){return profileImage;} public boolean isDemoProfile(){return demoProfile;}
}
