package com.onlinemedic.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@WebServlet("/pin-lookup")
public class PinLookupServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pin = request.getParameter("pin");
        if (pin == null || !pin.matches("[0-9]{6}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Enter a six-digit PIN code.");
            return;
        }
        try {
            HttpRequest lookup = HttpRequest.newBuilder(URI.create("https://api.postalpincode.in/pincode/" + pin))
                    .timeout(Duration.ofSeconds(6)).header("Accept", "application/json").GET().build();
            HttpResponse<String> result = client.send(lookup, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (result.statusCode() != 200 || result.body().length() > 1_000_000) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "PIN lookup is unavailable.");
                return;
            }
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "public, max-age=3600");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.getWriter().write(result.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "PIN lookup is unavailable.");
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "PIN lookup is unavailable.");
        }
    }
}
