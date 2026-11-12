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

@WebServlet("/admin/staff")
@MultipartConfig(maxFileSize = ImageStorage.MAX_IMAGE_BYTES, maxRequestSize = 6L * 1024 * 1024)
public class AdminStaffServlet extends HttpServlet {
    private final AdminService service = new AdminService();
    private final ImageStorage images = new ImageStorage();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try { request.setAttribute("staff", service.staffList()); }
        catch (Exception e) { throw new ServletException("Staff list could not be loaded.", e); }
        request.getRequestDispatcher("/WEB-INF/views/admin-staff.jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User admin = (User) request.getSession().getAttribute("user");
        String image = null;
        boolean updating = "updateStaff".equals(request.getParameter("action"));
        try {
            if (updating) {
                service.updateStaff(admin.id(), Long.parseLong(request.getParameter("staffId")), request.getParameter("role"),
                        request.getParameter("name"), request.getParameter("email"), request.getParameter("phone"),
                        "true".equals(request.getParameter("active")), request.getParameter("newPassword"),
                        request.getParameter("credential"), request.getParameter("location"));
                request.getSession().setAttribute("flashSuccess", "Staff account updated.");
                response.sendRedirect(request.getContextPath() + "/admin/staff");
                return;
            }
            String role = request.getParameter("role");
            image = "PHARMACIST".equals(role) ? images.store(request.getPart("profileImage")) : null;
            service.createStaff(admin.id(), request.getParameter("name"), request.getParameter("email"), request.getParameter("phone"),
                    request.getParameter("password"), role, request.getParameter("credential"), request.getParameter("location"), image);
            request.getSession().setAttribute("flashSuccess", "Staff account and profile created.");
            response.sendRedirect(request.getContextPath() + "/admin/staff");
        } catch (IllegalArgumentException e) {
            images.delete(image);
            request.setAttribute("error", e.getMessage());
            try { request.setAttribute("staff", service.staffList()); }
            catch (Exception loadError) { throw new ServletException("Staff list could not be loaded.", loadError); }
            request.getRequestDispatcher("/WEB-INF/views/admin-staff.jsp").forward(request, response);
        } catch (IllegalStateException e) {
            images.delete(image);
            request.setAttribute("error", "The upload is too large. Choose an image under 5 MB.");
            try { request.setAttribute("staff", service.staffList()); }
            catch (Exception loadError) { throw new ServletException("Staff list could not be loaded.", loadError); }
            request.getRequestDispatcher("/WEB-INF/views/admin-staff.jsp").forward(request, response);
        } catch (Exception e) {
            images.delete(image);
            if (updating) {
                request.getSession().setAttribute("flashError", "Staff account could not be updated. Check that the email is not already in use and that active deliveries are reassigned before pausing staff.");
                response.sendRedirect(request.getContextPath() + "/admin/staff");
                return;
            }
            throw new ServletException("Staff account could not be created.", e);
        }
    }
}
