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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/doctor/availability")
public class AvailabilityServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User doctor = (User) request.getSession().getAttribute("user");
        String sql = "SELECT a.id,a.available_date,a.start_time,a.end_time,EXISTS(SELECT 1 FROM appointments b WHERE b.doctor_id=a.doctor_id AND b.appointment_date=a.available_date AND b.start_time=a.start_time AND b.status NOT IN ('CANCELLED','REJECTED')) booked FROM doctor_availability a WHERE a.doctor_id=? AND (a.available_date>CURRENT_DATE OR (a.available_date=CURRENT_DATE AND a.start_time>CURRENT_TIME)) ORDER BY a.available_date,a.start_time";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, doctor.id());
            try (ResultSet r = p.executeQuery()) {
                List<Map<String, Object>> slots = new ArrayList<>();
                while (r.next()) {
                    Map<String, Object> slot = new LinkedHashMap<>();
                    slot.put("id", r.getLong("id")); slot.put("date", r.getObject("available_date"));
                    slot.put("start", r.getObject("start_time")); slot.put("end", r.getObject("end_time"));
                    slot.put("booked", r.getBoolean("booked")); slots.add(slot);
                }
                request.setAttribute("availability", slots);
            }
            request.setAttribute("today", LocalDate.now());
            request.setAttribute("earliestDate", LocalDate.now().plusDays(1));
            request.setAttribute("lastDate", LocalDate.now().plusDays(90));
            request.getRequestDispatcher("/WEB-INF/views/availability.jsp").forward(request, response);
        } catch (Exception e) { throw new ServletException("Availability is unavailable.", e); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User doctor = (User) request.getSession().getAttribute("user");
        String action = request.getParameter("action");
        try {
            if ("add".equals(action)) {
                LocalDate date = parseDate(request.getParameter("date"));
                LocalTime start = parseTime(request.getParameter("start")), end = parseTime(request.getParameter("end"));
                validateWindow(date, start, end);
                try (Connection c = Database.getConnection()) {
                    c.setAutoCommit(false);
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO doctor_availability(doctor_id,available_date,start_time,end_time) VALUES(?,?,?,?)")) {
                        p.setLong(1, doctor.id()); p.setObject(2, date); p.setObject(3, start); p.setObject(4, end); p.executeUpdate();
                        audit(c, doctor.id(), "AVAILABILITY ADDED", "Doctor added an appointment availability window."); c.commit();
                    } catch (Exception e) {
                        c.rollback();
                        if (e instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("23")) throw new IllegalArgumentException("That date and start time already exists.");
                        throw e;
                    }
                }
                request.getSession().setAttribute("flashSuccess", "Availability added.");
            } else if ("update".equals(action)) {
                long slotId = Long.parseLong(request.getParameter("slotId"));
                LocalDate date = parseDate(request.getParameter("date"));
                LocalTime start = parseTime(request.getParameter("start")), end = parseTime(request.getParameter("end"));
                validateWindow(date, start, end);
                changeOpenSlot(doctor.id(), slotId, date, start, end, false);
                request.getSession().setAttribute("flashSuccess", "Availability updated.");
            } else if ("delete".equals(action)) {
                changeOpenSlot(doctor.id(), Long.parseLong(request.getParameter("slotId")), null, null, null, true);
                request.getSession().setAttribute("flashSuccess", "Availability removed.");
            } else { response.sendError(HttpServletResponse.SC_BAD_REQUEST); return; }
            response.sendRedirect(request.getContextPath() + "/doctor/availability");
        } catch (IllegalArgumentException e) {
            request.getSession().setAttribute("flashError", e.getMessage()); response.sendRedirect(request.getContextPath() + "/doctor/availability");
        } catch (Exception e) { throw new ServletException("Availability could not be saved.", e); }
    }

    private void changeOpenSlot(long doctorId, long slotId, LocalDate date, LocalTime start, LocalTime end, boolean delete) throws Exception {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                LocalDate existingDate; LocalTime existingStart;
                try (PreparedStatement p = c.prepareStatement("SELECT available_date,start_time FROM doctor_availability WHERE id=? AND doctor_id=? FOR UPDATE")) {
                    p.setLong(1, slotId); p.setLong(2, doctorId);
                    try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Availability window not found on your account."); existingDate = r.getObject(1, LocalDate.class); existingStart = r.getObject(2, LocalTime.class); }
                }
                try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM appointments WHERE doctor_id=? AND appointment_date=? AND start_time=? AND status NOT IN ('CANCELLED','REJECTED')")) {
                    p.setLong(1, doctorId); p.setObject(2, existingDate); p.setObject(3, existingStart);
                    try (ResultSet r = p.executeQuery()) { if (r.next()) throw new IllegalArgumentException("This availability window has an appointment and cannot be edited or removed."); }
                }
                if (delete) {
                    try (PreparedStatement p = c.prepareStatement("DELETE FROM doctor_availability WHERE id=? AND doctor_id=?")) { p.setLong(1, slotId); p.setLong(2, doctorId); p.executeUpdate(); }
                    audit(c, doctorId, "AVAILABILITY REMOVED", "Doctor removed an unused future availability window.");
                } else {
                    try (PreparedStatement p = c.prepareStatement("UPDATE doctor_availability SET available_date=?,start_time=?,end_time=? WHERE id=? AND doctor_id=?")) {
                        p.setObject(1, date); p.setObject(2, start); p.setObject(3, end); p.setLong(4, slotId); p.setLong(5, doctorId); p.executeUpdate();
                    }
                    audit(c, doctorId, "AVAILABILITY UPDATED", "Doctor updated an unused future availability window.");
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("23")) throw new IllegalArgumentException("That date and start time already exists.");
                throw e;
            }
        }
    }

    private LocalDate parseDate(String value) { try { return LocalDate.parse(value); } catch (RuntimeException e) { throw new IllegalArgumentException("Enter a valid date."); } }
    private LocalTime parseTime(String value) { try { return LocalTime.parse(value); } catch (RuntimeException e) { throw new IllegalArgumentException("Enter a valid start and end time."); } }
    private void validateWindow(LocalDate date, LocalTime start, LocalTime end) {
        LocalDate today = LocalDate.now();
        if (!date.isAfter(today) || date.isAfter(today.plusDays(90)) || !end.isAfter(start)) throw new IllegalArgumentException("Choose a future date within 90 days and an end time after the start.");
    }
    private void audit(Connection c, long doctorId, String action, String detail) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,?,?)")) { p.setLong(1, doctorId); p.setString(2, action); p.setString(3, detail); p.executeUpdate(); }
    }
}
