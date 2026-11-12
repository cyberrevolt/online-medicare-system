package com.onlinemedic.service;

import com.onlinemedic.dao.OrderDao;
import com.onlinemedic.util.Database;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public class OrderService {
    public record CheckoutResult(long orderId, boolean paymentSuccess, String message) { }

    public CheckoutResult checkout(long patientId, Map<Long, Integer> cart, String method, String outcome,
                                   String line1, String city, String state, String postalCode) throws SQLException {
        return checkout(patientId, cart, method, outcome, line1, city, city, state, postalCode, null, null);
    }

    public boolean hasCompleteVerifiedPrescriptionCoverage(long patientId, Map<Long, Integer> cart) throws SQLException {
        if (cart == null || cart.isEmpty()) return true;
        try (Connection c = Database.getConnection()) {
            for (Long medicineId : cart.keySet()) {
                try (PreparedStatement p = c.prepareStatement("SELECT name,prescription_required FROM medicines WHERE id=? AND active=TRUE")) {
                    p.setLong(1, medicineId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) continue;
                        if (r.getBoolean("prescription_required") && !hasVerifiedPrescription(c, patientId, r.getString("name"))) return false;
                    }
                }
            }
        }
        return true;
    }

    public CheckoutResult checkout(long patientId, Map<Long, Integer> cart, String method, String outcome,
                                   String line1, String city, String district, String state, String postalCode,
                                   String prescriptionFile, String prescriptionMediaType) throws SQLException {
        return checkout(patientId, cart, method, outcome, line1, city, district, state, postalCode, prescriptionFile, prescriptionMediaType, null);
    }

    public CheckoutResult checkout(long patientId, Map<Long, Integer> cart, String method, String outcome,
                                   String line1, String city, String district, String state, String postalCode,
                                   String prescriptionFile, String prescriptionMediaType, Long savedAddressId) throws SQLException {
        if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("Your cart is empty.");
        if (!Set.of("DEMO_CARD", "COD").contains(method)) throw new IllegalArgumentException("Choose a valid payment method.");
        boolean useSavedAddress = savedAddressId != null && savedAddressId > 0;
        if (savedAddressId != null && savedAddressId < 1) throw new IllegalArgumentException("Choose a valid saved address.");
        if (!useSavedAddress) {
            line1 = required(line1, 5, 220, "Enter your delivery address.");
            city = required(city, 2, 100, "Choose a post office / city from the PIN code results.");
            district = required(district, 2, 100, "Choose a district from the PIN code results.");
            state = required(state, 2, 100, "Choose a state.");
            if (!INDIA_STATES.contains(state)) throw new IllegalArgumentException("Choose a valid Indian state or union territory.");
            postalCode = required(postalCode, 6, 6, "Enter a valid six-digit PIN code.");
            if (!postalCode.matches("[0-9]{6}")) throw new IllegalArgumentException("Enter a valid six-digit PIN code.");
        }
        boolean hasPrescriptionFile = prescriptionFile != null && !prescriptionFile.isBlank();
        if (hasPrescriptionFile && !Set.of("application/pdf", "image/jpeg", "image/png").contains(prescriptionMediaType))
            throw new IllegalArgumentException("Upload a PDF, JPEG, or PNG prescription.");

        PaymentProvider.Result payment = new PaymentService().processDemoPayment("COD".equals(method) ? "success" : outcome);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                List<Line> lines = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                boolean hasPrescriptionMedicine = false;
                boolean missingVerifiedPrescription = false;
                for (var entry : cart.entrySet()) {
                    int quantity = entry.getValue() == null ? 0 : entry.getValue();
                    if (quantity < 1 || quantity > 30) throw new IllegalArgumentException("Choose a quantity between 1 and 30.");
                    try (PreparedStatement p = c.prepareStatement("SELECT name,price,prescription_required,stock_quantity FROM medicines WHERE id=? AND active=TRUE FOR UPDATE")) {
                        p.setLong(1, entry.getKey());
                        try (ResultSet r = p.executeQuery()) {
                            if (!r.next()) throw new IllegalArgumentException("A medicine in your cart is no longer available.");
                            String medicineName = r.getString(1);
                            if (r.getBoolean(3)) {
                                hasPrescriptionMedicine = true;
                                if (!hasVerifiedPrescription(c, patientId, medicineName)) missingVerifiedPrescription = true;
                            }
                            BigDecimal price = r.getBigDecimal(2);
                            if (price == null || price.signum() <= 0) throw new IllegalArgumentException(medicineName + " has not been configured for sale by the pharmacy.");
                            int aggregateStock = r.getInt(4);
                            List<BatchAllocation> allocations = new ArrayList<>();
                            int allBatchStock = 0, remaining = quantity;
                            try (PreparedStatement batches = c.prepareStatement("SELECT id,quantity,expiry_date FROM medicine_batches WHERE medicine_id=? AND quantity>0 ORDER BY expiry_date,id FOR UPDATE")) {
                                batches.setLong(1, entry.getKey());
                                try (ResultSet batchRows = batches.executeQuery()) {
                                    while (batchRows.next()) {
                                        int batchQuantity = batchRows.getInt("quantity");
                                        allBatchStock += batchQuantity;
                                        java.sql.Date expiry = batchRows.getDate("expiry_date");
                                        if (expiry != null && !expiry.toLocalDate().isBefore(java.time.LocalDate.now()) && remaining > 0) {
                                            int allocated = Math.min(remaining, batchQuantity);
                                            if (allocated > 0) { allocations.add(new BatchAllocation(batchRows.getLong("id"), allocated)); remaining -= allocated; }
                                        }
                                    }
                                }
                            }
                            if (allBatchStock > aggregateStock) throw new IllegalStateException("Inventory for " + medicineName + " needs pharmacist reconciliation before checkout.");
                            int legacyStock = aggregateStock - allBatchStock;
                            if (remaining > legacyStock) throw new IllegalArgumentException(medicineName + " has insufficient non-expired stock.");
                            lines.add(new Line(entry.getKey(), quantity, price, allocations));
                            total = total.add(price.multiply(BigDecimal.valueOf(quantity)));
                        }
                    }
                }
                if (hasPrescriptionFile && !hasPrescriptionMedicine) throw new IllegalArgumentException("Your cart has no medicine that requires a prescription.");
                if (missingVerifiedPrescription && !hasPrescriptionFile)
                    throw new IllegalArgumentException("Upload a doctor-issued prescription for the prescription-required medicine(s), or use an existing verified prescription.");

                BigDecimal deliveryFee = CartTotals.deliveryFee(total);
                BigDecimal orderTotal = total.add(deliveryFee);
                if ("DEMO_CARD".equals(method) && !payment.success()) {
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO payments(user_id,amount,payment_method,transaction_id,status) VALUES(?,?,?,?, 'FAILED')")) {
                        p.setLong(1, patientId); p.setBigDecimal(2, orderTotal); p.setString(3, method); p.setString(4, payment.transactionId()); p.executeUpdate();
                    }
                    c.commit();
                    return new CheckoutResult(0, false, payment.message() + " No order was created and stock was unchanged.");
                }

                long addressId;
                if (useSavedAddress) {
                    try (PreparedStatement p = c.prepareStatement("SELECT id FROM addresses WHERE id=? AND user_id=?")) { p.setLong(1, savedAddressId); p.setLong(2, patientId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("That saved address is not on your account. Choose another address."); addressId = r.getLong(1); } }
                } else {
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO addresses(user_id,label,line1,city,district,state,postal_code,is_default) VALUES(?,'Checkout',?,?,?,?,?,FALSE)", Statement.RETURN_GENERATED_KEYS)) {
                        p.setLong(1, patientId); p.setString(2, line1); p.setString(3, city); p.setString(4, district); p.setString(5, state); p.setString(6, postalCode); p.executeUpdate();
                        try (ResultSet k = p.getGeneratedKeys()) { k.next(); addressId = k.getLong(1); }
                    }
                }
                long orderId;
                try (PreparedStatement p = c.prepareStatement("INSERT INTO orders(patient_id,address_id,total,delivery_fee,payment_method,payment_status,status) VALUES(?,?,?,?,?,?,'PLACED')", Statement.RETURN_GENERATED_KEYS)) {
                    p.setLong(1, patientId); p.setLong(2, addressId); p.setBigDecimal(3, orderTotal); p.setBigDecimal(4, deliveryFee);
                    p.setString(5, "COD".equals(method) ? "COD" : "DEMO_CARD"); p.setString(6, "COD".equals(method) ? "PENDING" : "SUCCESS"); p.executeUpdate();
                    try (ResultSet k = p.getGeneratedKeys()) { k.next(); orderId = k.getLong(1); }
                }
                new OrderDao().insertHistory(c, orderId, "PLACED", hasPrescriptionFile ? "Order placed; uploaded prescription is awaiting pharmacy review." : "Order placed.", patientId);
                if (hasPrescriptionFile) {
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO order_prescriptions(order_id,patient_id,file_name,media_type,status) VALUES(?,?,?,?,'PENDING')")) {
                        p.setLong(1, orderId); p.setLong(2, patientId); p.setString(3, prescriptionFile); p.setString(4, prescriptionMediaType); p.executeUpdate();
                    }
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO deliveries(order_id,status) VALUES(?,'PENDING')")) {
                    p.setLong(1, orderId); p.executeUpdate();
                }
                for (Line line : lines) {
                    long orderItemId;
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO order_items(order_id,medicine_id,quantity,unit_price) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                        p.setLong(1, orderId); p.setLong(2, line.id); p.setInt(3, line.quantity); p.setBigDecimal(4, line.price); p.executeUpdate();
                        try (ResultSet keys = p.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Order item key was not returned."); orderItemId = keys.getLong(1); }
                    }
                    for (BatchAllocation allocation : line.allocations) {
                        try (PreparedStatement p = c.prepareStatement("UPDATE medicine_batches SET quantity=quantity-? WHERE id=? AND quantity>=?")) {
                            p.setInt(1, allocation.quantity); p.setLong(2, allocation.batchId); p.setInt(3, allocation.quantity);
                            if (p.executeUpdate() != 1) throw new IllegalArgumentException("Batch stock changed while checking out. Please try again.");
                        }
                        try (PreparedStatement p = c.prepareStatement("INSERT INTO order_item_batches(order_item_id,batch_id,quantity) VALUES(?,?,?)")) {
                            p.setLong(1, orderItemId); p.setLong(2, allocation.batchId); p.setInt(3, allocation.quantity); p.executeUpdate();
                        }
                    }
                    try (PreparedStatement p = c.prepareStatement("UPDATE medicines SET stock_quantity=stock_quantity-? WHERE id=? AND stock_quantity>=?")) {
                        p.setInt(1, line.quantity); p.setLong(2, line.id); p.setInt(3, line.quantity);
                        if (p.executeUpdate() != 1) throw new IllegalArgumentException("Stock changed while checking out. Please try again.");
                    }
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO inventory_transactions(medicine_id,quantity_change,reason,reference_id) VALUES(?,?,'CUSTOMER ORDER',?)")) {
                        p.setLong(1, line.id); p.setInt(2, -line.quantity); p.setLong(3, orderId); p.executeUpdate();
                    }
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO payments(user_id,order_id,amount,payment_method,transaction_id,status) VALUES(?,?,?,?,?,?)")) {
                    p.setLong(1, patientId); p.setLong(2, orderId); p.setBigDecimal(3, orderTotal); p.setString(4, method); p.setString(5, payment.transactionId());
                    p.setString(6, "COD".equals(method) ? "PENDING" : "SUCCESS"); p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO notifications(user_id,title,body) VALUES(?,?,?)")) {
                    p.setLong(1, patientId); p.setString(2, "Order placed");
                    p.setString(3, "Order #" + orderId + " has been placed." + (hasPrescriptionFile ? " The uploaded prescription is waiting for pharmacy review." : "")); p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'ORDER PLACED',?)")) {
                    p.setLong(1, patientId); p.setString(2, "Order #" + orderId + " created with " + lines.size() + " line item(s)"); p.executeUpdate();
                }
                c.commit();
                String message = "Order #" + orderId + " placed. " + ("COD".equals(method) ? "Pay on delivery." : "Demo payment approved; no real charge was made.");
                if (hasPrescriptionFile) message += " The prescription must be verified by the pharmacy before processing.";
                return new CheckoutResult(orderId, true, message);
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException x) throw x;
                if (e instanceof RuntimeException x) throw x;
                throw new SQLException("Order could not be created", e);
            }
        }
    }

    private boolean hasVerifiedPrescription(Connection c, long patientId, String medicineName) throws SQLException {
        String sql = "SELECT 1 FROM prescriptions p JOIN prescription_items pi ON pi.prescription_id=p.id WHERE p.patient_id=? AND p.status='VERIFIED' AND LOWER(REPLACE(TRIM(pi.medicine_name),' ',''))=LOWER(REPLACE(TRIM(?),' ','')) LIMIT 1";
        try (PreparedStatement p = c.prepareStatement(sql)) { p.setLong(1, patientId); p.setString(2, medicineName); try (ResultSet r = p.executeQuery()) { return r.next(); } }
    }

    private String required(String value, int min, int max, String message) {
        if (value == null || value.trim().length() < min || value.trim().length() > max) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static final Set<String> INDIA_STATES = Set.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh",
            "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland",
            "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
            "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry");

    private record BatchAllocation(long batchId, int quantity) { }
    private record Line(long id, int quantity, BigDecimal price, List<BatchAllocation> allocations) { }
}
