package com.onlinemedic.controller;

import com.onlinemedic.model.User;
import com.onlinemedic.service.AdminService;
import com.onlinemedic.util.ImageStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;

@WebServlet("/admin/doctors")
@MultipartConfig(maxFileSize = ImageStorage.MAX_IMAGE_BYTES, maxRequestSize = 6L * 1024 * 1024)
public class AdminDoctorServlet extends HttpServlet {
    private final AdminService service = new AdminService();
    private final ImageStorage images = new ImageStorage();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/views/admin-doctor.jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User admin = (User) request.getSession().getAttribute("user");
        String image = null;
        boolean updating = "update".equals(request.getParameter("action"));
        try {
            image = images.store(request.getPart("profileImage"));
            if (updating) {
                boolean replacingImage = image != null;
                String oldImage = service.updateDoctor(admin.id(), Long.parseLong(request.getParameter("doctorId")),
                        request.getParameter("name"), request.getParameter("email"), request.getParameter("phone"),
                        "true".equals(request.getParameter("active")), request.getParameter("newPassword"),
                        request.getParameter("specialization"), request.getParameter("qualification"), request.getParameter("registration"),
                        Integer.parseInt(request.getParameter("experience")), new BigDecimal(request.getParameter("fee")),
                        request.getParameter("about"), request.getParameter("clinic"), image);
                if (replacingImage) images.delete(oldImage);
                image = null;
                request.getSession().setAttribute("flashSuccess", "Doctor profile updated.");
                response.sendRedirect(request.getContextPath() + "/admin");
                return;
            }
            service.createDoctor(admin.id(), request.getParameter("name"), request.getParameter("email"), request.getParameter("phone"),
                    request.getParameter("password"), request.getParameter("specialization"), request.getParameter("qualification"),
                    request.getParameter("registration"), Integer.parseInt(request.getParameter("experience")),
                    new BigDecimal(request.getParameter("fee")), request.getParameter("about"), request.getParameter("clinic"), image);
            request.getSession().setAttribute("flashSuccess", "Doctor account and profile created.");
            response.sendRedirect(request.getContextPath() + "/admin");
        } catch (IllegalArgumentException e) {
            images.delete(image);
            if (updating) { request.getSession().setAttribute("flashError", e.getMessage()); response.sendRedirect(request.getContextPath() + "/admin"); return; }
            request.setAttribute("error", e.getMessage());
            request.getRequestDispatcher("/WEB-INF/views/admin-doctor.jsp").forward(request, response);
        } catch (IllegalStateException e) {
            images.delete(image);
            if (updating) { request.getSession().setAttribute("flashError", "The upload is too large. Choose an image under 5 MB."); response.sendRedirect(request.getContextPath() + "/admin"); return; }
            request.setAttribute("error", "The upload is too large. Choose an image under 5 MB.");
            request.getRequestDispatcher("/WEB-INF/views/admin-doctor.jsp").forward(request, response);
        } catch (Exception e) {
            images.delete(image);
            if (updating) { request.getSession().setAttribute("flashError", "Doctor profile could not be updated. Check that the email is not already used."); response.sendRedirect(request.getContextPath() + "/admin"); return; }
            throw new ServletException("Doctor account could not be created.", e);
        }
    }
}
