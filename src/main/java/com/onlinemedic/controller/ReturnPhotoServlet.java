package com.onlinemedic.controller;

import com.onlinemedic.dao.OrderDao;
import com.onlinemedic.util.ReturnPhotoStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Streams requested return verification photos only to signed-in pharmacists. */
@WebServlet("/pharmacist/return-photo")
public class ReturnPhotoServlet extends HttpServlet {
    private final OrderDao orders = new OrderDao();
    private final ReturnPhotoStorage photos = new ReturnPhotoStorage();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            long orderId = Long.parseLong(request.getParameter("id"));
            String fileName = orders.returnPhotoFile(orderId);
            Path file = photos.resolve(fileName);
            if (file == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
            String type = fileName.endsWith(".png") ? "image/png" : "image/jpeg";
            response.setContentType(type);
            response.setHeader("Content-Disposition", "inline; filename=\"return-photo." + (type.endsWith("png") ? "png" : "jpg") + "\"");
            response.setHeader("Cache-Control", "private, no-store, max-age=0");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLengthLong(Files.size(file));
            try (var in = Files.newInputStream(file); var out = response.getOutputStream()) { in.transferTo(out); }
        } catch (NumberFormatException e) { response.sendError(HttpServletResponse.SC_BAD_REQUEST); }
        catch (Exception e) { throw new ServletException("Return photo is unavailable.", e); }
    }
}
