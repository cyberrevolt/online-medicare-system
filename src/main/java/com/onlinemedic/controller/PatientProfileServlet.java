package com.onlinemedic.controller;

import com.onlinemedic.dao.AddressDao;
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
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Set;

@WebServlet("/patient/profile")
public class PatientProfileServlet extends HttpServlet {
    private static final Set<String> STATES = Set.of("Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal", "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry");
    private final AddressDao addresses = new AddressDao();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT u.full_name,u.email,u.phone,p.date_of_birth,p.gender,p.emergency_contact FROM users u LEFT JOIN patient_profiles p ON p.user_id=u.id WHERE u.id=? AND u.role='PATIENT'")) {
            statement.setLong(1, user.id());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
                request.setAttribute("profileName", result.getString("full_name"));
                request.setAttribute("profileEmail", result.getString("email"));
                request.setAttribute("profilePhone", result.getString("phone"));
                java.sql.Date dob = result.getDate("date_of_birth");
                request.setAttribute("profileDateOfBirth", dob == null ? "" : dob.toLocalDate());
                request.setAttribute("profileGender", result.getString("gender"));
                request.setAttribute("emergencyContact", result.getString("emergency_contact"));
            }
            request.setAttribute("addresses", addresses.listForUser(user.id()));
            request.setAttribute("states", STATES.stream().sorted().toList());
            request.setAttribute("today", LocalDate.now());
            request.getRequestDispatcher("/WEB-INF/views/patient-profile.jsp").forward(request, response);
        } catch (Exception e) {
            throw new ServletException("Your profile could not be loaded.", e);
        }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        String path = request.getContextPath() + "/patient/profile";
        try {
            String action = request.getParameter("action");
            if ("saveProfile".equals(action)) saveProfile(request, user);
            else if ("saveAddress".equals(action)) saveAddress(request, user);
            else if ("makeDefault".equals(action)) makeDefault(request, user);
            else if ("deleteAddress".equals(action)) deleteAddress(request, user);
            else { response.sendError(HttpServletResponse.SC_BAD_REQUEST); return; }
            response.sendRedirect(path);
        } catch (IllegalArgumentException e) {
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(path);
        } catch (Exception e) {
            throw new ServletException("Your profile change could not be saved.", e);
        }
    }

    private void saveProfile(HttpServletRequest request, User user) throws Exception {
        String name = required(request.getParameter("name"), 2, 120, "Enter your name (2–120 characters).");
        String phone = request.getParameter("phone"); phone = phone == null ? "" : phone.trim();
        if (phone.length() > 30 || (!phone.isBlank() && phone.replaceAll("\\D", "").length() < 7)) throw new IllegalArgumentException("Enter a valid phone number or leave it blank.");
        String dobValue = request.getParameter("dateOfBirth"); LocalDate dob = parseDate(dobValue, "Enter a valid date of birth.");
        if (dob != null && (dob.isAfter(LocalDate.now()) || dob.isBefore(LocalDate.now().minusYears(120)))) throw new IllegalArgumentException("Enter a valid date of birth.");
        String gender = request.getParameter("gender");
        if (gender != null && !gender.isBlank() && !Set.of("Female", "Male", "Non-binary", "Prefer not to say").contains(gender)) throw new IllegalArgumentException("Choose a listed gender option.");
        String emergency = request.getParameter("emergencyContact"); emergency = emergency == null ? "" : emergency.trim();
        if (emergency.length() > 100) throw new IllegalArgumentException("Emergency contact details must be 100 characters or fewer.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("UPDATE users SET full_name=?,phone=? WHERE id=? AND role='PATIENT'")) { p.setString(1, name); p.setString(2, phone.isBlank() ? null : phone); p.setLong(3, user.id()); if (p.executeUpdate() != 1) throw new IllegalArgumentException("Patient account not found."); }
                int updated;
                try (PreparedStatement p = c.prepareStatement("UPDATE patient_profiles SET date_of_birth=?,gender=?,emergency_contact=? WHERE user_id=?")) { if (dob == null) p.setNull(1, java.sql.Types.DATE); else p.setObject(1, dob); p.setString(2, gender == null || gender.isBlank() ? null : gender); p.setString(3, emergency.isBlank() ? null : emergency); p.setLong(4, user.id()); updated = p.executeUpdate(); }
                if (updated == 0) try (PreparedStatement p = c.prepareStatement("INSERT INTO patient_profiles(user_id,date_of_birth,gender,emergency_contact) VALUES(?,?,?,?)")) { p.setLong(1, user.id()); if (dob == null) p.setNull(2, java.sql.Types.DATE); else p.setObject(2, dob); p.setString(3, gender == null || gender.isBlank() ? null : gender); p.setString(4, emergency.isBlank() ? null : emergency); p.executeUpdate(); }
                audit(c, user.id(), "PATIENT PROFILE UPDATED", "Patient profile details updated.");
                c.commit();
                request.getSession().setAttribute("user", new User(user.id(), name, user.email(), phone.isBlank() ? null : phone, user.role()));
                request.getSession().setAttribute("flashSuccess", "Profile details saved.");
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    private void saveAddress(HttpServletRequest request, User user) throws Exception {
        String label = request.getParameter("label"); label = label == null || label.isBlank() ? "Home" : required(label, 2, 60, "Address label must be 2–60 characters.");
        String line1 = required(request.getParameter("line1"), 5, 220, "Enter a street address (5–220 characters).");
        String city = required(request.getParameter("city"), 2, 100, "Enter the city or post office.");
        String district = required(request.getParameter("district"), 2, 100, "Enter the district.");
        String state = required(request.getParameter("state"), 2, 100, "Choose a state.");
        if (!STATES.contains(state)) throw new IllegalArgumentException("Choose a valid Indian state or union territory.");
        String pin = request.getParameter("postalCode"); if (pin == null || !pin.trim().matches("[0-9]{6}")) throw new IllegalArgumentException("Enter a valid six-digit PIN code."); pin = pin.trim();
        String idText = request.getParameter("addressId"); long id = idText == null || idText.isBlank() ? 0 : Long.parseLong(idText);
        boolean requestedDefault = "true".equals(request.getParameter("isDefault"));
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (id > 0) try (PreparedStatement p = c.prepareStatement("SELECT id FROM addresses WHERE id=? AND user_id=? FOR UPDATE")) { p.setLong(1, id); p.setLong(2, user.id()); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Address not found on your account."); } }
                if (id > 0) try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM orders WHERE address_id=?")) { p.setLong(1, id); try (ResultSet r = p.executeQuery()) { r.next(); if (r.getInt(1) > 0) throw new IllegalArgumentException("This address is attached to an order and is kept unchanged for its history. Add a new address for future deliveries."); } }
                boolean makeDefault = requestedDefault || id == 0 && addressCount(c, user.id()) == 0;
                if (makeDefault) clearDefault(c, user.id());
                if (id == 0) {
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO addresses(user_id,label,line1,city,district,state,postal_code,is_default) VALUES(?,?,?,?,?,?,?,?)")) { p.setLong(1, user.id()); p.setString(2, label); p.setString(3, line1); p.setString(4, city); p.setString(5, district); p.setString(6, state); p.setString(7, pin); p.setBoolean(8, makeDefault); p.executeUpdate(); }
                } else {
                    try (PreparedStatement p = c.prepareStatement("UPDATE addresses SET label=?,line1=?,city=?,district=?,state=?,postal_code=?,is_default=? WHERE id=? AND user_id=?")) { p.setString(1, label); p.setString(2, line1); p.setString(3, city); p.setString(4, district); p.setString(5, state); p.setString(6, pin); p.setBoolean(7, makeDefault); p.setLong(8, id); p.setLong(9, user.id()); p.executeUpdate(); }
                }
                audit(c, user.id(), "PATIENT ADDRESS SAVED", "Delivery address saved on patient account."); c.commit();
                request.getSession().setAttribute("flashSuccess", "Delivery address saved.");
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    private void makeDefault(HttpServletRequest request, User user) throws Exception {
        long id = Long.parseLong(request.getParameter("addressId"));
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                clearDefault(c, user.id());
                try (PreparedStatement p = c.prepareStatement("UPDATE addresses SET is_default=TRUE WHERE id=? AND user_id=?")) { p.setLong(1, id); p.setLong(2, user.id()); if (p.executeUpdate() != 1) throw new IllegalArgumentException("Address not found on your account."); }
                audit(c, user.id(), "DEFAULT ADDRESS CHANGED", "Patient selected a default delivery address."); c.commit();
                request.getSession().setAttribute("flashSuccess", "Default delivery address updated.");
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    private void deleteAddress(HttpServletRequest request, User user) throws Exception {
        long id = Long.parseLong(request.getParameter("addressId"));
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                boolean wasDefault;
                try (PreparedStatement p = c.prepareStatement("SELECT is_default FROM addresses WHERE id=? AND user_id=? FOR UPDATE")) { p.setLong(1, id); p.setLong(2, user.id()); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalArgumentException("Address not found on your account."); wasDefault = r.getBoolean(1); } }
                try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM orders WHERE address_id=?")) { p.setLong(1, id); try (ResultSet r = p.executeQuery()) { r.next(); if (r.getInt(1) > 0) throw new IllegalArgumentException("This address is attached to past orders, so it cannot be deleted."); } }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM addresses WHERE id=? AND user_id=?")) { p.setLong(1, id); p.setLong(2, user.id()); p.executeUpdate(); }
                if (wasDefault && addressCount(c, user.id()) > 0) try (PreparedStatement p = c.prepareStatement("UPDATE addresses SET is_default=TRUE WHERE id=(SELECT MAX(id) FROM addresses WHERE user_id=?)")) { p.setLong(1, user.id()); p.executeUpdate(); }
                audit(c, user.id(), "PATIENT ADDRESS DELETED", "Patient removed an unused saved delivery address."); c.commit();
                request.getSession().setAttribute("flashSuccess", "Address removed.");
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    private int addressCount(Connection c, long userId) throws Exception { try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM addresses WHERE user_id=?")) { p.setLong(1, userId); try (ResultSet r = p.executeQuery()) { r.next(); return r.getInt(1); } } }
    private void clearDefault(Connection c, long userId) throws Exception { try (PreparedStatement p = c.prepareStatement("UPDATE addresses SET is_default=FALSE WHERE user_id=?")) { p.setLong(1, userId); p.executeUpdate(); } }
    private void audit(Connection c, long userId, String action, String detail) throws Exception { try (PreparedStatement p = c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,?,?)")) { p.setLong(1, userId); p.setString(2, action); p.setString(3, detail); p.executeUpdate(); } }
    private LocalDate parseDate(String value, String message) { if (value == null || value.isBlank()) return null; try { return LocalDate.parse(value); } catch (java.time.format.DateTimeParseException e) { throw new IllegalArgumentException(message); } }
    private String required(String value, int min, int max, String message) { if (value == null || value.trim().length() < min || value.trim().length() > max) throw new IllegalArgumentException(message); return value.trim(); }
}
