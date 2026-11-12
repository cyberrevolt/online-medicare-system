package com.onlinemedic.controller;

import com.onlinemedic.dao.AppointmentDao;
import com.onlinemedic.model.User;
import com.onlinemedic.service.PaymentService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/payment")
public class PaymentServlet extends HttpServlet {
    private final AppointmentDao appointments = new AppointmentDao();
    private final PaymentService payments = new PaymentService();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendRedirect(request.getContextPath() + "/login?next=%2Fappointments"); return; }
        if (!"PATIENT".equals(user.role())) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
        try {
            long id = Long.parseLong(request.getParameter("appointment"));
            if (!appointments.isPendingPaymentForPatient(id, user.id())) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
            request.setAttribute("appointmentId", id);
            request.getRequestDispatcher("/WEB-INF/views/payment.jsp").forward(request, response);
        } catch (NumberFormatException e) { response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid appointment payment request."); }
        catch (Exception e) { throw new ServletException("Payment page is unavailable.", e); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendRedirect(request.getContextPath() + "/login?next=%2Fappointments"); return; }
        if (!"PATIENT".equals(user.role())) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
        try {
            long id = Long.parseLong(request.getParameter("appointmentId"));
            String method = request.getParameter("method");
            if (!"DEMO_CARD".equals(method)) throw new IllegalArgumentException("Choose the local demo payment option.");
            var result = payments.processDemoPayment(request.getParameter("outcome"));
            appointments.pay(id, user.id(), result.success(), method, result.transactionId());
            request.getSession().setAttribute(result.success() ? "flashSuccess" : "flashError", result.message() + (result.success() ? " Your appointment is confirmed. Open its appointment room to chat or join a video consultation." : " The appointment slot was released."));
            response.sendRedirect(request.getContextPath() + "/appointments");
        } catch (IllegalArgumentException e) {
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/appointments");
        } catch (SecurityException e) { response.sendError(HttpServletResponse.SC_FORBIDDEN); }
        catch (Exception e) { throw new ServletException("Payment could not be recorded.", e); }
    }
}
