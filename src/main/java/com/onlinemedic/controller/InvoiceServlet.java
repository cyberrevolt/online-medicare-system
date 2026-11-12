package com.onlinemedic.controller;

import com.onlinemedic.dao.OrderDao;
import com.onlinemedic.model.OrderSummary;
import com.onlinemedic.model.User;
import com.onlinemedic.util.InvoicePdf;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/orders/invoice")
public class InvoiceServlet extends HttpServlet {
    private final OrderDao orders = new OrderDao();
    private final InvoicePdf pdf = new InvoicePdf();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendRedirect(request.getContextPath() + "/login/patient?next=%2Forders"); return; }
        if (!"PATIENT".equals(user.role())) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
        try {
            long orderId = Long.parseLong(request.getParameter("id"));
            OrderSummary order = orders.findForPatient(orderId, user.id()).orElse(null);
            if (order == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
            byte[] document = pdf.generate(order);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"online-medic-invoice-" + orderId + ".pdf\"");
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLength(document.length);
            response.getOutputStream().write(document);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
            throw new ServletException("The invoice could not be generated.", e);
        }
    }
}
