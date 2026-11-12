package com.onlinemedic.model;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
public record Appointment(long id, String doctorName, LocalDate date, LocalTime time, String type, BigDecimal fee, String status, String paymentStatus, boolean cancelEligible, boolean reviewed) {
    public long getId(){return id;} public String getDoctorName(){return doctorName;} public LocalDate getDate(){return date;} public LocalTime getTime(){return time;} public String getType(){return type;} public BigDecimal getFee(){return fee;} public String getStatus(){return status;} public String getPaymentStatus(){return paymentStatus;} public boolean isCancelEligible(){return cancelEligible;} public boolean isReviewed(){return reviewed;}
}
