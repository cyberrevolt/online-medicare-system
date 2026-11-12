package com.onlinemedic.controller;

import com.onlinemedic.dao.OrderDao;
import com.onlinemedic.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;

@WebServlet(urlPatterns = {"/pharmacist/returns", "/delivery/return-pickups"})
public class ReturnManagementServlet extends HttpServlet {
    private final OrderDao orders = new OrderDao();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        String path = request.getServletPath();
        try {
            if ("/pharmacist/returns".equals(path)) {
                if (!"PHARMACIST".equals(user.role())) { response.sendError(403); return; }
                request.setAttribute("returnQueue", orders.returnQueue());
                request.setAttribute("deliveryStaff", orders.deliveryStaffOptions());
                request.getRequestDispatcher("/WEB-INF/views/return-queue.jsp").forward(request, response);
            } else {
                if (!"DELIVERY".equals(user.role())) { response.sendError(403); return; }
                request.setAttribute("returnPickups", orders.returnPickupQueue(user.id()));
                request.getRequestDispatcher("/WEB-INF/views/return-pickups.jsp").forward(request, response);
            }
        } catch (Exception e) { throw new ServletException("Return workspace is unavailable.", e); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        String path = request.getServletPath();
        String action = request.getParameter("action");
        try {
            long orderId = Long.parseLong(request.getParameter("orderId"));
            if ("/delivery/return-pickups".equals(path)) {
                if (!"DELIVERY".equals(user.role())) { response.sendError(403); return; }
                if (!"markPickedUp".equals(action)) { response.sendError(400); return; }
                orders.markReturnPickedUp(orderId, user.id());
                request.getSession().setAttribute("flashSuccess", "Return pickup recorded. The pharmacy and patient have been notified.");
            } else {
                if (!"PHARMACIST".equals(user.role())) { response.sendError(403); return; }
                switch (action == null ? "" : action) {
                    case "requestImage" -> {
                        String old = orders.requestReturnPhoto(orderId, user.id(), request.getParameter("note"));
                        new com.onlinemedic.util.ReturnPhotoStorage().delete(old);
                        request.getSession().setAttribute("flashSuccess", "The patient has been asked to upload a product photo.");
                    }
                    case "reject" -> {
                        orders.rejectReturn(orderId, user.id(), request.getParameter("note"));
                        request.getSession().setAttribute("flashSuccess", "Return request declined and the patient was notified.");
                    }
                    case "approveSchedule" -> {
                        long staffId = Long.parseLong(request.getParameter("deliveryStaffId"));
                        LocalDateTime pickupAt = LocalDateTime.parse(request.getParameter("pickupAt"));
                        orders.approveReturnAndSchedulePickup(orderId, staffId, pickupAt, user.id(), request.getParameter("note"));
                        request.getSession().setAttribute("flashSuccess", "Return approved. Pickup scheduled and assigned to delivery staff.");
                    }
                    case "markReceived" -> {
                        orders.markReturnReceived(orderId, user.id(), request.getParameter("note"));
                        request.getSession().setAttribute("flashSuccess", "Return receipt confirmed. The patient has been notified.");
                    }
                    case "initiateRefund" -> {
                        orders.initiateRefundByPharmacist(orderId, user.id(), request.getParameter("reason"));
                        request.getSession().setAttribute("flashSuccess", "Full demo refund initiated and sent to the administrator for review.");
                    }
                    default -> { response.sendError(400); return; }
                }
            }
            response.sendRedirect(request.getContextPath() + path);
        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
            request.getSession().setAttribute("flashError", "Choose a valid order, staff member, and pickup date and time.");
            response.sendRedirect(request.getContextPath() + path);
        } catch (SecurityException e) { response.sendError(403); }
        catch (IllegalArgumentException e) {
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + path);
        } catch (Exception e) { throw new ServletException("The return update could not be saved.", e); }
    }
}
