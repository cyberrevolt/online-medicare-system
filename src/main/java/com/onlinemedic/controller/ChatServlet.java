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
import java.time.format.DateTimeFormatter;

@WebServlet("/chat")
public class ChatServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED); return; }
        if (!isCareRole(user)) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
        try (Connection connection = Database.getConnection()) {
            long appointmentId = Long.parseLong(request.getParameter("appointment"));
            long conversationId = conversation(connection, appointmentId, user.id());
            try (PreparedStatement read = connection.prepareStatement("UPDATE messages SET read_at=CURRENT_TIMESTAMP WHERE conversation_id=? AND sender_id<>? AND read_at IS NULL")) { read.setLong(1, conversationId); read.setLong(2, user.id()); read.executeUpdate(); }
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            try (PreparedStatement statement = connection.prepareStatement("SELECT u.full_name,m.message,m.sent_at FROM messages m JOIN users u ON u.id=m.sender_id WHERE m.conversation_id=? ORDER BY m.id")) {
                statement.setLong(1, conversationId);
                try (ResultSet result = statement.executeQuery()) {
                    StringBuilder json = new StringBuilder("["); boolean first = true;
                    while (result.next()) {
                        if (!first) json.append(','); first = false;
                        json.append("{\"sender\":\"").append(escape(result.getString(1))).append("\",\"message\":\"").append(escape(result.getString(2))).append("\",\"sentAt\":\"").append(escape(result.getTimestamp(3).toLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMM, HH:mm")))).append("\"}");
                    }
                    json.append(']'); response.getWriter().write(json.toString());
                }
            }
        } catch (SecurityException e) { response.sendError(HttpServletResponse.SC_FORBIDDEN); }
        catch (NumberFormatException e) { response.sendError(HttpServletResponse.SC_BAD_REQUEST); }
        catch (Exception e) { throw new ServletException("Messages are unavailable.", e); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED); return; }
        if (!isCareRole(user)) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
        String message = request.getParameter("message");
        try (Connection connection = Database.getConnection()) {
            if (message == null || message.isBlank() || message.trim().length() > 4000) throw new IllegalArgumentException("Enter a message up to 4,000 characters.");
            long appointmentId = Long.parseLong(request.getParameter("appointment"));
            long conversationId = conversation(connection, appointmentId, user.id());
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO messages(conversation_id,sender_id,message) VALUES(?,?,?)")) { statement.setLong(1, conversationId); statement.setLong(2, user.id()); statement.setString(3, message.trim()); statement.executeUpdate(); }
            response.setContentType("application/json;charset=UTF-8"); response.setHeader("Cache-Control", "no-store"); response.getWriter().write("{\"sent\":true}");
        } catch (SecurityException e) { response.sendError(HttpServletResponse.SC_FORBIDDEN); }
        catch (IllegalArgumentException e) { response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage()); }
        catch (Exception e) { throw new ServletException("Message could not be sent.", e); }
    }

    private long conversation(Connection connection, long appointmentId, long userId) throws Exception {
        String sql = "SELECT c.id FROM conversations c JOIN appointments a ON a.id=c.appointment_id JOIN users u ON u.id=? AND u.active=TRUE WHERE c.appointment_id=? AND ((u.role='PATIENT' AND a.patient_id=u.id) OR (u.role='DOCTOR' AND a.doctor_id=u.id)) AND (a.status='CONFIRMED' OR (a.status='COMPLETED' AND u.role='DOCTOR'))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId); statement.setLong(2, appointmentId);
            try (ResultSet result = statement.executeQuery()) { if (!result.next()) throw new SecurityException("No authorized appointment chat was found."); return result.getLong(1); }
        }
    }

    private boolean isCareRole(User user) { return "PATIENT".equals(user.role()) || "DOCTOR".equals(user.role()); }

    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); }
}
