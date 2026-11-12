package com.onlinemedic.service;

import com.onlinemedic.dao.OrderDao;
import com.onlinemedic.dao.UserDao;
import com.onlinemedic.util.Database;

import java.sql.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class DashboardService {
    private final UserDao users = new UserDao();
    private final OrderDao orders = new OrderDao();

    public Map<String, Integer> adminStats() throws SQLException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("patients", users.countRole("PATIENT"));
        stats.put("doctors", users.countRole("DOCTOR"));
        stats.put("pharmacists", users.countRole("PHARMACIST"));
        stats.put("delivery", users.countRole("DELIVERY"));
        stats.put("medicines", count("SELECT COUNT(*) FROM medicines"));
        stats.put("orders", orders.count());
        stats.put("appointments", count("SELECT COUNT(*) FROM appointments"));
        stats.put("pending", orders.countPending());
        stats.put("refundsPending", orders.countRefundsPending());
        stats.put("revenue", revenue());
        stats.put("lowStock", count("SELECT COUNT(*) FROM medicines m WHERE m.price>0 AND (GREATEST(0,m.stock_quantity-COALESCE((SELECT SUM(b.quantity) FROM medicine_batches b WHERE b.medicine_id=m.id),0))+COALESCE((SELECT SUM(b.quantity) FROM medicine_batches b WHERE b.medicine_id=m.id AND b.expiry_date>=CURRENT_DATE),0))<=m.min_stock"));
        stats.put("expiredBatches", count("SELECT COUNT(*) FROM medicine_batches WHERE quantity>0 AND expiry_date<CURRENT_DATE"));
        stats.put("expiringBatches", countDate("SELECT COUNT(*) FROM medicine_batches WHERE quantity>0 AND expiry_date>=CURRENT_DATE AND expiry_date<=?", LocalDate.now().plusDays(30)));
        return stats;
    }

    public Map<String, Integer> doctorStats(long id) throws SQLException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("today", countParam("SELECT COUNT(*) FROM appointments WHERE doctor_id=? AND appointment_date=CURRENT_DATE AND status IN ('PENDING','CONFIRMED')", id));
        stats.put("upcoming", countParam("SELECT COUNT(*) FROM appointments WHERE doctor_id=? AND appointment_date>=CURRENT_DATE AND status IN ('PENDING','CONFIRMED')", id));
        stats.put("completed", countParam("SELECT COUNT(*) FROM appointments WHERE doctor_id=? AND status='COMPLETED'", id));
        stats.put("patients", countParam("SELECT COUNT(DISTINCT patient_id) FROM appointments WHERE doctor_id=?", id));
        stats.put("earnings", amountParam("SELECT COALESCE(SUM(p.amount),0) FROM payments p JOIN appointments a ON a.id=p.appointment_id WHERE a.doctor_id=? AND p.status='SUCCESS'", id));
        stats.put("prescriptions", countParam("SELECT COUNT(*) FROM prescriptions WHERE doctor_id=? AND status='ISSUED'", id));
        return stats;
    }

    public Map<String, Integer> patientStats(long id) throws SQLException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("appointments", countParam("SELECT COUNT(*) FROM appointments WHERE patient_id=?", id));
        stats.put("upcoming", countParam("SELECT COUNT(*) FROM appointments WHERE patient_id=? AND status='CONFIRMED' AND appointment_date>=CURRENT_DATE", id));
        stats.put("orders", countParam("SELECT COUNT(*) FROM orders WHERE patient_id=?", id));
        stats.put("prescriptions", countParam("SELECT COUNT(*) FROM prescriptions WHERE patient_id=?", id));
        return stats;
    }

    public int unread(long id) throws SQLException {
        return countParam("SELECT COUNT(*) FROM notifications WHERE user_id=? AND read_at IS NULL", id);
    }

    private int count(String sql) throws SQLException {
        try (Connection c = Database.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getInt(1); }
    }

    private int countParam(String sql, long id) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) { p.setLong(1, id); try (ResultSet r = p.executeQuery()) { r.next(); return r.getInt(1); } }
    }

    private int countDate(String sql, LocalDate date) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) { p.setObject(1, date); try (ResultSet r = p.executeQuery()) { r.next(); return r.getInt(1); } }
    }

    private int amountParam(String sql, long id) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) { p.setLong(1, id); try (ResultSet r = p.executeQuery()) { r.next(); return r.getBigDecimal(1).intValue(); } }
    }

    private int revenue() throws SQLException {
        try (Connection c = Database.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='SUCCESS'")) { r.next(); return r.getBigDecimal(1).intValue(); }
    }
}
