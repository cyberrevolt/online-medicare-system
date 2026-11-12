package com.onlinemedic.controller;

import com.onlinemedic.model.User;
import com.onlinemedic.util.Database;
import com.onlinemedic.util.PrescriptionStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet(urlPatterns = {"/pharmacist/order-prescription", "/admin/order-prescription"})
public class OrderPrescriptionFileServlet extends HttpServlet {
    private final PrescriptionStorage storage = new PrescriptionStorage();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        if (user == null || !java.util.Set.of("PHARMACIST", "ADMIN").contains(user.role())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        try {
            long orderId = Long.parseLong(request.getParameter("orderId"));
            String fileName;
            String mediaType;
            try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT file_name,media_type FROM order_prescriptions WHERE order_id=?")) {
                p.setLong(1, orderId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                    fileName = r.getString(1);
                    mediaType = r.getString(2);
                }
            }
            Path file = storage.resolve(fileName);
            if (file == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
            response.setContentType(mediaType);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Content-Disposition", "attachment; filename=\"order-" + orderId + "-prescription" + extension(fileName) + "\"");
            response.setContentLengthLong(Files.size(file));
            Files.copy(file, response.getOutputStream());
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
            throw new ServletException("Prescription document is unavailable.", e);
        }
    }

    private String extension(String fileName) { return fileName.endsWith(".pdf") ? ".pdf" : fileName.endsWith(".png") ? ".png" : ".jpg"; }
}
