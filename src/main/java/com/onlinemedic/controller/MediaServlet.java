package com.onlinemedic.controller;

import com.onlinemedic.util.ImageStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@WebServlet("/media/*")
public class MediaServlet extends HttpServlet {
    private final ImageStorage images = new ImageStorage();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        String name = path == null || path.length() < 2 ? null : path.substring(1);
        Path file = images.resolve(name);
        if (file == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
        String type = name.endsWith(".png") ? "image/png" : "image/jpeg";
        response.setContentType(type);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "public, max-age=86400");
        response.setContentLengthLong(Files.size(file));
        Files.copy(file, response.getOutputStream());
    }
}
