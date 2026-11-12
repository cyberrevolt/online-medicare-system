package com.onlinemedic.controller;

import com.onlinemedic.model.User;
import com.onlinemedic.util.Database;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;

@WebServlet("/prescriptions")
public class PrescriptionsServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login/patient?next=%2Fprescriptions");
            return;
        }
        if (!Set.of("PATIENT", "PHARMACIST", "ADMIN").contains(user.role())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String sql = "SELECT p.id,p.patient_id,p.doctor_id,p.clinical_notes,p.status,p.rejection_reason,p.created_at,pu.full_name patient_name,du.full_name doctor_name,pi.medicine_name,pi.dosage,pi.frequency,pi.duration,pi.instructions FROM prescriptions p JOIN users pu ON pu.id=p.patient_id JOIN users du ON du.id=p.doctor_id LEFT JOIN prescription_items pi ON pi.prescription_id=p.id "
                + ("PATIENT".equals(user.role()) ? "WHERE p.patient_id=? " : "") + "ORDER BY p.created_at DESC";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            if ("PATIENT".equals(user.role())) statement.setLong(1, user.id());
            List<Map<String, Object>> prescriptions = new ArrayList<>();
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", result.getLong("id"));
                    item.put("patientId", result.getLong("patient_id"));
                    item.put("doctor", result.getString("doctor_name"));
                    item.put("patient", result.getString("patient_name"));
                    item.put("notes", result.getString("clinical_notes"));
                    item.put("status", result.getString("status"));
                    item.put("reason", result.getString("rejection_reason"));
                    item.put("createdAt", result.getTimestamp("created_at"));
                    item.put("medicine", result.getString("medicine_name"));
                    item.put("dosage", result.getString("dosage"));
                    item.put("frequency", result.getString("frequency"));
                    item.put("duration", result.getString("duration"));
                    item.put("instructions", result.getString("instructions"));
                    prescriptions.add(item);
                }
            }
            request.setAttribute("prescriptions", prescriptions);
            request.setAttribute("reviewMode", !"PATIENT".equals(user.role()));
            request.getRequestDispatcher("/WEB-INF/views/prescriptions.jsp").forward(request, response);
        } catch (Exception e) {
            throw new ServletException("Prescription records are unavailable.", e);
        }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sign in to review prescriptions.");
            return;
        }
        if (!Set.of("PHARMACIST", "ADMIN").contains(user.role())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String status = request.getParameter("status"), reason = request.getParameter("reason");
        if (status == null || !Set.of("VERIFIED", "REJECTED").contains(status)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        reason = reason == null ? "" : reason.trim();
        if (reason.length() > 500) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Review notes must be 500 characters or fewer.");
            return;
        }
        if ("REJECTED".equals(status) && reason.isBlank()) reason = "Needs review";
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            long prescriptionId = Long.parseLong(request.getParameter("prescriptionId"));
            long patientId; String currentStatus;
            try (PreparedStatement statement = connection.prepareStatement("SELECT patient_id,status FROM prescriptions WHERE id=? FOR UPDATE")) {
                statement.setLong(1, prescriptionId);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) { connection.rollback(); response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                    patientId = result.getLong("patient_id"); currentStatus = result.getString("status");
                }
            }
            if (!"ISSUED".equals(currentStatus)) {
                connection.rollback();
                request.getSession().setAttribute("flashError", "This prescription was already reviewed and cannot be changed again.");
                response.sendRedirect(request.getContextPath() + "/prescriptions");
                return;
            }
            String reviewReason = "REJECTED".equals(status) ? reason : null;
            try (PreparedStatement statement = connection.prepareStatement("UPDATE prescriptions SET status=?,rejection_reason=? WHERE id=? AND status='ISSUED'")) {
                statement.setString(1, status);
                statement.setString(2, reviewReason);
                statement.setLong(3, prescriptionId);
                if (statement.executeUpdate() != 1) throw new IllegalArgumentException("This prescription has already been reviewed.");
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO notifications(user_id,title,body) VALUES(?,?,?)")) {
                statement.setLong(1, patientId);
                statement.setString(2, "Prescription review");
                statement.setString(3, "Your prescription was " + status.toLowerCase(Locale.ROOT) + ("REJECTED".equals(status) ? ": " + reviewReason : "."));
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,?,?)")) {
                statement.setLong(1, user.id()); statement.setString(2, "PRESCRIPTION " + status);
                statement.setString(3, "Prescription #" + prescriptionId + (reviewReason == null ? " verified." : " rejected: " + reviewReason)); statement.executeUpdate();
            }
            connection.commit();
            request.getSession().setAttribute("flashSuccess", "Prescription review saved.");
            response.sendRedirect(request.getContextPath() + "/prescriptions");
        } catch (IllegalArgumentException e) {
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/prescriptions");
        } catch (Exception e) {
            throw new ServletException("Prescription review could not be saved.", e);
        }
    }
}
