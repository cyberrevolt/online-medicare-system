package com.onlinemedic.controller;

import com.onlinemedic.dao.OrderDao;
import com.onlinemedic.model.User;
import com.onlinemedic.util.ReturnPhotoStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/orders")
@MultipartConfig(maxFileSize = ReturnPhotoStorage.MAX_BYTES, maxRequestSize = 6L * 1024 * 1024)
public class PatientOrdersServlet extends HttpServlet {
    private final OrderDao orders = new OrderDao();
    private final ReturnPhotoStorage returnPhotos = new ReturnPhotoStorage();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendRedirect(request.getContextPath() + "/login/patient?next=%2Forders"); return; }
        if (!"PATIENT".equals(user.role())) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
        try {
            request.setAttribute("orders", orders.forPatient(user.id()));
            request.getRequestDispatcher("/WEB-INF/views/orders.jsp").forward(request, response);
        } catch (Exception e) { throw new ServletException("Order history is unavailable.", e); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null) { response.sendRedirect(request.getContextPath() + "/login/patient?next=%2Forders"); return; }
        if (!"PATIENT".equals(user.role())) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
        String action = request.getParameter("action");
        String uploadedPhoto = null;
        try {
            long orderId = Long.parseLong(request.getParameter("orderId"));
            if ("cancel".equals(action)) {
                orders.cancelByPatient(orderId, user.id());
                request.getSession().setAttribute("flashSuccess", "Order cancelled and reserved stock returned to inventory. If it was paid, a full demo refund request is now waiting for administrator review.");
            } else if ("requestRefund".equals(action)) {
                orders.requestRefundByPatient(orderId, user.id(), request.getParameter("reason"));
                request.getSession().setAttribute("flashSuccess", "Your full demo refund request was sent to the administrator for review.");
            } else if ("requestReturn".equals(action)) {
                orders.requestReturnByPatient(orderId, user.id(), request.getParameter("reason"));
                request.getSession().setAttribute("flashSuccess", "Your return request was submitted to the pharmacy.");
            } else if ("uploadReturnImage".equals(action)) {
                uploadedPhoto = returnPhotos.store(request.getPart("returnPhoto"));
                orders.submitReturnImage(orderId, user.id(), uploadedPhoto);
                uploadedPhoto = null;
                request.getSession().setAttribute("flashSuccess", "Your product photo was uploaded for pharmacy review.");
            } else { response.sendError(HttpServletResponse.SC_BAD_REQUEST); return; }
            response.sendRedirect(request.getContextPath() + "/orders");
        } catch (IllegalArgumentException e) {
            returnPhotos.delete(uploadedPhoto);
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/orders");
        } catch (IllegalStateException e) {
            returnPhotos.delete(uploadedPhoto);
            request.getSession().setAttribute("flashError", "The product photo is too large. Choose a JPEG or PNG under 5 MB.");
            response.sendRedirect(request.getContextPath() + "/orders");
        } catch (Exception e) { returnPhotos.delete(uploadedPhoto); throw new ServletException("Order request could not be completed.", e); }
    }
}
