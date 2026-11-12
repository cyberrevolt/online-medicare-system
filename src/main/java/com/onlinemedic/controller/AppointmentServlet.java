package com.onlinemedic.controller;

import com.onlinemedic.dao.AppointmentDao;
import com.onlinemedic.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;

@WebServlet("/appointments")
public class AppointmentServlet extends HttpServlet {
    private final AppointmentDao dao = new AppointmentDao();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendRedirect(request.getContextPath() + "/login?next=%2Fappointments"); return; }
        if (!"PATIENT".equals(user.role())) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
        try {
            request.setAttribute("appointments", dao.forPatient(user.id()));
            request.getRequestDispatcher("/WEB-INF/views/appointments.jsp").forward(request, response);
        } catch (Exception e) { throw new ServletException("Appointments are unavailable.", e); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendRedirect(request.getContextPath() + "/login?next=%2Fappointments"); return; }
        if (!"PATIENT".equals(user.role())) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }

        if ("cancel".equals(request.getParameter("action"))) {
            try {
                dao.cancel(Long.parseLong(request.getParameter("appointmentId")), user.id());
                request.getSession().setAttribute("flashSuccess", "Appointment cancelled. The demo refund was recorded; no real bank payment was made.");
                response.sendRedirect(request.getContextPath() + "/appointments");
            } catch (IllegalArgumentException e) {
                request.getSession().setAttribute("flashError", e.getMessage());
                response.sendRedirect(request.getContextPath() + "/appointments");
            } catch (Exception e) { throw new ServletException("Appointment could not be cancelled.", e); }
            return;
        }

        String doctorId = request.getParameter("doctorId");
        try {
            long doctor = Long.parseLong(doctorId);
            String slotValue = request.getParameter("slot");
            if (slotValue == null || !slotValue.contains("|")) throw new IllegalArgumentException("Choose an available appointment time.");
            String[] slot = slotValue.split("\\|", 2);
            LocalDate date = LocalDate.parse(slot[0]);
            LocalTime time = LocalTime.parse(slot[1]);
            String type = request.getParameter("type");
            if (!"In person".equals(type) && !"Secure chat".equals(type) && !"Video consultation".equals(type)) {
                throw new IllegalArgumentException("Choose a valid consultation type.");
            }
            long appointmentId = dao.book(user.id(), doctor, date, time, type);
            response.sendRedirect(request.getContextPath() + "/payment?appointment=" + appointmentId);
        } catch (IllegalArgumentException e) {
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/doctors?id=" + URLEncoder.encode(doctorId == null ? "" : doctorId, StandardCharsets.UTF_8));
        } catch (Exception e) { throw new ServletException("Appointment could not be booked.", e); }
    }
}
