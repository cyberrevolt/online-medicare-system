package com.onlinemedic.controller;

import com.onlinemedic.dao.MedicineDao;
import com.onlinemedic.dao.DoctorDao;
import com.onlinemedic.dao.OrderDao;
import com.onlinemedic.model.User;
import com.onlinemedic.util.ImageStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;

@WebServlet(urlPatterns = {"/pharmacist/orders", "/delivery", "/admin", "/admin/orders"})
@MultipartConfig(maxFileSize = ImageStorage.MAX_IMAGE_BYTES, maxRequestSize = 6L * 1024 * 1024)
public class OrderWorkflowServlet extends HttpServlet {
    private final OrderDao orders = new OrderDao();
    private final MedicineDao medicines = new MedicineDao();
    private final DoctorDao doctors = new DoctorDao();
    private final ImageStorage images = new ImageStorage();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        try {
            String path = request.getServletPath();
            if ("/admin".equals(path)) {
                request.setAttribute("stats", new com.onlinemedic.service.DashboardService().adminStats());
                request.setAttribute("report", new com.onlinemedic.service.AdminReportService().last30Days());
                request.setAttribute("adminDoctors", doctors.adminList());
                request.setAttribute("lowStock", medicines.lowStock());
                request.setAttribute("expiryWatch", medicines.expiryWatch(30));
                request.setAttribute("orderQueue", orders.queue("ADMIN", user.id()));
                request.setAttribute("refundQueue", orders.refundQueue());
                request.setAttribute("categories", medicines.categories());
                request.setAttribute("manufacturers", medicines.manufacturers());
                request.getRequestDispatcher("/WEB-INF/views/admin.jsp").forward(request, response);
            } else {
                // The page is forwarded into WEB-INF, so its servletPath may refer to
                // the JSP. Keep the public workflow URL for form submissions.
                request.setAttribute("workflowPostUrl", request.getContextPath() + path);
                request.setAttribute("orderQueue", orders.queue(user.role(), user.id()));
                request.setAttribute("deliveryMode", "DELIVERY".equals(user.role()));
                request.setAttribute("adminMode", "ADMIN".equals(user.role()));
                if (!"DELIVERY".equals(user.role())) request.setAttribute("deliveryStaff", orders.deliveryStaffOptions());
                request.getRequestDispatcher("/WEB-INF/views/order-workflow.jsp").forward(request, response);
            }
        } catch (Exception e) { throw new ServletException("Order workspace is unavailable.", e); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        String path = request.getServletPath();
        String image = null;
        try {
            String action = request.getParameter("action");
            if ("restock".equals(action)) {
                if (!"ADMIN".equals(user.role())) { response.sendError(403); return; }
                medicines.restock(Long.parseLong(request.getParameter("medicineId")), Integer.parseInt(request.getParameter("quantity")), user.id());
                request.getSession().setAttribute("flashSuccess", "Inventory restocked and recorded in the audit log.");
            } else if ("createMedicine".equals(action)) {
                if (!"ADMIN".equals(user.role())) { response.sendError(403); return; }
                image = images.store(request.getPart("image"));
                String price = request.getParameter("price");
                medicines.create(request.getParameter("name"), request.getParameter("category"), request.getParameter("manufacturer"),
                        request.getParameter("description"), new BigDecimal(price), "true".equals(request.getParameter("prescriptionRequired")),
                        Integer.parseInt(request.getParameter("stock")), Integer.parseInt(request.getParameter("minStock")), image, user.id());
                image = null;
                request.getSession().setAttribute("flashSuccess", "Medicine added to the pharmacy catalogue.");
            } else if ("reviewPrescription".equals(action)) {
                orders.reviewPrescription(Long.parseLong(request.getParameter("orderId")), user.id(), user.role(), request.getParameter("decision"));
                request.getSession().setAttribute("flashSuccess", "Order prescription review saved.");
            } else if ("assignDelivery".equals(action)) {
                orders.assignDeliveryStaff(Long.parseLong(request.getParameter("orderId")), Long.parseLong(request.getParameter("deliveryStaffId")), user.id(), user.role());
                request.getSession().setAttribute("flashSuccess", "Delivery staff assigned. The order is now out for delivery.");
            } else if ("approveRefund".equals(action) || "rejectRefund".equals(action)) {
                if (!"/admin".equals(path) || !"ADMIN".equals(user.role())) { response.sendError(403); return; }
                orders.reviewRefund(Long.parseLong(request.getParameter("refundId")), user.id(),
                        "approveRefund".equals(action) ? "APPROVED" : "REJECTED", request.getParameter("note"));
                request.getSession().setAttribute("flashSuccess", "Refund request review saved.");
            } else if ("processRefund".equals(action)) {
                if (!"/admin".equals(path) || !"ADMIN".equals(user.role())) { response.sendError(403); return; }
                orders.processRefund(Long.parseLong(request.getParameter("refundId")), user.id(), request.getParameter("note"));
                request.getSession().setAttribute("flashSuccess", "Demo refund marked processed. No bank transfer was made.");
            } else {
                orders.advance(Long.parseLong(request.getParameter("orderId")), user.id(), user.role(), request.getParameter("status"));
                request.getSession().setAttribute("flashSuccess", "Order status updated.");
            }
            response.sendRedirect(request.getContextPath() + path);
        } catch (SecurityException e) {
            images.delete(image); response.sendError(403);
        } catch (IllegalArgumentException e) {
            images.delete(image); request.getSession().setAttribute("flashError", e.getMessage()); response.sendRedirect(request.getContextPath() + path);
        } catch (IllegalStateException e) {
            images.delete(image); request.getSession().setAttribute("flashError", "The upload is too large. Choose an image under 5 MB."); response.sendRedirect(request.getContextPath() + path);
        } catch (Exception e) {
            images.delete(image); throw new ServletException("The requested update could not be saved.", e);
        }
    }
}
