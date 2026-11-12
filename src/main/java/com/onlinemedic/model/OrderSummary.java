package com.onlinemedic.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record OrderSummary(long id, String patientName, String patientEmail, String patientPhone, BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal total,
                           String paymentMethod, String paymentStatus, String status, LocalDateTime createdAt,
                           String transactionId, String prescriptionStatus, String returnStatus, LocalDateTime returnRequestedAt,
                           boolean returnImageRequired, boolean returnImageUploaded, LocalDateTime returnImageUploadedAt,
                           String returnReviewNote, String pickupStatus, LocalDateTime pickupScheduledAt, LocalDateTime pickupCompletedAt,
                           String pickupStaffName, String pickupStaffPhone,
                           String addressLine, String city, String district, String state, String postalCode,
                           LocalDateTime deliveredAt, String deliveryStaffName, String deliveryStaffPhone,
                           String refundStatus, BigDecimal refundAmount, String refundReviewNote,
                           List<Item> items, List<StatusEvent> timeline) {
    public long getId(){return id;} public String getPatientName(){return patientName;} public String getPatientEmail(){return patientEmail;} public String getPatientPhone(){return patientPhone;} public BigDecimal getSubtotal(){return subtotal;}
    public BigDecimal getDeliveryFee(){return deliveryFee;} public BigDecimal getTotal(){return total;} public String getPaymentMethod(){return paymentMethod;}
    public String getPaymentStatus(){return paymentStatus;} public String getStatus(){return status;} public LocalDateTime getCreatedAt(){return createdAt;}
    public String getTransactionId(){return transactionId;} public String getPrescriptionStatus(){return prescriptionStatus;} public String getReturnStatus(){return returnStatus;}
    public boolean isReturnImageRequired(){return returnImageRequired;} public boolean isReturnImageUploaded(){return returnImageUploaded;} public LocalDateTime getReturnImageUploadedAt(){return returnImageUploadedAt;}
    public String getReturnReviewNote(){return returnReviewNote;} public String getPickupStatus(){return pickupStatus;} public LocalDateTime getPickupScheduledAt(){return pickupScheduledAt;} public LocalDateTime getPickupCompletedAt(){return pickupCompletedAt;}
    public String getPickupStaffName(){return pickupStaffName;} public String getPickupStaffPhone(){return pickupStaffPhone;}
    public LocalDateTime getReturnRequestedAt(){return returnRequestedAt;} public String getAddressLine(){return addressLine;} public String getCity(){return city;}
    public String getDistrict(){return district;} public String getState(){return state;} public String getPostalCode(){return postalCode;}
    public LocalDateTime getDeliveredAt(){return deliveredAt;} public String getDeliveryStaffName(){return deliveryStaffName;} public String getDeliveryStaffPhone(){return deliveryStaffPhone;} public List<Item> getItems(){return items;} public List<StatusEvent> getTimeline(){return timeline;}
    public String getRefundStatus(){return refundStatus;} public BigDecimal getRefundAmount(){return refundAmount;} public String getRefundReviewNote(){return refundReviewNote;}
    public boolean isCanCancel(){return canCancel();} public boolean isCanReturn(){return canReturn();} public long getReturnDaysRemaining(){return returnDaysRemaining();}

    public boolean canCancel() {
        return List.of("PLACED", "CONFIRMED", "PROCESSING", "PACKED").contains(status);
    }

    public boolean canReturn() {
        return "DELIVERED".equals(status) && returnStatus == null && deliveredAt != null
                && !LocalDateTime.now(ZoneId.of("Asia/Kolkata")).isAfter(deliveredAt.plusDays(7));
    }

    public long returnDaysRemaining() {
        if (deliveredAt == null) return 0;
        long remaining = java.time.Duration.between(LocalDateTime.now(ZoneId.of("Asia/Kolkata")), deliveredAt.plusDays(7)).toDays();
        return Math.max(0, remaining + 1);
    }

    public record Item(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        public String getName(){return name;} public int getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getLineTotal(){return lineTotal;}
    }
    public record StatusEvent(String status, String note, LocalDateTime changedAt) {
        public String getStatus(){return status;} public String getNote(){return note;} public LocalDateTime getChangedAt(){return changedAt;}
    }
}
