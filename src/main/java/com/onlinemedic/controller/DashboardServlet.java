package com.onlinemedic.controller;

import com.onlinemedic.dao.AppointmentDao;
import com.onlinemedic.dao.OrderDao;
import com.onlinemedic.model.User;
import com.onlinemedic.service.DashboardService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {
    private final DashboardService stats = new DashboardService();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login?next=%2Fdashboard");
            return;
        }

        try {
            request.setAttribute("unread", stats.unread(user.id()));
            switch (user.role()) {
                case "ADMIN" -> request.setAttribute("stats", stats.adminStats());
                case "DOCTOR" -> {
                    request.setAttribute("stats", stats.doctorStats(user.id()));
                    request.setAttribute("appointments", new AppointmentDao().forDoctor(user.id()));
                }
                case "PATIENT" -> {
                    request.setAttribute("stats", stats.patientStats(user.id()));
                    request.setAttribute("appointments", new AppointmentDao().forPatient(user.id()));
                    request.setAttribute("orders", new OrderDao().forPatient(user.id()));
                }
                case "PHARMACIST", "DELIVERY" -> request.setAttribute("stats", stats.adminStats());
                default -> {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }
            request.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(request, response);
        } catch (Exception e) {
            throw new ServletException("Dashboard is unavailable.", e);
        }
    }
}
