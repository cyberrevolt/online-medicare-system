package com.onlinemedic.controller;

import com.onlinemedic.dao.MedicineDao;
import com.onlinemedic.dao.AddressDao;
import com.onlinemedic.model.Medicine;
import com.onlinemedic.model.User;
import com.onlinemedic.service.CartTotals;
import com.onlinemedic.service.OrderService;
import com.onlinemedic.util.PrescriptionStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/cart")
@MultipartConfig(maxFileSize = PrescriptionStorage.MAX_BYTES, maxRequestSize = 6L * 1024 * 1024)
public class CartServlet extends HttpServlet {
    private final MedicineDao medicines = new MedicineDao();
    private final OrderService orders = new OrderService();
    private final PrescriptionStorage prescriptionStorage = new PrescriptionStorage();

    @SuppressWarnings("unchecked")
    private Map<Long, Integer> cart(HttpSession session) {
        Map<Long, Integer> value = (Map<Long, Integer>) session.getAttribute("cart");
        if (value == null) {
            value = new LinkedHashMap<>();
            session.setAttribute("cart", value);
        }
        return value;
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            Map<Long, Integer> cart = cart(request.getSession());
            List<Map<String, Object>> lines = new ArrayList<>();
            List<String> prescriptionItems = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (var entry : cart.entrySet()) {
                var found = medicines.find(entry.getKey());
                if (found.isEmpty()) continue;
                Medicine medicine = found.get();
                Map<String, Object> line = new HashMap<>();
                line.put("medicine", medicine);
                line.put("quantity", entry.getValue());
                BigDecimal lineTotal = medicine.price().multiply(BigDecimal.valueOf(entry.getValue()));
                line.put("lineTotal", lineTotal);
                lines.add(line);
                total = total.add(lineTotal);
                if (medicine.prescriptionRequired()) prescriptionItems.add(medicine.name());
            }
            request.setAttribute("lines", lines);
            request.setAttribute("total", total);
            BigDecimal deliveryFee = CartTotals.deliveryFee(total);
            request.setAttribute("deliveryFee", deliveryFee);
            request.setAttribute("grandTotal", total.add(deliveryFee));
            request.setAttribute("prescriptionItems", prescriptionItems);
            if (request.getSession().getAttribute("user") instanceof User user && "PATIENT".equals(user.role())) {
                request.setAttribute("prescriptionUploadRequired", !orders.hasCompleteVerifiedPrescriptionCoverage(user.id(), cart));
                request.setAttribute("savedAddresses", new AddressDao().listForUser(user.id()));
            }
            request.getRequestDispatcher("/WEB-INF/views/cart.jsp").forward(request, response);
        } catch (Exception e) {
            throw new ServletException("Cart is unavailable.", e);
        }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Map<Long, Integer> cart = cart(request.getSession());
        String action = request.getParameter("action");
        String uploadedPrescription = null;
        try {
            if ("checkout".equals(action)) {
                User user = (User) request.getSession().getAttribute("user");
                if (user == null) {
                    response.sendRedirect(request.getContextPath() + "/login/patient?next=%2Fcart");
                    return;
                }
                if (!"PATIENT".equals(user.role())) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                PrescriptionStorage.StoredFile document = prescriptionStorage.store(request.getPart("prescriptionFile"));
                uploadedPrescription = document == null ? null : document.fileName();
                String savedAddressValue = request.getParameter("savedAddressId");
                Long savedAddressId = savedAddressValue == null || savedAddressValue.isBlank() ? null : Long.parseLong(savedAddressValue);
                var result = orders.checkout(user.id(), cart, request.getParameter("method"), request.getParameter("outcome"),
                        request.getParameter("line1"), firstNonBlank(request.getParameter("city"), request.getParameter("cityFallback")),
                        firstNonBlank(request.getParameter("district"), request.getParameter("districtFallback")),
                        request.getParameter("state"), request.getParameter("postalCode"),
                        document == null ? null : document.fileName(), document == null ? null : document.mediaType(), savedAddressId);
                if (result.orderId() > 0) uploadedPrescription = null;
                else prescriptionStorage.delete(uploadedPrescription);
                if (result.paymentSuccess()) cart.clear();
                request.getSession().setAttribute(result.paymentSuccess() ? "flashSuccess" : "flashError", result.message());
                response.sendRedirect(request.getContextPath() + "/cart");
                return;
            }

            long id = Long.parseLong(request.getParameter("medicineId"));
            if ("remove".equals(action)) {
                cart.remove(id);
            } else if ("add".equals(action) || "update".equals(action)) {
                Medicine medicine = medicines.find(id).orElseThrow(() -> new IllegalArgumentException("This medicine is no longer available."));
                if (medicine.price() == null || medicine.price().signum() <= 0)
                    throw new IllegalArgumentException("The pharmacy has not confirmed this medicine’s sale price yet.");
                int quantity = Integer.parseInt(request.getParameter("quantity"));
                if (quantity < 1 || quantity > 30) throw new IllegalArgumentException("Choose a quantity between 1 and 30.");
                int requested = "add".equals(action) ? cart.getOrDefault(id, 0) + quantity : quantity;
                if (requested > 30) throw new IllegalArgumentException("A maximum of 30 units can be kept in the cart.");
                if (requested > medicine.stock()) throw new IllegalArgumentException("Only " + medicine.stock() + " unit(s) of " + medicine.name() + " are in stock.");
                cart.put(id, requested);
                if ("add".equals(action)) request.getSession().setAttribute("flashSuccess", "Medicine added to your cart.");
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            response.sendRedirect(request.getContextPath() + "/cart");
        } catch (IllegalArgumentException e) {
            prescriptionStorage.delete(uploadedPrescription);
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/cart");
        } catch (IllegalStateException e) {
            prescriptionStorage.delete(uploadedPrescription);
            request.getSession().setAttribute("flashError", "The prescription upload is too large. Choose a file under 5 MB.");
            response.sendRedirect(request.getContextPath() + "/cart");
        } catch (Exception e) {
            prescriptionStorage.delete(uploadedPrescription);
            throw new ServletException("Cart action failed.", e);
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}
