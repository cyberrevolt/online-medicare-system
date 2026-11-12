package com.onlinemedic.service;

import com.onlinemedic.util.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdminReportService {
    public Map<String, Object> last30Days() throws SQLException {
        LocalDateTime from = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime until = LocalDate.now().plusDays(1).atStartOfDay();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("from", from.toLocalDate());
        report.put("to", until.toLocalDate().minusDays(1));
        report.put("orders", countRange("SELECT COUNT(*) FROM orders WHERE created_at>=? AND created_at<?", from, until));
        report.put("orderRevenue", sumRange("SELECT COALESCE(SUM(amount),0) FROM payments WHERE order_id IS NOT NULL AND status='SUCCESS' AND created_at>=? AND created_at<?", from, until));
        report.put("appointments", countRange("SELECT COUNT(*) FROM appointments WHERE created_at>=? AND created_at<?", from, until));
        report.put("appointmentRevenue", sumRange("SELECT COALESCE(SUM(amount),0) FROM payments WHERE appointment_id IS NOT NULL AND status='SUCCESS' AND created_at>=? AND created_at<?", from, until));
        report.put("unitsSold", countRange("SELECT COALESCE(SUM(oi.quantity),0) FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.status NOT IN ('CANCELLED','RETURNED') AND o.created_at>=? AND o.created_at<?", from, until));
        report.put("openReturns", count("SELECT COUNT(*) FROM order_returns WHERE status IN ('REQUESTED','APPROVED')"));
        report.put("topMedicines", topMedicines(from, until));
        return report;
    }

    private List<Map<String, Object>> topMedicines(LocalDateTime from, LocalDateTime until) throws SQLException {
        String sql = "SELECT m.name,SUM(oi.quantity) units,SUM(oi.quantity*oi.unit_price) gross FROM order_items oi JOIN orders o ON o.id=oi.order_id JOIN medicines m ON m.id=oi.medicine_id WHERE o.status NOT IN ('CANCELLED','RETURNED') AND o.created_at>=? AND o.created_at<? GROUP BY m.id,m.name ORDER BY units DESC,m.name LIMIT 5";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            setRange(p, from, until);
            try (ResultSet r = p.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (r.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", r.getString("name")); item.put("units", r.getLong("units")); item.put("gross", r.getBigDecimal("gross")); rows.add(item);
                }
                return rows;
            }
        }
    }

    private long countRange(String sql, LocalDateTime from, LocalDateTime until) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) { setRange(p, from, until); try (ResultSet r = p.executeQuery()) { r.next(); return r.getLong(1); } }
    }

    private BigDecimal sumRange(String sql, LocalDateTime from, LocalDateTime until) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) { setRange(p, from, until); try (ResultSet r = p.executeQuery()) { r.next(); return r.getBigDecimal(1); } }
    }

    private int count(String sql) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) { r.next(); return r.getInt(1); }
    }

    private void setRange(PreparedStatement p, LocalDateTime from, LocalDateTime until) throws SQLException {
        p.setTimestamp(1, Timestamp.valueOf(from)); p.setTimestamp(2, Timestamp.valueOf(until));
    }
}
