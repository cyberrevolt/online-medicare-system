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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@WebServlet("/consultation")
public class ConsultationServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendRedirect(request.getContextPath() + "/login"); return; }
        if (!isCareRole(user)) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }

        try (Connection connection = Database.getConnection()) {
            long id = Long.parseLong(request.getParameter("id"));
            String sql = "SELECT a.id,a.patient_id,a.doctor_id,a.appointment_date,a.start_time,a.status,a.consultation_type,a.fee,p.full_name patient_name,d.full_name doctor_name,CASE WHEN COALESCE(dp.about,'') LIKE 'DEMO_ONLY:%' THEN TRUE ELSE FALSE END demo_profile FROM appointments a JOIN users p ON p.id=a.patient_id JOIN users d ON d.id=a.doctor_id JOIN doctor_profiles dp ON dp.user_id=a.doctor_id WHERE a.id=? AND ((?='PATIENT' AND a.patient_id=?) OR (?='DOCTOR' AND a.doctor_id=?))";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id); statement.setString(2, user.role()); statement.setLong(3, user.id()); statement.setString(4, user.role()); statement.setLong(5, user.id());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                    String status = result.getString("status");
                    if ("PATIENT".equals(user.role()) && "COMPLETED".equals(status)) {
                        request.getSession().setAttribute("flashError", "This appointment is complete. Book a new appointment to start another consultation.");
                        response.sendRedirect(request.getContextPath() + "/appointments");
                        return;
                    }
                    if (!"CONFIRMED".equals(status) && !"COMPLETED".equals(status)) {
                        response.sendError(HttpServletResponse.SC_CONFLICT, "The private appointment chat opens after the booking is confirmed.");
                        return;
                    }
                    request.setAttribute("appointmentId", id);
                    request.setAttribute("patientName", result.getString("patient_name"));
                    request.setAttribute("doctorName", result.getString("doctor_name"));
                    request.setAttribute("appointmentDate", result.getObject("appointment_date"));
                    request.setAttribute("appointmentTime", result.getObject("start_time"));
                    request.setAttribute("appointmentStatus", status);
                    request.setAttribute("consultationType", result.getString("consultation_type"));
                    request.setAttribute("fee", result.getBigDecimal("fee"));
                    request.setAttribute("demoProfile", result.getBoolean("demo_profile"));
                }
            }
            request.setAttribute("prescription", findPrescription(connection, id));
            request.getRequestDispatcher("/WEB-INF/views/consultation.jsp").forward(request, response);
        } catch (NumberFormatException e) { response.sendError(HttpServletResponse.SC_BAD_REQUEST); }
        catch (Exception e) { throw new ServletException("Consultation room is unavailable.", e); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendRedirect(request.getContextPath() + "/login/doctor"); return; }
        if (!"DOCTOR".equals(user.role())) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }

        try (Connection connection = Database.getConnection()) {
            long appointmentId = Long.parseLong(request.getParameter("appointmentId"));
            String notes = request.getParameter("clinicalNotes"), medicine = request.getParameter("medicine"), dosage = request.getParameter("dosage"), frequency = request.getParameter("frequency"), duration = request.getParameter("duration"), instructions = request.getParameter("instructions");
            connection.setAutoCommit(false);
            long patientId;
            String status;
            boolean demoProfile;
            try (PreparedStatement statement = connection.prepareStatement("SELECT a.patient_id,a.status,CASE WHEN COALESCE(d.about,'') LIKE 'DEMO_ONLY:%' THEN TRUE ELSE FALSE END demo_profile FROM appointments a JOIN doctor_profiles d ON d.user_id=a.doctor_id WHERE a.id=? AND a.doctor_id=?")) {
                statement.setLong(1, appointmentId); statement.setLong(2, user.id());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SecurityException("Not authorized.");
                    patientId = result.getLong("patient_id"); status = result.getString("status"); demoProfile = result.getBoolean("demo_profile");
                }
            }
            if (demoProfile) throw new IllegalArgumentException("Fictional demo profiles cannot issue prescriptions.");
            if (!"CONFIRMED".equals(status) && !"COMPLETED".equals(status)) throw new IllegalArgumentException("Only an active confirmed consultation can receive a prescription.");
            if (notes == null || notes.isBlank() || notes.length() > 2000 || medicine == null || medicine.isBlank() || medicine.length() > 180 || dosage == null || dosage.isBlank() || frequency == null || frequency.isBlank() || duration == null || duration.isBlank()) {
                throw new IllegalArgumentException("Complete the notes and all required medicine directions.");
            }

            long prescriptionId;
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO prescriptions(appointment_id,patient_id,doctor_id,clinical_notes,status) VALUES(?,?,?,?,'ISSUED')", Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, appointmentId); statement.setLong(2, patientId); statement.setLong(3, user.id()); statement.setString(4, notes.trim()); statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) { keys.next(); prescriptionId = keys.getLong(1); }
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE consultations SET status='IN_PROGRESS',started_at=COALESCE(started_at,CURRENT_TIMESTAMP) WHERE appointment_id=?")) { statement.setLong(1, appointmentId); statement.executeUpdate(); }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO consultation_notes(consultation_id,author_id,note) SELECT id,?,? FROM consultations WHERE appointment_id=?")) { statement.setLong(1, user.id()); statement.setString(2, notes.trim()); statement.setLong(3, appointmentId); statement.executeUpdate(); }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO prescription_items(prescription_id,medicine_name,dosage,frequency,duration,instructions) VALUES(?,?,?,?,?,?)")) {
                statement.setLong(1, prescriptionId); statement.setString(2, medicine.trim()); statement.setString(3, dosage.trim()); statement.setString(4, frequency.trim()); statement.setString(5, duration.trim()); statement.setString(6, instructions == null ? "" : instructions.trim()); statement.executeUpdate();
            }
            notify(connection, patientId, "Prescription issued", "A prescription is ready in your account.");
            connection.commit(); request.getSession().setAttribute("flashSuccess", "Prescription issued to the patient.");
            response.sendRedirect(request.getContextPath() + "/consultation?id=" + appointmentId);
        } catch (SecurityException e) { response.sendError(HttpServletResponse.SC_FORBIDDEN); }
        catch (IllegalArgumentException e) { request.getSession().setAttribute("flashError", e.getMessage()); response.sendRedirect(request.getContextPath() + "/consultation?id=" + request.getParameter("appointmentId")); }
        catch (Exception e) { throw new ServletException("Prescription could not be saved.", e); }
    }

    private boolean isCareRole(User user) { return "PATIENT".equals(user.role()) || "DOCTOR".equals(user.role()); }

    private Object findPrescription(Connection connection, long appointmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id,clinical_notes,status,created_at FROM prescriptions WHERE appointment_id=?")) {
            statement.setLong(1, appointmentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                java.util.Map<String,Object> item = new java.util.HashMap<>();
                item.put("id", result.getLong(1)); item.put("notes", result.getString(2)); item.put("status", result.getString(3)); item.put("createdAt", result.getTimestamp(4));
                try (PreparedStatement lines = connection.prepareStatement("SELECT medicine_name,dosage,frequency,duration,instructions FROM prescription_items WHERE prescription_id=?")) {
                    lines.setLong(1, result.getLong(1));
                    try (ResultSet line = lines.executeQuery()) { if (line.next()) { item.put("medicine", line.getString(1)); item.put("dosage", line.getString(2)); item.put("frequency", line.getString(3)); item.put("duration", line.getString(4)); item.put("instructions", line.getString(5)); } }
                }
                return item;
            }
        }
    }

    private void notify(Connection connection, long userId, String title, String body) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO notifications(user_id,title,body) VALUES(?,?,?)")) { statement.setLong(1, userId); statement.setString(2, title); statement.setString(3, body); statement.executeUpdate(); }
    }
}
