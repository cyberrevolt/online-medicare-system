package com.onlinemedic.controller;

import com.onlinemedic.model.User;
import com.onlinemedic.service.VideoCallService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@WebServlet("/video-signal")
public class VideoSignalServlet extends HttpServlet {
    private final VideoCallService calls = new VideoCallService();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = authorizedUser(request, response);
        if (user == null) return;
        try {
            long appointmentId = Long.parseLong(request.getParameter("appointmentId"));
            long afterId = request.getParameter("after") == null ? 0 : Long.parseLong(request.getParameter("after"));
            if (afterId < 0) throw new IllegalArgumentException("Invalid signal cursor.");
            writeJson(response, HttpServletResponse.SC_OK, calls.poll(appointmentId, user.id(), afterId));
        } catch (SecurityException e) { writeError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage()); }
        catch (IllegalArgumentException e) { writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage()); }
        catch (Exception e) { throw new ServletException("Video call status could not be loaded.", e); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        User user = authorizedUser(request, response);
        if (user == null) return;
        try {
            String action = request.getParameter("action");
            long appointmentId = Long.parseLong(request.getParameter("appointmentId"));
            Map<String, Object> result;
            if ("start".equals(action)) result = calls.start(appointmentId, user.id());
            else {
                long callId = Long.parseLong(request.getParameter("callId"));
                if ("accept".equals(action)) result = calls.accept(appointmentId, callId, user.id());
                else if ("signal".equals(action)) { calls.sendSignal(appointmentId, callId, user.id(), request.getParameter("type"), request.getParameter("payload")); result = Map.of("sent", true); }
                else if ("end".equals(action)) { calls.end(appointmentId, callId, user.id()); result = Map.of("ended", true); }
                else throw new IllegalArgumentException("Unknown video call action.");
            }
            writeJson(response, HttpServletResponse.SC_OK, result);
        } catch (SecurityException e) { writeError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage()); }
        catch (IllegalArgumentException e) { writeError(response, HttpServletResponse.SC_CONFLICT, e.getMessage()); }
        catch (Exception e) { throw new ServletException("Video call action could not be completed.", e); }
    }

    private User authorizedUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to join this appointment call."); return null; }
        if (!"PATIENT".equals(user.role()) && !"DOCTOR".equals(user.role())) { writeError(response, HttpServletResponse.SC_FORBIDDEN, "Only the patient and doctor can use appointment video calls."); return null; }
        return user;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        writeJson(response, status, Map.of("error", message == null ? "The video call request was rejected." : message));
    }

    private void writeJson(HttpServletResponse response, int status, Object value) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8"); response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write(json(value));
    }

    private String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{"); boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) { if (!first) out.append(','); first = false; out.append(quote(String.valueOf(entry.getKey()))).append(':').append(json(entry.getValue())); }
            return out.append('}').toString();
        }
        if (value instanceof List<?> list) { StringBuilder out = new StringBuilder("["); for (int i = 0; i < list.size(); i++) { if (i > 0) out.append(','); out.append(json(list.get(i))); } return out.append(']').toString(); }
        return quote(value.toString());
    }

    private String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f"); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch)); else out.append(ch); }
            }
        }
        return out.append('"').toString();
    }
}
