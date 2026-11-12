package com.onlinemedic.dao;

import com.onlinemedic.model.OrderSummary;
import com.onlinemedic.util.Database;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class OrderDao {
    private static final String PATIENT_ORDER_SELECT = "SELECT o.id,u.full_name patient_name,u.email patient_email,COALESCE(u.phone,'') patient_phone,o.total,o.delivery_fee,o.payment_method,o.payment_status,CASE WHEN d.status='NOT_DELIVERED' THEN 'NOT_DELIVERED' ELSE o.status END status,o.created_at,(SELECT pay.transaction_id FROM payments pay WHERE pay.order_id=o.id ORDER BY pay.id DESC LIMIT 1) transaction_id,op.status prescription_status,orr.status return_status,orr.requested_at,COALESCE(orr.image_required,FALSE) return_image_required,(orr.image_file IS NOT NULL) return_image_uploaded,orr.image_uploaded_at return_image_uploaded_at,orr.review_note return_review_note,orr.pickup_status,orr.pickup_scheduled_at,orr.pickup_completed_at,prs.full_name pickup_staff_name,COALESCE(prs.phone,'') pickup_staff_phone,a.line1,a.city,a.district,a.state,a.postal_code,d.delivered_at,ds.full_name delivery_staff_name,COALESCE(ds.phone,'') delivery_staff_phone,(SELECT rf.status FROM refunds rf JOIN payments rfp ON rfp.id=rf.payment_id WHERE rfp.order_id=o.id ORDER BY rf.id DESC LIMIT 1) refund_status,(SELECT rf.amount FROM refunds rf JOIN payments rfp ON rfp.id=rf.payment_id WHERE rfp.order_id=o.id ORDER BY rf.id DESC LIMIT 1) refund_amount,(SELECT rf.review_note FROM refunds rf JOIN payments rfp ON rfp.id=rf.payment_id WHERE rfp.order_id=o.id ORDER BY rf.id DESC LIMIT 1) refund_review_note FROM orders o JOIN users u ON u.id=o.patient_id LEFT JOIN order_prescriptions op ON op.order_id=o.id LEFT JOIN order_returns orr ON orr.order_id=o.id LEFT JOIN users prs ON prs.id=orr.pickup_staff_id LEFT JOIN addresses a ON a.id=o.address_id LEFT JOIN deliveries d ON d.order_id=o.id LEFT JOIN users ds ON ds.id=d.delivery_staff_id ";

    public List<OrderSummary> forPatient(long patientId) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(PATIENT_ORDER_SELECT + "WHERE o.patient_id=? ORDER BY o.created_at DESC")) {
            p.setLong(1, patientId);
            try (ResultSet r = p.executeQuery()) {
                List<OrderSummary> out = new ArrayList<>();
                while (r.next()) out.add(readPatientOrder(c, r));
                return out;
            }
        }
    }

    public Optional<OrderSummary> findForPatient(long orderId, long patientId) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(PATIENT_ORDER_SELECT + "WHERE o.id=? AND o.patient_id=?")) {
            p.setLong(1, orderId); p.setLong(2, patientId);
            try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(readPatientOrder(c, r)) : Optional.empty(); }
        }
    }

    private OrderSummary readPatientOrder(Connection c, ResultSet r) throws SQLException {
        long id = r.getLong("id");
        BigDecimal total = r.getBigDecimal("total");
        BigDecimal fee = r.getBigDecimal("delivery_fee");
        Timestamp returnedAt = r.getTimestamp("requested_at");
        return new OrderSummary(id, r.getString("patient_name"), r.getString("patient_email"), r.getString("patient_phone"), total.subtract(fee), fee, total,
                r.getString("payment_method"), r.getString("payment_status"), r.getString("status"), localDateTime(r.getTimestamp("created_at")),
                r.getString("transaction_id"), r.getString("prescription_status"), r.getString("return_status"), localDateTime(returnedAt),
                r.getBoolean("return_image_required"), r.getBoolean("return_image_uploaded"), localDateTime(r.getTimestamp("return_image_uploaded_at")),
                r.getString("return_review_note"), r.getString("pickup_status"), localDateTime(r.getTimestamp("pickup_scheduled_at")), localDateTime(r.getTimestamp("pickup_completed_at")),
                r.getString("pickup_staff_name"), r.getString("pickup_staff_phone"),
                r.getString("line1"), r.getString("city"), r.getString("district"), r.getString("state"), r.getString("postal_code"),
                localDateTime(r.getTimestamp("delivered_at")), r.getString("delivery_staff_name"), r.getString("delivery_staff_phone"),
                r.getString("refund_status"), r.getBigDecimal("refund_amount"), r.getString("refund_review_note"),
                itemsForOrder(c, id), historyForOrder(c, id, r.getString("status"), localDateTime(r.getTimestamp("created_at"))));
    }

    private List<OrderSummary.Item> itemsForOrder(Connection c, long orderId) throws SQLException {
        List<OrderSummary.Item> items = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT m.name,oi.quantity,oi.unit_price FROM order_items oi JOIN medicines m ON m.id=oi.medicine_id WHERE oi.order_id=? ORDER BY oi.id")) {
            p.setLong(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    BigDecimal unitPrice = r.getBigDecimal("unit_price");
                    int quantity = r.getInt("quantity");
                    items.add(new OrderSummary.Item(r.getString("name"), quantity, unitPrice, unitPrice.multiply(BigDecimal.valueOf(quantity))));
                }
            }
        }
        return items;
    }

    private List<OrderSummary.StatusEvent> historyForOrder(Connection c, long orderId, String currentStatus, LocalDateTime createdAt) throws SQLException {
        List<OrderSummary.StatusEvent> events = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT status,note,changed_at FROM order_status_history WHERE order_id=? ORDER BY changed_at,id")) {
            p.setLong(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) events.add(new OrderSummary.StatusEvent(r.getString("status"), r.getString("note"), localDateTime(r.getTimestamp("changed_at"))));
            }
        }
        if (events.isEmpty()) events.add(new OrderSummary.StatusEvent(currentStatus, "Current order status", createdAt));
        return events;
    }

    private LocalDateTime localDateTime(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }

    public int count() throws SQLException { return countSql("SELECT COUNT(*) FROM orders"); }
    public int countPending() throws SQLException { return countSql("SELECT COUNT(*) FROM orders WHERE status IN ('PLACED','CONFIRMED','PROCESSING')"); }
    public int countRefundsPending() throws SQLException { return countSql("SELECT COUNT(*) FROM refunds rf JOIN payments p ON p.id=rf.payment_id WHERE p.order_id IS NOT NULL AND rf.status IN ('PENDING','APPROVED')"); }

    public List<Map<String, Object>> queue(String role, long staffId) throws SQLException {
        String sql = "SELECT o.id,u.full_name patient_name,o.total,o.payment_method,o.payment_status,CASE WHEN d.status='NOT_DELIVERED' THEN 'NOT_DELIVERED' ELSE o.status END status,o.created_at,COALESCE(a.line1,'Address not provided') address,COALESCE(a.city,'') city,COALESCE(a.district,'') district,op.status prescription_status,orr.status return_status,d.delivery_staff_id assigned_delivery_id,ds.full_name assigned_delivery_name,COALESCE(ds.phone,'') assigned_delivery_phone FROM orders o JOIN users u ON u.id=o.patient_id LEFT JOIN addresses a ON a.id=o.address_id LEFT JOIN order_prescriptions op ON op.order_id=o.id LEFT JOIN order_returns orr ON orr.order_id=o.id LEFT JOIN deliveries d ON d.order_id=o.id LEFT JOIN users ds ON ds.id=d.delivery_staff_id ";
        if ("DELIVERY".equals(role)) sql += "WHERE d.delivery_staff_id=? AND o.status IN ('SHIPPED','OUT_FOR_DELIVERY') AND d.status IN ('SHIPPED','OUT_FOR_DELIVERY') ";
        else if ("PHARMACIST".equals(role)) sql += "WHERE o.status IN ('PLACED','CONFIRMED','PROCESSING','PACKED') ";
        else sql += "WHERE o.status NOT IN ('DELIVERED','CANCELLED','RETURNED') AND COALESCE(d.status,'')<>'NOT_DELIVERED' ";
        sql += "ORDER BY o.created_at ASC LIMIT 100";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            if ("DELIVERY".equals(role)) p.setLong(1, staffId);
            try (ResultSet r = p.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (r.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getLong("id")); m.put("patient", r.getString("patient_name")); m.put("total", r.getBigDecimal("total"));
                    m.put("method", r.getString("payment_method")); m.put("payment", r.getString("payment_status")); m.put("status", r.getString("status"));
                    m.put("createdAt", r.getTimestamp("created_at")); m.put("address", r.getString("address")); m.put("city", r.getString("city"));
                    m.put("district", r.getString("district")); m.put("prescriptionStatus", r.getString("prescription_status")); m.put("returnStatus", r.getString("return_status"));
                    long assignedDeliveryId = r.getLong("assigned_delivery_id");
                    m.put("assignedDeliveryId", r.wasNull() ? null : assignedDeliveryId);
                    m.put("assignedDeliveryName", r.getString("assigned_delivery_name"));
                    m.put("assignedDeliveryPhone", r.getString("assigned_delivery_phone"));
                    out.add(m);
                }
                return out;
            }
        }
    }

    public List<Map<String, Object>> deliveryStaffOptions() throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT id,full_name,phone FROM users WHERE role='DELIVERY' AND active=TRUE AND phone IS NOT NULL AND TRIM(phone)<>'' ORDER BY full_name";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                Map<String, Object> staff = new LinkedHashMap<>();
                staff.put("id", r.getLong("id")); staff.put("name", r.getString("full_name")); staff.put("phone", r.getString("phone"));
                staff.put("hasPhone", r.getString("phone") != null && !r.getString("phone").isBlank());
                out.add(staff);
            }
        }
        return out;
    }

    public void assignDeliveryStaff(long orderId, long deliveryStaffId, long actorId, String role) throws SQLException {
        if (!Set.of("PHARMACIST", "ADMIN").contains(role)) throw new SecurityException("Only pharmacy or admin staff can assign a delivery person.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String status; long patientId;
                try (PreparedStatement p = c.prepareStatement("SELECT status,patient_id FROM orders WHERE id=? FOR UPDATE")) {
                    p.setLong(1, orderId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Order not found.");
                        status = r.getString("status"); patientId = r.getLong("patient_id");
                    }
                }
                if (!Set.of("PACKED", "SHIPPED").contains(status))
                    throw new IllegalArgumentException("Pack the order before assigning delivery staff for dispatch.");

                try (PreparedStatement p = c.prepareStatement("SELECT status FROM order_prescriptions WHERE order_id=? FOR UPDATE")) {
                    p.setLong(1, orderId);
                    try (ResultSet r = p.executeQuery()) {
                        if (r.next() && !"VERIFIED".equals(r.getString(1)))
                            throw new IllegalArgumentException("Verify the uploaded prescription before sending this order out for delivery.");
                    }
                }

                String staffName; String phone;
                try (PreparedStatement p = c.prepareStatement("SELECT full_name,phone FROM users WHERE id=? AND role='DELIVERY' AND active=TRUE")) {
                    p.setLong(1, deliveryStaffId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Choose an active delivery staff account.");
                        staffName = r.getString("full_name"); phone = r.getString("phone");
                    }
                }
                if (phone == null || phone.isBlank()) throw new IllegalArgumentException("Add a contact phone number to this delivery staff account before assigning the order.");

                int updated;
                try (PreparedStatement p = c.prepareStatement("UPDATE deliveries SET delivery_staff_id=?,assigned_at=CURRENT_TIMESTAMP,status='OUT_FOR_DELIVERY' WHERE order_id=?")) {
                    p.setLong(1, deliveryStaffId); p.setLong(2, orderId);
                    updated = p.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO deliveries(order_id,delivery_staff_id,status,assigned_at) VALUES(?,?,'OUT_FOR_DELIVERY',CURRENT_TIMESTAMP)")) {
                        p.setLong(1, orderId); p.setLong(2, deliveryStaffId); p.executeUpdate();
                    }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE orders SET status='OUT_FOR_DELIVERY' WHERE id=?")) {
                    p.setLong(1, orderId); p.executeUpdate();
                }
                insertHistory(c, orderId, "OUT_FOR_DELIVERY", "Assigned to " + staffName + "; the order is now out for delivery.", actorId);
                notifyPatient(c, patientId, "Order out for delivery", "Order #" + orderId + " is out for delivery. Open order tracking to see your delivery person's contact number.");
                audit(c, actorId, "DELIVERY ASSIGNED", "Order #" + orderId + " assigned to delivery staff #" + deliveryStaffId + " and marked OUT_FOR_DELIVERY");
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    public void advance(long orderId, long actorId, String role, String next) throws SQLException {
        if (next == null) throw new IllegalArgumentException("Choose an order status.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String current; long patient;
                try (PreparedStatement p = c.prepareStatement("SELECT status,patient_id FROM orders WHERE id=? FOR UPDATE")) {
                    p.setLong(1, orderId);
                    try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Order not found."); current = r.getString(1); patient = r.getLong(2); }
                }
                // A duplicate form submission should be harmless after its first request succeeds.
                if (current.equals(next) && Set.of("PHARMACIST", "ADMIN").contains(role)) {
                    c.commit();
                    return;
                }
                if ("DELIVERY".equals(role)) {
                    try (PreparedStatement p = c.prepareStatement("SELECT delivery_staff_id FROM deliveries WHERE order_id=?")) {
                        p.setLong(1, orderId);
                        try (ResultSet r = p.executeQuery()) { if (!r.next() || r.getObject(1) == null || r.getLong(1) != actorId) throw new SecurityException("This delivery is not assigned to you."); }
                    }
                }
                if (Set.of("PROCESSING", "PACKED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED", "NOT_DELIVERED").contains(next)) {
                    try (PreparedStatement p = c.prepareStatement("SELECT status FROM order_prescriptions WHERE order_id=? FOR UPDATE")) {
                        p.setLong(1, orderId);
                        try (ResultSet r = p.executeQuery()) { if (r.next() && !"VERIFIED".equals(r.getString(1))) throw new IllegalArgumentException("Verify the uploaded prescription before processing this order."); }
                    }
                }
                boolean allowed = switch (role) {
                    case "PHARMACIST" -> ("PROCESSING".equals(next) && Set.of("PLACED", "CONFIRMED").contains(current)) || ("PACKED".equals(next) && "PROCESSING".equals(current)) || ("SHIPPED".equals(next) && Set.of("PROCESSING", "PACKED").contains(current));
                    case "DELIVERY" -> ("OUT_FOR_DELIVERY".equals(next) && "SHIPPED".equals(current)) || (Set.of("DELIVERED", "NOT_DELIVERED").contains(next) && "OUT_FOR_DELIVERY".equals(current));
                    case "ADMIN" -> Set.of("PROCESSING", "PACKED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED").contains(next);
                    default -> false;
                };
                if (!allowed) throw new IllegalArgumentException("Order #" + orderId + " is currently " + current + " and cannot move to " + next + " as " + role.toLowerCase(Locale.ROOT) + ". Refresh the queue and choose a valid next step.");
                if ("SHIPPED".equals(next)) {
                    try (PreparedStatement p = c.prepareStatement("SELECT delivery_staff_id FROM deliveries WHERE order_id=? FOR UPDATE")) {
                        p.setLong(1, orderId);
                        try (ResultSet r = p.executeQuery()) {
                            if (!r.next() || r.getObject(1) == null) throw new IllegalArgumentException("Assign delivery staff before shipping this order.");
                        }
                    }
                }
                if (!"NOT_DELIVERED".equals(next)) {
                    try (PreparedStatement p = c.prepareStatement("UPDATE orders SET status=? WHERE id=?")) { p.setString(1, next); p.setLong(2, orderId); p.executeUpdate(); }
                }
                if ("SHIPPED".equals(next)) try (PreparedStatement p = c.prepareStatement("UPDATE deliveries SET status='SHIPPED' WHERE order_id=?")) { p.setLong(1, orderId); p.executeUpdate(); }
                if ("DELIVERED".equals(next)) {
                    try (PreparedStatement p = c.prepareStatement("UPDATE orders SET payment_status='SUCCESS' WHERE id=? AND payment_method='COD'")) { p.setLong(1, orderId); p.executeUpdate(); }
                    try (PreparedStatement p = c.prepareStatement("UPDATE payments SET status='SUCCESS',updated_at=CURRENT_TIMESTAMP WHERE order_id=? AND payment_method='COD'")) { p.setLong(1, orderId); p.executeUpdate(); }
                }
                if ("DELIVERY".equals(role)) {
                    try (PreparedStatement p = c.prepareStatement("UPDATE deliveries SET status=?,delivered_at=CASE WHEN ?='DELIVERED' THEN CURRENT_TIMESTAMP ELSE delivered_at END WHERE order_id=?")) {
                        p.setString(1, next); p.setString(2, next); p.setLong(3, orderId); p.executeUpdate();
                    }
                }
                String note = "Updated by " + role.toLowerCase(Locale.ROOT) + ".";
                insertHistory(c, orderId, next, note, actorId);
                notifyPatient(c, patient, "Order update", "Order #" + orderId + " is now " + next.replace('_', ' ').toLowerCase(Locale.ROOT) + ".");
                audit(c, actorId, "ORDER STATUS", "Order #" + orderId + " moved from " + current + " to " + next);
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    public void cancelByPatient(long orderId, long patientId) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String status, paymentStatus;
                try (PreparedStatement p = c.prepareStatement("SELECT status,payment_status FROM orders WHERE id=? AND patient_id=? FOR UPDATE")) {
                    p.setLong(1, orderId); p.setLong(2, patientId);
                    try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Order not found."); status = r.getString(1); paymentStatus = r.getString(2); }
                }
                if (!Set.of("PLACED", "CONFIRMED", "PROCESSING", "PACKED").contains(status))
                    throw new IllegalArgumentException("This order can no longer be cancelled because it has shipped or is already closed.");
                boolean paid = "SUCCESS".equals(paymentStatus);
                try (PreparedStatement p = c.prepareStatement("UPDATE orders SET status='CANCELLED',payment_status=? WHERE id=?")) { p.setString(1, paid ? "SUCCESS" : "CANCELLED"); p.setLong(2, orderId); p.executeUpdate(); }
                restoreStock(c, orderId, "ORDER CANCELLED");
                if (paid) {
                    if (createRefund(c, orderId, patientId, "Patient cancelled the paid order before shipment.") == null)
                        throw new IllegalArgumentException("The order is marked paid, but no successful payment record was found. Contact an administrator before cancelling it.");
                }
                else try (PreparedStatement p = c.prepareStatement("UPDATE payments SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE order_id=? AND status IN ('PENDING','SUCCESS')")) { p.setLong(1, orderId); p.executeUpdate(); }
                try (PreparedStatement p = c.prepareStatement("UPDATE deliveries SET status='CANCELLED' WHERE order_id=?")) { p.setLong(1, orderId); p.executeUpdate(); }
                insertHistory(c, orderId, "CANCELLED", "Cancelled by patient before shipment. Stock was returned to inventory.", patientId);
                notifyPatient(c, patientId, "Order cancelled", "Order #" + orderId + " was cancelled. Reserved items were returned to stock." + (paid ? " A full demo refund request is waiting for administrator review." : " No online payment was collected, so no refund is due."));
                audit(c, patientId, "ORDER CANCELLED", "Patient cancelled order #" + orderId + " before shipment.");
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    /** Lets a patient request a refund after the pharmacy confirms the returned product was received. */
    public void requestRefundByPatient(long orderId, long patientId, String reason) throws SQLException {
        reason = cleanRefundNote(reason);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String orderStatus, paymentStatus;
                try (PreparedStatement p = c.prepareStatement("SELECT status,payment_status FROM orders WHERE id=? AND patient_id=? FOR UPDATE")) {
                    p.setLong(1, orderId); p.setLong(2, patientId);
                    try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Order not found."); orderStatus = r.getString("status"); paymentStatus = r.getString("payment_status"); }
                }
                String returnStatus = null;
                try (PreparedStatement p = c.prepareStatement("SELECT status FROM order_returns WHERE order_id=? FOR UPDATE")) {
                    p.setLong(1, orderId); try (ResultSet r = p.executeQuery()) { if (r.next()) returnStatus = r.getString(1); }
                }
                boolean eligibleReturn = "RECEIVED".equals(returnStatus);
                boolean eligibleCancellation = "CANCELLED".equals(orderStatus) && "SUCCESS".equals(paymentStatus);
                if (!eligibleReturn && !eligibleCancellation) throw new IllegalArgumentException("A refund can be requested after the pharmacy confirms it received your return, or for a paid order cancelled before shipment.");
                if (createRefund(c, orderId, patientId, reason) == null) throw new IllegalArgumentException("No successful payment was found for this order, so a refund cannot be requested.");
                notifyPatient(c, patientId, "Refund request submitted", "Your full demo refund request for order #" + orderId + (eligibleCancellation ? " for the cancelled paid order" : " after the pharmacy received your return") + " was sent to the administrator for review.");
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    /** Lets pharmacy staff initiate the full simulated refund after confirming receipt of a return. */
    public void initiateRefundByPharmacist(long orderId, long actorId, String reason) throws SQLException {
        reason = cleanRefundNote(reason);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String returnStatus; long patientId;
                try (PreparedStatement p = c.prepareStatement("SELECT patient_id,status FROM order_returns WHERE order_id=? FOR UPDATE")) {
                    p.setLong(1, orderId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Return request not found."); patientId = r.getLong(1); returnStatus = r.getString(2); }
                }
                if (!"RECEIVED".equals(returnStatus)) throw new IllegalArgumentException("Confirm the returned product was received before initiating a refund.");
                String refundReason = reason.isBlank() ? "Pharmacy initiated a full refund after confirming receipt of the returned product." : reason;
                if (createRefund(c, orderId, actorId, refundReason) == null) throw new IllegalArgumentException("No successful payment was found for this order, so a refund cannot be initiated.");
                notifyPatient(c, patientId, "Refund requested", "The pharmacy initiated a full demo refund for order #" + orderId + ". It is waiting for administrator review.");
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    public void requestReturnByPatient(long orderId, long patientId, String reason) throws SQLException {
        reason = reason == null ? "" : reason.trim();
        if (reason.length() > 500) throw new IllegalArgumentException("Return reason must be 500 characters or fewer.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String status; Timestamp deliveredAt;
                try (PreparedStatement p = c.prepareStatement("SELECT o.status,d.delivered_at FROM orders o JOIN deliveries d ON d.order_id=o.id WHERE o.id=? AND o.patient_id=? FOR UPDATE")) {
                    p.setLong(1, orderId); p.setLong(2, patientId);
                    try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Order not found."); status = r.getString(1); deliveredAt = r.getTimestamp(2); }
                }
                if (!"DELIVERED".equals(status) || deliveredAt == null) throw new IllegalArgumentException("A return can be requested after delivery only.");
                LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
                if (now.isAfter(deliveredAt.toLocalDateTime().plusDays(7))) throw new IllegalArgumentException("The seven-day return window has ended.");
                try (PreparedStatement p = c.prepareStatement("INSERT INTO order_returns(order_id,patient_id,status,reason) VALUES(?,?,'REQUESTED',?)")) {
                    p.setLong(1, orderId); p.setLong(2, patientId); p.setString(3, reason.isBlank() ? null : reason); p.executeUpdate();
                }
                insertHistory(c, orderId, "RETURN_REQUESTED", "Return requested by patient.", patientId);
                notifyPatient(c, patientId, "Return request submitted", "Your return request for order #" + orderId + " was sent to the pharmacy. The pharmacy will follow up about collection and refund.");
                try (PreparedStatement p = c.prepareStatement("INSERT INTO notifications(user_id,title,body) SELECT id,'Patient return request',? FROM users WHERE active=TRUE AND role IN ('PHARMACIST','ADMIN')")) {
                    p.setString(1, "Patient requested a return for order #" + orderId + "."); p.executeUpdate();
                }
                audit(c, patientId, "ORDER RETURN REQUESTED", "Patient requested a return for order #" + orderId + " within the seven-day window.");
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                if ("23505".equals(e.getSQLState())) throw new IllegalArgumentException("A return request has already been submitted for this order.");
                throw e;
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    /** Lists all return requests with the private workflow data needed by pharmacy staff. */
    public List<Map<String, Object>> returnQueue() throws SQLException {
        String sql = "SELECT r.order_id,r.patient_id,r.status,r.reason,r.requested_at,r.review_note,r.image_required,r.image_file,r.image_uploaded_at,r.pickup_status,r.pickup_scheduled_at,r.pickup_completed_at,o.total,u.full_name patient_name,COALESCE(u.phone,'') patient_phone,COALESCE(a.line1,'Address not provided') address,COALESCE(a.city,'') city,COALESCE(a.district,'') district,COALESCE(a.state,'') state,COALESCE(a.postal_code,'') postal_code,d.delivered_at,s.full_name pickup_staff_name,COALESCE(s.phone,'') pickup_staff_phone,(SELECT rf.status FROM refunds rf JOIN payments rfp ON rfp.id=rf.payment_id WHERE rfp.order_id=o.id ORDER BY rf.id DESC LIMIT 1) refund_status FROM order_returns r JOIN orders o ON o.id=r.order_id JOIN users u ON u.id=r.patient_id LEFT JOIN addresses a ON a.id=o.address_id LEFT JOIN deliveries d ON d.order_id=o.id LEFT JOIN users s ON s.id=r.pickup_staff_id ORDER BY CASE r.status WHEN 'REQUESTED' THEN 0 WHEN 'APPROVED' THEN 1 ELSE 2 END,r.requested_at";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            List<Map<String, Object>> out = new ArrayList<>();
            while (r.next()) {
                long orderId = r.getLong("order_id");
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderId", orderId); m.put("patientId", r.getLong("patient_id")); m.put("status", r.getString("status"));
                m.put("reason", r.getString("reason")); m.put("requestedAt", localDateTime(r.getTimestamp("requested_at")));
                m.put("reviewNote", r.getString("review_note")); m.put("imageRequired", r.getBoolean("image_required"));
                m.put("imageUploaded", r.getString("image_file") != null); m.put("imageUploadedAt", localDateTime(r.getTimestamp("image_uploaded_at")));
                m.put("pickupStatus", r.getString("pickup_status")); m.put("pickupScheduledAt", localDateTime(r.getTimestamp("pickup_scheduled_at")));
                m.put("pickupCompletedAt", localDateTime(r.getTimestamp("pickup_completed_at"))); m.put("pickupStaffName", r.getString("pickup_staff_name"));
                m.put("pickupStaffPhone", r.getString("pickup_staff_phone")); m.put("total", r.getBigDecimal("total")); m.put("patient", r.getString("patient_name"));
                m.put("refundStatus", r.getString("refund_status"));
                m.put("patientPhone", r.getString("patient_phone")); m.put("address", r.getString("address")); m.put("city", r.getString("city"));
                m.put("district", r.getString("district")); m.put("state", r.getString("state")); m.put("postalCode", r.getString("postal_code"));
                m.put("deliveredAt", localDateTime(r.getTimestamp("delivered_at"))); m.put("items", itemsForOrder(c, orderId)); out.add(m);
            }
            return out;
        }
    }

    /** Refunds tied to pharmacy orders; appointment refunds remain outside this order panel. */
    public List<Map<String, Object>> refundQueue() throws SQLException {
        String sql = "SELECT rf.id,rf.amount,rf.status,rf.reason,rf.review_note,rf.created_at,rf.reviewed_at,rf.processed_at,p.id payment_id,p.payment_method,p.transaction_id,o.id order_id,o.status order_status,u.id patient_id,u.full_name patient_name,u.email patient_email,i.full_name initiated_by_name,i.role initiated_by_role,v.full_name reviewed_by_name FROM refunds rf JOIN payments p ON p.id=rf.payment_id JOIN orders o ON o.id=p.order_id JOIN users u ON u.id=o.patient_id LEFT JOIN users i ON i.id=rf.initiated_by LEFT JOIN users v ON v.id=rf.reviewed_by ORDER BY CASE rf.status WHEN 'PENDING' THEN 0 WHEN 'APPROVED' THEN 1 WHEN 'REJECTED' THEN 2 ELSE 3 END,rf.created_at DESC,rf.id DESC";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            List<Map<String, Object>> out = new ArrayList<>();
            while (r.next()) {
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("id", r.getLong("id")); item.put("amount", r.getBigDecimal("amount")); item.put("status", r.getString("status"));
                item.put("reason", r.getString("reason")); item.put("reviewNote", r.getString("review_note"));
                item.put("createdAt", localDateTime(r.getTimestamp("created_at"))); item.put("reviewedAt", localDateTime(r.getTimestamp("reviewed_at")));
                item.put("processedAt", localDateTime(r.getTimestamp("processed_at"))); item.put("paymentId", r.getLong("payment_id"));
                item.put("method", r.getString("payment_method")); item.put("transactionId", r.getString("transaction_id"));
                item.put("orderId", r.getLong("order_id")); item.put("orderStatus", r.getString("order_status"));
                item.put("patientId", r.getLong("patient_id")); item.put("patient", r.getString("patient_name")); item.put("patientEmail", r.getString("patient_email"));
                item.put("initiatedBy", r.getString("initiated_by_name")); item.put("initiatedByRole", r.getString("initiated_by_role"));
                item.put("reviewedBy", r.getString("reviewed_by_name")); out.add(item);
            }
            return out;
        }
    }

    public String returnPhotoFile(long orderId) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT image_file FROM order_returns WHERE order_id=?")) {
            p.setLong(1, orderId);
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    /** Returns the previous file name so the caller can remove a replaced private photo after commit. */
    public String requestReturnPhoto(long orderId, long actorId, String note) throws SQLException {
        note = cleanReturnNote(note);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long patientId; String oldFile;
                try (PreparedStatement p = c.prepareStatement("SELECT patient_id,status,image_file FROM order_returns WHERE order_id=? FOR UPDATE")) {
                    p.setLong(1, orderId);
                    try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Return request not found."); patientId = r.getLong(1); if (!"REQUESTED".equals(r.getString(2))) throw new IllegalArgumentException("Only a pending return request can ask for a product photo."); oldFile = r.getString(3); }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE order_returns SET image_required=TRUE,image_file=NULL,image_uploaded_at=NULL,review_note=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE order_id=?")) {
                    p.setString(1, note.isBlank() ? "Please upload a clear photo of the product and its packaging for return verification." : note);
                    p.setLong(2, actorId); p.setLong(3, orderId); p.executeUpdate();
                }
                insertHistory(c, orderId, "RETURN_IMAGE_REQUESTED", "Pharmacy requested a product photo for return verification.", actorId);
                notifyPatient(c, patientId, "Return photo requested", "The pharmacy needs a product photo for your return request for order #" + orderId + ". Open My orders to upload it.");
                audit(c, actorId, "RETURN PHOTO REQUESTED", "Requested a product photo for order return #" + orderId);
                c.commit(); return oldFile;
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    public void submitReturnImage(long orderId, long patientId, String fileName) throws SQLException {
        if (fileName == null || !fileName.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) throw new IllegalArgumentException("The product photo could not be saved. Please try again.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("UPDATE order_returns SET image_file=?,image_uploaded_at=CURRENT_TIMESTAMP,review_note=NULL WHERE order_id=? AND patient_id=? AND status='REQUESTED' AND image_required=TRUE AND image_file IS NULL")) {
                    p.setString(1, fileName); p.setLong(2, orderId); p.setLong(3, patientId);
                    if (p.executeUpdate() != 1) throw new IllegalArgumentException("The pharmacy has not requested a photo for this return, or the return is no longer awaiting one.");
                }
                insertHistory(c, orderId, "RETURN_IMAGE_SUBMITTED", "Patient uploaded the requested return product photo.", patientId);
                try (PreparedStatement p = c.prepareStatement("INSERT INTO notifications(user_id,title,body) SELECT id,'Return photo uploaded',? FROM users WHERE active=TRUE AND role IN ('PHARMACIST','ADMIN')")) {
                    p.setString(1, "A product photo was uploaded for return request on order #" + orderId + "."); p.executeUpdate();
                }
                audit(c, patientId, "RETURN PHOTO UPLOADED", "Patient uploaded a product photo for order return #" + orderId);
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    public void rejectReturn(long orderId, long actorId, String note) throws SQLException {
        note = cleanReturnNote(note);
        if (note.isBlank()) throw new IllegalArgumentException("Add a short reason before rejecting the return.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long patientId;
                try (PreparedStatement p = c.prepareStatement("SELECT patient_id,status FROM order_returns WHERE order_id=? FOR UPDATE")) {
                    p.setLong(1, orderId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Return request not found."); patientId = r.getLong(1); if (!"REQUESTED".equals(r.getString(2))) throw new IllegalArgumentException("This return request has already been reviewed."); }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE order_returns SET status='REJECTED',review_note=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,image_required=FALSE WHERE order_id=?")) {
                    p.setString(1, note); p.setLong(2, actorId); p.setLong(3, orderId); p.executeUpdate();
                }
                insertHistory(c, orderId, "RETURN_REJECTED", "Pharmacy declined the return: " + note, actorId);
                notifyPatient(c, patientId, "Return request update", "The pharmacy could not approve the return for order #" + orderId + ". Reason: " + note);
                audit(c, actorId, "RETURN REJECTED", "Rejected order return #" + orderId);
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    public void approveReturnAndSchedulePickup(long orderId, long staffId, LocalDateTime scheduledAt, long actorId, String note) throws SQLException {
        note = cleanReturnNote(note);
        if (scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))) throw new IllegalArgumentException("Choose a future pickup date and time.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long patientId; boolean imageRequired; String imageFile;
                try (PreparedStatement p = c.prepareStatement("SELECT patient_id,status,image_required,image_file FROM order_returns WHERE order_id=? FOR UPDATE")) {
                    p.setLong(1, orderId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Return request not found."); patientId = r.getLong(1); if (!"REQUESTED".equals(r.getString(2))) throw new IllegalArgumentException("This return request has already been reviewed."); imageRequired = r.getBoolean(3); imageFile = r.getString(4); }
                }
                if (imageRequired && imageFile == null) throw new IllegalArgumentException("Wait for the patient to upload the requested product photo before approving.");
                String staffName; String phone;
                try (PreparedStatement p = c.prepareStatement("SELECT full_name,phone FROM users WHERE id=? AND role='DELIVERY' AND active=TRUE FOR UPDATE")) {
                    p.setLong(1, staffId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Choose an active delivery staff account."); staffName = r.getString(1); phone = r.getString(2); }
                }
                if (phone == null || phone.isBlank()) throw new IllegalArgumentException("The assigned delivery staff account needs a contact number.");
                try (PreparedStatement p = c.prepareStatement("UPDATE order_returns SET status='APPROVED',review_note=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,pickup_staff_id=?,pickup_scheduled_at=?,pickup_status='SCHEDULED' WHERE order_id=? AND status='REQUESTED'")) {
                    p.setString(1, note.isBlank() ? "Product return approved after review." : note); p.setLong(2, actorId); p.setLong(3, staffId); p.setTimestamp(4, Timestamp.valueOf(scheduledAt)); p.setLong(5, orderId); p.executeUpdate();
                }
                String description = "Return pickup scheduled for " + scheduledAt + " with " + staffName + ".";
                insertHistory(c, orderId, "RETURN_PICKUP_SCHEDULED", description, actorId);
                notifyPatient(c, patientId, "Return pickup scheduled", "Return for order #" + orderId + " was approved. Pickup is scheduled for " + scheduledAt + ". Contact " + staffName + " at " + phone + ".");
                notifyPatient(c, staffId, "Return pickup assigned", "You have a return pickup for order #" + orderId + " scheduled at " + scheduledAt + ". Open Return pickups for the patient address and contact.");
                audit(c, actorId, "RETURN PICKUP SCHEDULED", "Approved order return #" + orderId + " and assigned delivery staff #" + staffId);
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    public List<Map<String, Object>> returnPickupQueue(long staffId) throws SQLException {
        String sql = "SELECT r.order_id,r.patient_id,r.reason,r.pickup_scheduled_at,r.pickup_status,o.total,u.full_name patient_name,COALESCE(u.phone,'') patient_phone,COALESCE(a.line1,'Address not provided') address,COALESCE(a.city,'') city,COALESCE(a.district,'') district,COALESCE(a.state,'') state,COALESCE(a.postal_code,'') postal_code FROM order_returns r JOIN orders o ON o.id=r.order_id JOIN users u ON u.id=r.patient_id LEFT JOIN addresses a ON a.id=o.address_id WHERE r.pickup_staff_id=? AND r.status='APPROVED' AND r.pickup_status='SCHEDULED' ORDER BY r.pickup_scheduled_at";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, staffId);
            try (ResultSet r = p.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (r.next()) {
                    long orderId = r.getLong("order_id"); Map<String, Object> m = new LinkedHashMap<>();
                    m.put("orderId", orderId); m.put("patient", r.getString("patient_name")); m.put("patientPhone", r.getString("patient_phone"));
                    m.put("address", r.getString("address")); m.put("city", r.getString("city")); m.put("district", r.getString("district"));
                    m.put("state", r.getString("state")); m.put("postalCode", r.getString("postal_code")); m.put("reason", r.getString("reason"));
                    m.put("total", r.getBigDecimal("total")); m.put("pickupScheduledAt", localDateTime(r.getTimestamp("pickup_scheduled_at")));
                    m.put("pickupStatus", r.getString("pickup_status")); m.put("items", itemsForOrder(c, orderId)); out.add(m);
                }
                return out;
            }
        }
    }

    public void markReturnPickedUp(long orderId, long staffId) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long patientId;
                try (PreparedStatement p = c.prepareStatement("SELECT patient_id FROM order_returns WHERE order_id=? AND pickup_staff_id=? AND status='APPROVED' AND pickup_status='SCHEDULED' FOR UPDATE")) {
                    p.setLong(1, orderId); p.setLong(2, staffId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("This return pickup is not assigned to you or is no longer scheduled."); patientId = r.getLong(1); }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE order_returns SET pickup_status='PICKED_UP',pickup_completed_at=CURRENT_TIMESTAMP WHERE order_id=?")) { p.setLong(1, orderId); p.executeUpdate(); }
                insertHistory(c, orderId, "RETURN_PICKED_UP", "Assigned delivery staff collected the return product.", staffId);
                notifyPatient(c, patientId, "Return product picked up", "The return product for order #" + orderId + " has been collected. The pharmacy will confirm receipt.");
                try (PreparedStatement p = c.prepareStatement("INSERT INTO notifications(user_id,title,body) SELECT id,'Return pickup completed',? FROM users WHERE active=TRUE AND role IN ('PHARMACIST','ADMIN')")) { p.setString(1, "The return product for order #" + orderId + " was collected."); p.executeUpdate(); }
                audit(c, staffId, "RETURN PICKED UP", "Collected return product for order #" + orderId);
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    public void markReturnReceived(long orderId, long actorId, String note) throws SQLException {
        note = cleanReturnNote(note);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long patientId;
                try (PreparedStatement p = c.prepareStatement("SELECT patient_id FROM order_returns WHERE order_id=? AND status='APPROVED' AND pickup_status='PICKED_UP' FOR UPDATE")) {
                    p.setLong(1, orderId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("The return product must be picked up before receipt can be confirmed."); patientId = r.getLong(1); }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE order_returns SET status='RECEIVED',review_note=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE order_id=?")) {
                    p.setString(1, note.isBlank() ? "Pharmacy confirmed receipt of the returned product." : note); p.setLong(2, actorId); p.setLong(3, orderId); p.executeUpdate();
                }
                insertHistory(c, orderId, "RETURN_RECEIVED", "Pharmacy confirmed the returned product was received.", actorId);
                notifyPatient(c, patientId, "Return received by pharmacy", "The pharmacy confirmed receipt of the returned product for order #" + orderId + ".");
                audit(c, actorId, "RETURN RECEIVED", "Confirmed return product receipt for order #" + orderId);
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    /** Admin review step for a patient or pharmacist refund initiation. */
    public void reviewRefund(long refundId, long actorId, String decision, String note) throws SQLException {
        if (!Set.of("APPROVED", "REJECTED").contains(decision)) throw new IllegalArgumentException("Choose approve or reject.");
        note = cleanRefundNote(note);
        if ("REJECTED".equals(decision) && note.isBlank()) throw new IllegalArgumentException("Add a reason before rejecting this refund request.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long paymentId;
                try (PreparedStatement p = c.prepareStatement("SELECT payment_id,status FROM refunds WHERE id=? FOR UPDATE")) {
                    p.setLong(1, refundId); try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Refund request not found.");
                        paymentId = r.getLong("payment_id");
                        if (!"PENDING".equals(r.getString("status"))) throw new IllegalArgumentException("This refund request has already been reviewed.");
                    }
                }
                long orderId, patientId;
                try (PreparedStatement p = c.prepareStatement("SELECT p.order_id,o.patient_id FROM payments p JOIN orders o ON o.id=p.order_id WHERE p.id=? FOR UPDATE")) {
                    p.setLong(1, paymentId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("This refund is not linked to a pharmacy order."); orderId = r.getLong(1); patientId = r.getLong(2); }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE refunds SET status=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_note=? WHERE id=? AND status='PENDING'")) {
                    p.setString(1, decision); p.setLong(2, actorId); p.setString(3, note.isBlank() ? null : note); p.setLong(4, refundId);
                    if (p.executeUpdate() != 1) throw new IllegalArgumentException("This refund request has already been reviewed.");
                }
                String patientMessage = "Refund request for order #" + orderId + ("APPROVED".equals(decision) ? " was approved and is awaiting processing." : " was declined. Reason: " + note);
                notifyPatient(c, patientId, "Refund request " + decision.toLowerCase(Locale.ROOT), patientMessage);
                insertStaffNotification(c, "Refund request " + decision.toLowerCase(Locale.ROOT), "Refund #" + refundId + " for order #" + orderId + " was " + decision.toLowerCase(Locale.ROOT) + ".", new String[]{"PHARMACIST"});
                audit(c, actorId, "REFUND " + decision, "Refund #" + refundId + " for order #" + orderId + " was " + decision.toLowerCase(Locale.ROOT));
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    /** Marks an approved demo refund complete and updates the order/payment records. */
    public void processRefund(long refundId, long actorId, String note) throws SQLException {
        note = cleanRefundNote(note);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long paymentId; BigDecimal refundAmount; String status;
                try (PreparedStatement p = c.prepareStatement("SELECT payment_id,amount,status FROM refunds WHERE id=? FOR UPDATE")) {
                    p.setLong(1, refundId); try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Refund request not found.");
                        paymentId = r.getLong("payment_id"); refundAmount = r.getBigDecimal("amount"); status = r.getString("status");
                    }
                }
                if (!"APPROVED".equals(status)) throw new IllegalArgumentException("Approve the refund request before marking it processed.");
                long orderId, patientId; BigDecimal paymentAmount; String paymentStatus;
                try (PreparedStatement p = c.prepareStatement("SELECT p.order_id,p.user_id,p.amount,p.status FROM payments p JOIN orders o ON o.id=p.order_id WHERE p.id=? FOR UPDATE")) {
                    p.setLong(1, paymentId); try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("This refund is not linked to a pharmacy order.");
                        orderId = r.getLong("order_id"); patientId = r.getLong("user_id"); paymentAmount = r.getBigDecimal("amount"); paymentStatus = r.getString("status");
                    }
                }
                if (!"SUCCESS".equals(paymentStatus)) throw new IllegalArgumentException("The original payment is not in a refundable state.");
                if (refundAmount == null || refundAmount.signum() <= 0 || paymentAmount == null || refundAmount.compareTo(paymentAmount) != 0) throw new IllegalArgumentException("This workflow only supports a full refund matching the original order payment.");
                try (PreparedStatement p = c.prepareStatement("UPDATE payments SET status='REFUNDED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='SUCCESS'")) {
                    p.setLong(1, paymentId); if (p.executeUpdate() != 1) throw new IllegalArgumentException("The original payment has already been refunded.");
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE orders SET payment_status='REFUNDED' WHERE id=?")) { p.setLong(1, orderId); p.executeUpdate(); }
                try (PreparedStatement p = c.prepareStatement("SELECT status,review_note FROM order_returns WHERE order_id=? FOR UPDATE")) {
                    p.setLong(1, orderId); try (ResultSet r = p.executeQuery()) {
                        if (r.next() && "RECEIVED".equals(r.getString("status"))) {
                            String returnNote = r.getString("review_note");
                            String processedNote = note.isBlank() ? "Full demo refund processed by administrator." : note;
                            String combined = returnNote == null || returnNote.isBlank() ? processedNote : returnNote + " · " + processedNote;
                            try (PreparedStatement update = c.prepareStatement("UPDATE order_returns SET status='REFUNDED',review_note=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE order_id=?")) { update.setString(1, combined); update.setLong(2, actorId); update.setLong(3, orderId); update.executeUpdate(); }
                            try (PreparedStatement update = c.prepareStatement("UPDATE orders SET status='RETURNED' WHERE id=? AND status='DELIVERED'")) { update.setLong(1, orderId); update.executeUpdate(); }
                        }
                    }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE refunds SET status='PROCESSED',processed_by=?,processed_at=CURRENT_TIMESTAMP,review_note=CASE WHEN ?='' THEN review_note ELSE ? END WHERE id=? AND status='APPROVED'")) {
                    p.setLong(1, actorId); p.setString(2, note); p.setString(3, note); p.setLong(4, refundId);
                    if (p.executeUpdate() != 1) throw new IllegalArgumentException("This refund is no longer awaiting processing.");
                }
                String history = "Full demo refund of ₹" + refundAmount + " marked processed. No bank transfer was made.";
                insertHistory(c, orderId, "REFUND_PROCESSED", history, actorId);
                notifyPatient(c, patientId, "Demo refund processed", "A refund of ₹" + refundAmount + " for order #" + orderId + " was marked processed in Online Medic. No bank transfer was made.");
                audit(c, actorId, "REFUND PROCESSED", "Refund #" + refundId + " for order #" + orderId + " amount ₹" + refundAmount + " was marked processed in demo mode.");
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    private String cleanReturnNote(String note) {
        String value = note == null ? "" : note.trim();
        if (value.length() > 500) throw new IllegalArgumentException("Return notes must be 500 characters or fewer.");
        return value;
    }

    public void reviewPrescription(long orderId, long actorId, String role, String decision) throws SQLException {
        if (!Set.of("PHARMACIST", "ADMIN").contains(role)) throw new SecurityException("Only pharmacy staff can review order prescriptions.");
        if (decision == null || !Set.of("VERIFIED", "REJECTED").contains(decision)) throw new IllegalArgumentException("Choose verify or reject.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long patientId; String current, paymentStatus;
                try (PreparedStatement p = c.prepareStatement("SELECT op.patient_id,op.status,o.payment_status FROM order_prescriptions op JOIN orders o ON o.id=op.order_id WHERE op.order_id=? FOR UPDATE")) {
                    p.setLong(1, orderId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("No uploaded prescription was found for this order."); patientId = r.getLong(1); current = r.getString(2); paymentStatus = r.getString(3); }
                }
                if (!"PENDING".equals(current)) throw new IllegalArgumentException("This uploaded prescription has already been reviewed.");
                try (PreparedStatement p = c.prepareStatement("UPDATE order_prescriptions SET status=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_note=? WHERE order_id=? AND status='PENDING'")) {
                    p.setString(1, decision); p.setLong(2, actorId); p.setString(3, "REJECTED".equals(decision) ? "Prescription not accepted. Please contact the pharmacy." : null); p.setLong(4, orderId); p.executeUpdate();
                }
                if ("REJECTED".equals(decision)) {
                    boolean paid = "SUCCESS".equals(paymentStatus);
                    try (PreparedStatement p = c.prepareStatement("UPDATE orders SET status='CANCELLED',payment_status=? WHERE id=? AND status='PLACED'")) {
                        p.setString(1, paid ? "SUCCESS" : "CANCELLED"); p.setLong(2, orderId); if (p.executeUpdate() != 1) throw new IllegalArgumentException("The order has already moved forward and cannot be cancelled from prescription review.");
                    }
                    restoreStock(c, orderId, "PRESCRIPTION REJECTED");
                    if (paid) {
                        if (createRefund(c, orderId, actorId, "Order cancelled after the uploaded prescription was rejected.") == null)
                            throw new IllegalArgumentException("The order is marked paid, but no successful payment record was found. Contact an administrator before completing prescription review.");
                    } else try (PreparedStatement p = c.prepareStatement("UPDATE payments SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE order_id=? AND status='PENDING'")) { p.setLong(1, orderId); p.executeUpdate(); }
                    try (PreparedStatement p = c.prepareStatement("UPDATE deliveries SET status='CANCELLED' WHERE order_id=?")) { p.setLong(1, orderId); p.executeUpdate(); }
                    insertHistory(c, orderId, "CANCELLED", "Order cancelled because the uploaded prescription was rejected.", actorId);
                }
                String message = "Prescription for order #" + orderId + " was " + decision.toLowerCase(Locale.ROOT)
                        + ("REJECTED".equals(decision) ? ". The order was cancelled and reserved stock restored." + ("SUCCESS".equals(paymentStatus) ? " A full demo refund request is waiting for administrator review." : " No online payment was collected, so no refund is due.") : ". The pharmacy can now process your order.");
                notifyPatient(c, patientId, "Order prescription review", message);
                audit(c, actorId, "ORDER PRESCRIPTION REVIEW", "Order #" + orderId + " prescription " + decision.toLowerCase(Locale.ROOT));
                c.commit();
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException x) throw x; if (e instanceof RuntimeException x) throw x; throw new SQLException(e); }
        }
    }

    private Long createRefund(Connection c, long orderId, long actorId, String reason) throws SQLException {
        reason = cleanRefundNote(reason);
        long paymentId; BigDecimal amount;
        try (PreparedStatement p = c.prepareStatement("SELECT id,amount FROM payments WHERE order_id=? AND status='SUCCESS' ORDER BY id DESC LIMIT 1 FOR UPDATE")) {
            p.setLong(1, orderId);
            try (ResultSet r = p.executeQuery()) { if (!r.next()) return null; paymentId = r.getLong("id"); amount = r.getBigDecimal("amount"); }
        }
        if (amount == null || amount.signum() <= 0) return null;
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM refunds WHERE payment_id=? AND status IN ('PENDING','APPROVED','PROCESSED')")) {
            p.setLong(1, paymentId);
            try (ResultSet r = p.executeQuery()) { if (r.next()) throw new IllegalArgumentException("A refund request for this payment is already pending, approved, or processed."); }
        }
        long refundId;
        try (PreparedStatement p = c.prepareStatement("INSERT INTO refunds(payment_id,amount,status,reason,initiated_by) VALUES(?,?,'PENDING',?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, paymentId); p.setBigDecimal(2, amount); p.setString(3, reason.isBlank() ? "Full order refund requested." : reason); p.setLong(4, actorId); p.executeUpdate();
            try (ResultSet keys = p.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("No refund identifier returned."); refundId = keys.getLong(1); }
        }
        insertHistory(c, orderId, "REFUND_REQUESTED", "Full demo refund of ₹" + amount + " submitted for administrator review.", actorId);
        insertStaffNotification(c, "Refund request needs review", "A full demo refund was initiated for order #" + orderId + ".", new String[]{"ADMIN", "PHARMACIST"});
        audit(c, actorId, "REFUND INITIATED", "Refund #" + refundId + " for order #" + orderId + " amount ₹" + amount + " submitted for admin review.");
        return refundId;
    }

    private String cleanRefundNote(String note) {
        String value = note == null ? "" : note.trim();
        if (value.length() > 500) throw new IllegalArgumentException("Refund reason or note must be 500 characters or fewer.");
        return value;
    }

    private void insertStaffNotification(Connection c, String title, String body, String[] roles) throws SQLException {
        if (roles == null || roles.length == 0) return;
        String marks = String.join(",", Collections.nCopies(roles.length, "?"));
        try (PreparedStatement p = c.prepareStatement("INSERT INTO notifications(user_id,title,body) SELECT id,?,? FROM users WHERE active=TRUE AND role IN (" + marks + ")")) {
            p.setString(1, title); p.setString(2, body); for (int i = 0; i < roles.length; i++) p.setString(3 + i, roles[i]); p.executeUpdate();
        }
    }

    private void restoreStock(Connection c, long orderId, String reason) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id,medicine_id,quantity FROM order_items WHERE order_id=?")) {
            p.setLong(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    long itemId = r.getLong(1), medicineId = r.getLong(2); int quantity = r.getInt(3);
                    try (PreparedStatement batches = c.prepareStatement("SELECT batch_id,quantity FROM order_item_batches WHERE order_item_id=?")) {
                        batches.setLong(1, itemId);
                        try (ResultSet allocations = batches.executeQuery()) {
                            while (allocations.next()) {
                                try (PreparedStatement restore = c.prepareStatement("UPDATE medicine_batches SET quantity=quantity+? WHERE id=?")) {
                                    restore.setInt(1, allocations.getInt("quantity")); restore.setLong(2, allocations.getLong("batch_id")); restore.executeUpdate();
                                }
                            }
                        }
                    }
                    try (PreparedStatement update = c.prepareStatement("UPDATE medicines SET stock_quantity=stock_quantity+? WHERE id=?")) { update.setInt(1, quantity); update.setLong(2, medicineId); update.executeUpdate(); }
                    try (PreparedStatement log = c.prepareStatement("INSERT INTO inventory_transactions(medicine_id,quantity_change,reason,reference_id) VALUES(?,?,?,?)")) { log.setLong(1, medicineId); log.setInt(2, quantity); log.setString(3, reason); log.setLong(4, orderId); log.executeUpdate(); }
                }
            }
        }
    }

    public void insertHistory(Connection c, long orderId, String status, String note, Long actorId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO order_status_history(order_id,status,note,changed_by) VALUES(?,?,?,?)")) {
            p.setLong(1, orderId); p.setString(2, status); p.setString(3, note); if (actorId == null) p.setNull(4, Types.BIGINT); else p.setLong(4, actorId); p.executeUpdate();
        }
    }

    private void notifyPatient(Connection c, long patientId, String title, String body) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO notifications(user_id,title,body) VALUES(?,?,?)")) { p.setLong(1, patientId); p.setString(2, title); p.setString(3, body); p.executeUpdate(); }
    }

    private void audit(Connection c, long userId, String action, String detail) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,?,?)")) { p.setLong(1, userId); p.setString(2, action); p.setString(3, detail); p.executeUpdate(); }
    }

    private int countSql(String sql) throws SQLException {
        try (Connection c = Database.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getInt(1); }
    }
}
