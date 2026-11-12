package com.onlinemedic.controller;

import com.onlinemedic.dao.MedicineDao;
import com.onlinemedic.dao.PharmacyDao;
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

@WebServlet("/pharmacist/manage")
@MultipartConfig(maxFileSize = ImageStorage.MAX_IMAGE_BYTES, maxRequestSize = 6L * 1024 * 1024)
public class PharmacyManagementServlet extends HttpServlet {
    private final PharmacyDao pharmacy = new PharmacyDao();
    private final MedicineDao medicines = new MedicineDao();
    private final ImageStorage images = new ImageStorage();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        try {
            request.setAttribute("pharmacy", pharmacy.findForManager(user.id()));
            request.setAttribute("medicines", medicines.search(null, null, null));
            request.setAttribute("categories", medicines.categories());
            request.setAttribute("manufacturers", medicines.manufacturers());
            request.setAttribute("suppliers", medicines.suppliers());
            request.setAttribute("expiryWatch", medicines.expiryWatch(30));
            request.setAttribute("batchInventory", medicines.batchInventory());
            request.setAttribute("today", java.time.LocalDate.now());
            request.setAttribute("expiryMin", java.time.LocalDate.now().plusDays(1));
            request.getRequestDispatcher("/WEB-INF/views/pharmacy-management.jsp").forward(request, response);
        } catch (Exception e) {
            throw new ServletException("Pharmacy management is unavailable.", e);
        }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = (User) request.getSession().getAttribute("user");
        String path = request.getContextPath() + "/pharmacist/manage";
        String uploadedImage = null;
        try {
            String action = request.getParameter("action");
            if ("saveProfile".equals(action)) {
                uploadedImage = images.store(request.getPart("profileImage"));
                pharmacy.updateStorefront(user.id(), request.getParameter("pharmacyName"), uploadedImage);
                uploadedImage = null;
                request.getSession().setAttribute("flashSuccess", "Pharmacy profile saved and storefront updated.");
            } else if ("addMedicine".equals(action)) {
                uploadedImage = images.store(request.getPart("medicineImage"));
                medicines.create(request.getParameter("name"), request.getParameter("category"), request.getParameter("manufacturer"),
                        request.getParameter("description"), new BigDecimal(request.getParameter("price")),
                        "true".equals(request.getParameter("prescriptionRequired")),
                        Integer.parseInt(request.getParameter("stock")), Integer.parseInt(request.getParameter("minStock")), uploadedImage, user.id());
                uploadedImage = null;
                request.getSession().setAttribute("flashSuccess", "Medicine added to your pharmacy catalogue.");
            } else if ("configureMedicine".equals(action)) {
                medicines.configureImported(Long.parseLong(request.getParameter("medicineId")),
                        new BigDecimal(request.getParameter("price")), request.getParameter("description"),
                        "true".equals(request.getParameter("prescriptionRequired")),
                        Integer.parseInt(request.getParameter("stock")), Integer.parseInt(request.getParameter("minStock")), user.id());
                request.getSession().setAttribute("flashSuccess", "Medicine details saved and inventory configured.");
            } else if ("updateMedicine".equals(action)) {
                long medicineId = Long.parseLong(request.getParameter("medicineId"));
                var existing = medicines.find(medicineId).orElseThrow(() -> new IllegalArgumentException("This medicine is no longer available to edit."));
                String previousImage = existing.imageUrl();
                uploadedImage = images.store(request.getPart("medicineImage"));
                medicines.updateDetails(medicineId, request.getParameter("name"), request.getParameter("category"),
                        request.getParameter("manufacturer"), request.getParameter("description"),
                        new BigDecimal(request.getParameter("price")), "true".equals(request.getParameter("prescriptionRequired")),
                        Integer.parseInt(request.getParameter("minStock")), uploadedImage, user.id());
                if (uploadedImage != null && !uploadedImage.equals(previousImage)) images.delete(previousImage);
                uploadedImage = null;
                request.getSession().setAttribute("flashSuccess", "Medicine details updated.");
            } else if ("restock".equals(action)) {
                medicines.restock(Long.parseLong(request.getParameter("medicineId")), Integer.parseInt(request.getParameter("quantity")), user.id());
                request.getSession().setAttribute("flashSuccess", "Inventory updated and recorded.");
            } else if ("createSupplier".equals(action)) {
                medicines.createSupplier(request.getParameter("name"), request.getParameter("email"), request.getParameter("phone"), user.id());
                request.getSession().setAttribute("flashSuccess", "Supplier saved.");
            } else if ("receiveBatch".equals(action)) {
                String manufacturing = request.getParameter("manufacturingDate"), expiry = request.getParameter("expiryDate");
                medicines.receiveBatch(Long.parseLong(request.getParameter("medicineId")), Long.parseLong(request.getParameter("supplierId")),
                        request.getParameter("batchNumber"), parseDate(manufacturing), parseDate(expiry), Integer.parseInt(request.getParameter("quantity")),
                        new BigDecimal(request.getParameter("unitCost")), user.id());
                request.getSession().setAttribute("flashSuccess", "Supplier batch received, stock updated, and purchase recorded.");
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            response.sendRedirect(path);
        } catch (SecurityException e) {
            images.delete(uploadedImage);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        } catch (IllegalArgumentException e) {
            images.delete(uploadedImage);
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(path);
        } catch (IllegalStateException e) {
            images.delete(uploadedImage);
            request.getSession().setAttribute("flashError", "The upload is too large. Choose an image under 5 MB.");
            response.sendRedirect(path);
        } catch (Exception e) {
            images.delete(uploadedImage);
            throw new ServletException("Pharmacy changes could not be saved.", e);
        }
    }

    private java.time.LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return java.time.LocalDate.parse(value); }
        catch (java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("Enter dates in the calendar fields provided."); }
    }
}
