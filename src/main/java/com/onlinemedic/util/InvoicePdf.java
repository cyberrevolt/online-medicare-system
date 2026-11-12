package com.onlinemedic.util;

import com.onlinemedic.model.OrderSummary;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;

/** Generates a patient invoice using the supplied OnlineMedic Care artwork as its PDF page template. */
public final class InvoicePdf {
    private static final int IMAGE_WIDTH = 1199;
    private static final int IMAGE_HEIGHT = 1312;
    private static final float PAGE_WIDTH = 612f;
    private static final float SCALE = PAGE_WIDTH / IMAGE_WIDTH;
    private static final float PAGE_HEIGHT = IMAGE_HEIGHT * SCALE;
    private static final int IMAGE_OBJECT = 3;
    private static final int REGULAR_FONT_OBJECT = 4;
    private static final int BOLD_FONT_OBJECT = 5;
    private static final int FIRST_PAGE_OBJECT = 6;
    private static final int ITEMS_PER_CONTINUATION_PAGE = 24;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public byte[] generate(OrderSummary order) throws IOException {
        TemplateImage template = readTemplate();
        int firstPageItems = order.items().size() > 5 ? 4 : Math.min(order.items().size(), 5);
        int remaining = Math.max(0, order.items().size() - firstPageItems);
        int continuationPages = (remaining + ITEMS_PER_CONTINUATION_PAGE - 1) / ITEMS_PER_CONTINUATION_PAGE;
        List<PdfPage> pageContents = new ArrayList<>();
        pageContents.add(new PdfPage(firstPage(order, template, firstPageItems, remaining > 0), PAGE_WIDTH, PAGE_HEIGHT));
        for (int page = 0; page < continuationPages; page++) {
            int from = firstPageItems + page * ITEMS_PER_CONTINUATION_PAGE;
            int to = Math.min(order.items().size(), from + ITEMS_PER_CONTINUATION_PAGE);
            pageContents.add(new PdfPage(continuationPage(order, from, to, page + 2, page == continuationPages - 1), PAGE_WIDTH, 792f));
        }
        return assemblePdf(template, pageContents);
    }

    private TemplateImage readTemplate() throws IOException {
        try (var input = InvoicePdf.class.getResourceAsStream("/pdf/invoice-template.png")) {
            if (input == null) throw new IOException("The invoice template image is missing from the application.");
            byte[] png = input.readAllBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null || image.getWidth() != IMAGE_WIDTH || image.getHeight() != IMAGE_HEIGHT)
                throw new IOException("The invoice template image has unexpected dimensions.");
            ByteArrayOutputStream pixels = new ByteArrayOutputStream(IMAGE_WIDTH * IMAGE_HEIGHT * 3);
            for (int y = 0; y < IMAGE_HEIGHT; y++) {
                for (int x = 0; x < IMAGE_WIDTH; x++) {
                    int rgb = image.getRGB(x, y);
                    pixels.write((rgb >>> 16) & 0xff); pixels.write((rgb >>> 8) & 0xff); pixels.write(rgb & 0xff);
                }
            }
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) { pixels.writeTo(deflater); }
            return new TemplateImage(compressed.toByteArray());
        }
    }

    private byte[] firstPage(OrderSummary order, TemplateImage template, int displayedItems, boolean hasMore) {
        StringBuilder c = new StringBuilder();
        c.append("q ").append(PAGE_WIDTH).append(" 0 0 ").append(PAGE_HEIGHT).append(" 0 0 cm /Im0 Do Q\n");

        // Billed-to card: replace the sample customer details while preserving the artwork around it.
        roundedRect(c, 112, 302, 528, 185, 15, "0.955 0.970 0.995");
        textPx(c, 190, 338, 25, true, "Billed To:", "0.055 0.170 0.390");
        textPx(c, 190, 374, fitSize(order.patientName(), 360, 28, 18), true, order.patientName(), "0.055 0.075 0.120");
        drawUserIcon(c, 151, 333);
        drawLocationIcon(c, 192, 401);
        String address = safe(order.city()) + (blank(order.district()) ? "" : ", " + safe(order.district()))
                + (blank(order.state()) ? "" : ", " + safe(order.state())) + (blank(order.postalCode()) ? "" : " - " + safe(order.postalCode()));
        textPx(c, 220, 408, fitSize(address, 385, 20, 12), false, address, "0.035 0.090 0.200");
        drawEnvelopeIcon(c, 192, 431);
        textPx(c, 220, 441, fitSize(order.patientEmail(), 385, 20, 12), false, order.patientEmail(), "0.035 0.090 0.200");
        drawPhoneIcon(c, 193, 461);
        textPx(c, 220, 474, fitSize(order.patientPhone(), 385, 20, 12), false, order.patientPhone(), "0.035 0.090 0.200");

        // Order metadata card: this artwork labels its third line Appointment ID; here it is the order ID.
        roundedRect(c, 692, 309, 365, 153, 14, "0.955 0.970 0.995");
        String invoiceNumber = String.format(Locale.ROOT, "OMC-%d-%06d", order.createdAt().getYear(), order.id());
        metadataLine(c, 714, 348, "Invoice No:", invoiceNumber);
        metadataLine(c, 714, 390, "Invoice Date:", DATE.format(order.createdAt()));
        metadataLine(c, 714, 432, "Order ID:", "ORD-" + order.id());

        // Keep the artwork's blue table header and replace all three example rows with real order lines.
        roundedRect(c, 101, 586, 1014, 174, 10, "0.965 0.976 0.998");
        int visibleRows = hasMore ? 5 : Math.max(3, displayedItems);
        float rowHeight = 170f / visibleRows;
        float tableTop = 588f;
        for (int row = 0; row < visibleRows; row++) {
            float top = tableTop + row * rowHeight;
            float bottom = top + rowHeight;
            if (row % 2 == 1) rectPx(c, 103, top, 1010, rowHeight, "0.945 0.961 0.990");
            linePx(c, 102, bottom, 1114, bottom, "0.735 0.805 0.900", 0.7f);
            if (row < displayedItems) {
                OrderSummary.Item item = order.items().get(row);
                String name = safe(item.name());
                float size = fitSize(name, 365, Math.min(20, rowHeight * 0.50f), 8);
                float baseline = top + rowHeight * 0.63f;
                textCenterPx(c, 142, baseline, size, false, Integer.toString(row + 1), "0.035 0.090 0.200");
                textPx(c, 215, baseline, size, false, name, "0.035 0.090 0.200");
                textCenterPx(c, 658, baseline, size, false, Integer.toString(item.quantity()), "0.035 0.090 0.200");
                textCenterPx(c, 830, baseline, size, false, item.unitPrice().toPlainString(), "0.035 0.090 0.200");
                textCenterPx(c, 1022, baseline, size, false, item.lineTotal().toPlainString(), "0.035 0.090 0.200");
            } else if (hasMore && row == visibleRows - 1) {
                textPx(c, 215, top + rowHeight * 0.64f, 14, true, "Additional products continue on page 2.", "0.055 0.300 0.660");
            }
        }
        for (float boundary : new float[]{184, 578, 739, 923}) linePx(c, boundary, 588, boundary, 758, "0.760 0.820 0.910", 0.7f);

        // Payment panel.
        roundedRect(c, 110, 807, 515, 180, 14, "0.955 0.970 0.995");
        drawCardIcon(c, 153, 832);
        textPx(c, 196, 846, 25, true, "Payment Details", "0.055 0.170 0.390");
        String paymentMethod = "DEMO_CARD".equals(order.paymentMethod()) ? "Online / Demo card" : "COD".equals(order.paymentMethod()) ? "Cash on delivery" : safe(order.paymentMethod());
        paymentLine(c, "Payment Method", paymentMethod, 882);
        paymentLine(c, "Transaction ID", safe(order.transactionId()), 921);
        textPx(c, 143, 961, 20, false, "Payment Status", "0.035 0.090 0.200");
        textPx(c, 365, 961, 20, false, ":", "0.035 0.090 0.200");
        String paymentStatus = safe(order.paymentStatus());
        boolean paid = "SUCCESS".equalsIgnoreCase(paymentStatus);
        if (paid) roundedRect(c, 391, 938, 117, 37, 17, "0.035 0.610 0.265");
        textPx(c, paid ? 405 : 391, 963, 19, true, paid ? "Paid" : paymentStatus, paid ? "1 1 1" : "0.035 0.090 0.200");

        // Totals card. There is no discount in this application, so the reference's second row becomes delivery.
        roundedRect(c, 677, 802, 447, 199, 15, "0.955 0.970 0.995");
        totalsLine(c, "Subtotal", order.subtotal().toPlainString(), 842, "0.035 0.075 0.120");
        totalsLine(c, "Delivery", order.deliveryFee().signum() == 0 ? "Free" : order.deliveryFee().toPlainString(), 888,
                order.deliveryFee().signum() == 0 ? "0.035 0.520 0.250" : "0.035 0.075 0.120");
        roundedRect(c, 680, 918, 444, 69, 15, "0.020 0.350 0.790");
        textPx(c, 706, 960, 25, true, "Total Amount", "1 1 1");
        moneyRightPx(c, 1096, 960, 27, true, order.total().toPlainString(), "1 1 1");
        c.append("\n");
        return ascii(c.toString());
    }

    private byte[] continuationPage(OrderSummary order, int from, int to, int pageNumber, boolean lastPage) {
        StringBuilder c = new StringBuilder();
        rectPt(c, 0, 697, 612, 95, "0.025 0.340 0.760");
        textPt(c, 44, 748, 20, true, "OnlineMedic Care - Invoice continued", "1 1 1");
        textPt(c, 44, 719, 10, false, "Invoice OMC-" + order.createdAt().getYear() + "-" + order.id() + "  |  Order ORD-" + order.id() + "  |  Page " + pageNumber, "0.900 0.950 1");
        textPt(c, 44, 671, 11, true, "Patient: " + safe(order.patientName()), "0.055 0.170 0.390");
        textPt(c, 44, 651, 10, false, "Order date: " + DATE.format(order.createdAt()), "0.180 0.260 0.390");

        rectPt(c, 42, 610, 528, 28, "0.020 0.350 0.790");
        textPt(c, 53, 620, 10, true, "#", "1 1 1");
        textPt(c, 83, 620, 10, true, "Product / Item", "1 1 1");
        textPt(c, 380, 620, 10, true, "Qty", "1 1 1");
        textPt(c, 425, 620, 10, true, "Unit price (INR)", "1 1 1");
        textPt(c, 520, 620, 10, true, "Amount", "1 1 1");
        float yTop = 610;
        for (int i = from; i < to; i++) {
            float bottom = yTop - (i - from + 1) * 24;
            if ((i - from) % 2 == 1) rectPt(c, 42, bottom, 528, 24, "0.945 0.961 0.990");
            linePt(c, 42, bottom, 570, bottom, "0.760 0.820 0.910", 0.6f);
            OrderSummary.Item item = order.items().get(i);
            textPt(c, 53, bottom + 8, 9, false, Integer.toString(i + 1), "0.035 0.090 0.200");
            textPt(c, 83, bottom + 8, fitPointSize(safe(item.name()), 285, 9, 6), false, safe(item.name()), "0.035 0.090 0.200");
            textPt(c, 386, bottom + 8, 9, false, Integer.toString(item.quantity()), "0.035 0.090 0.200");
            textRightPt(c, 488, bottom + 8, 9, false, item.unitPrice().toPlainString(), "0.035 0.090 0.200");
            textRightPt(c, 560, bottom + 8, 9, false, item.lineTotal().toPlainString(), "0.035 0.090 0.200");
        }
        float bottom = yTop - (to - from) * 24 - 22;
        if (lastPage) {
            textRightPt(c, 560, bottom, 10, false, "Subtotal: INR " + order.subtotal().toPlainString(), "0.035 0.075 0.120");
            textRightPt(c, 560, bottom - 19, 10, false, "Delivery: INR " + order.deliveryFee().toPlainString(), "0.035 0.075 0.120");
            textRightPt(c, 560, bottom - 42, 12, true, "Total: INR " + order.total().toPlainString(), "0.020 0.350 0.790");
        }
        textPt(c, 44, 38, 8, false, "Order receipt for the Online Medic demonstration application.", "0.400 0.480 0.580");
        return ascii(c.toString());
    }

    private byte[] assemblePdf(TemplateImage image, List<PdfPage> pageContents) throws IOException {
        int pageCount = pageContents.size();
        int maxObject = 5 + pageCount * 2;
        byte[][] objects = new byte[maxObject + 1][];
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) kids.append(FIRST_PAGE_OBJECT + i * 2).append(" 0 R ");
        objects[1] = ascii("<< /Type /Catalog /Pages 2 0 R >>");
        objects[2] = ascii("<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>");
        objects[3] = imageObject(image.compressedPixels);
        objects[4] = ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        objects[5] = ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
        for (int i = 0; i < pageCount; i++) {
            int pageObject = FIRST_PAGE_OBJECT + i * 2;
            int streamObject = pageObject + 1;
            PdfPage page = pageContents.get(i);
            objects[pageObject] = ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + page.width + " " + page.height + "] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> /XObject << /Im0 3 0 R >> >> /Contents " + streamObject + " 0 R >>");
            objects[streamObject] = streamObject(page.content);
        }
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.write(ascii("%PDF-1.4\n"));
        long[] offsets = new long[maxObject + 1];
        for (int i = 1; i <= maxObject; i++) {
            offsets[i] = pdf.size();
            pdf.write(ascii(i + " 0 obj\n")); pdf.write(objects[i]); pdf.write(ascii("\nendobj\n"));
        }
        long xrefOffset = pdf.size();
        pdf.write(ascii("xref\n0 " + (maxObject + 1) + "\n0000000000 65535 f \n"));
        for (int i = 1; i <= maxObject; i++) pdf.write(ascii(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i])));
        pdf.write(ascii("trailer\n<< /Size " + (maxObject + 1) + " /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n"));
        return pdf.toByteArray();
    }

    private byte[] imageObject(byte[] compressedPixels) throws IOException {
        ByteArrayOutputStream object = new ByteArrayOutputStream();
        object.write(ascii("<< /Type /XObject /Subtype /Image /Width " + IMAGE_WIDTH + " /Height " + IMAGE_HEIGHT + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length " + compressedPixels.length + " >>\nstream\n"));
        object.write(compressedPixels); object.write(ascii("\nendstream"));
        return object.toByteArray();
    }

    private byte[] streamObject(byte[] content) throws IOException {
        ByteArrayOutputStream object = new ByteArrayOutputStream();
        object.write(ascii("<< /Length " + content.length + " >>\nstream\n")); object.write(content); object.write(ascii("\nendstream"));
        return object.toByteArray();
    }

    private void metadataLine(StringBuilder c, float x, float y, String label, String value) {
        textPx(c, x, y, 19, true, label, "0.055 0.170 0.390");
        textPx(c, x + 163, y, fitSize(value, 177, 19, 12), false, value, "0.035 0.075 0.120");
    }

    private void paymentLine(StringBuilder c, String label, String value, float y) {
        textPx(c, 143, y, 20, false, label, "0.035 0.090 0.200");
        textPx(c, 364, y, 20, false, ":", "0.035 0.090 0.200");
        textPx(c, 389, y, fitSize(value, 214, 20, 10), false, value, "0.035 0.090 0.200");
    }

    private void totalsLine(StringBuilder c, String label, String value, float y, String color) {
        textPx(c, 702, y, 24, true, label, "0.035 0.075 0.120");
        if (!"Free".equals(value)) moneyRightPx(c, 1093, y, 24, true, value, color);
        else textRightPx(c, 1093, y, 24, true, value, color);
    }

    private void drawUserIcon(StringBuilder c, float x, float y) {
        circlePx(c, x, y, 8, "0.015 0.580 0.700", false, 2);
        linePx(c, x - 13, y - 17, x + 13, y - 17, "0.015 0.580 0.700", 2);
        linePx(c, x - 13, y - 17, x - 8, y - 7, "0.015 0.580 0.700", 2);
        linePx(c, x + 13, y - 17, x + 8, y - 7, "0.015 0.580 0.700", 2);
    }

    private void drawLocationIcon(StringBuilder c, float x, float y) { circlePx(c, x, y, 8, "0.020 0.350 0.790", false, 2); circlePx(c, x, y, 2.5f, "0.020 0.350 0.790", false, 2); }
    private void drawEnvelopeIcon(StringBuilder c, float x, float y) { rectPx(c, x - 9, y - 6, 18, 12, "0.020 0.350 0.790", 1.6f); linePx(c, x - 8, y + 5, x, y - 1, "0.020 0.350 0.790", 1.2f); linePx(c, x + 8, y + 5, x, y - 1, "0.020 0.350 0.790", 1.2f); }
    private void drawPhoneIcon(StringBuilder c, float x, float y) {
        rectPx(c, x - 6, y - 10, 12, 19, "0.020 0.350 0.790", 1.8f);
        linePx(c, x - 3, y - 7, x + 3, y - 7, "0.020 0.350 0.790", 1.1f);
        linePx(c, x - 2, y + 6, x + 2, y + 6, "0.020 0.350 0.790", 1.1f);
    }

    private void drawCardIcon(StringBuilder c, float x, float y) {
        rectPx(c, x - 18, y - 13, 36, 25, "0.020 0.480 0.850", 2);
        linePx(c, x - 16, y + 4, x + 16, y + 4, "0.020 0.480 0.850", 3);
        linePx(c, x - 12, y - 6, x - 3, y - 6, "0.020 0.480 0.850", 2);
    }

    private void roundedRect(StringBuilder c, float x, float top, float width, float height, float radius, String color) {
        float s = SCALE, left = x * s, bottom = (IMAGE_HEIGHT - top - height) * s, w = width * s, h = height * s, r = radius * s, k = 0.55228475f;
        c.append(color).append(" rg\n").append(left + r).append(' ').append(bottom).append(" m ")
                .append(left + w - r).append(' ').append(bottom).append(" l ")
                .append(left + w - r + k * r).append(' ').append(bottom).append(' ').append(left + w).append(' ').append(bottom + r - k * r).append(' ').append(left + w).append(' ').append(bottom + r).append(" c ")
                .append(left + w).append(' ').append(bottom + h - r).append(" l ")
                .append(left + w).append(' ').append(bottom + h - r + k * r).append(' ').append(left + w - r + k * r).append(' ').append(bottom + h).append(' ').append(left + w - r).append(' ').append(bottom + h).append(" c ")
                .append(left + r).append(' ').append(bottom + h).append(" l ")
                .append(left + r - k * r).append(' ').append(bottom + h).append(' ').append(left).append(' ').append(bottom + h - r + k * r).append(' ').append(left).append(' ').append(bottom + h - r).append(" c ")
                .append(left).append(' ').append(bottom + r).append(" l ")
                .append(left).append(' ').append(bottom + r - k * r).append(' ').append(left + r - k * r).append(' ').append(bottom).append(' ').append(left + r).append(' ').append(bottom).append(" c f\n");
    }

    private void rectPx(StringBuilder c, float x, float top, float width, float height, String color) {
        c.append(color).append(" rg ").append(x * SCALE).append(' ').append((IMAGE_HEIGHT - top - height) * SCALE).append(' ').append(width * SCALE).append(' ').append(height * SCALE).append(" re f\n");
    }

    private void rectPx(StringBuilder c, float x, float top, float width, float height, String color, float stroke) {
        c.append(color).append(" RG ").append(stroke * SCALE).append(" w ").append(x * SCALE).append(' ').append((IMAGE_HEIGHT - top - height) * SCALE).append(' ').append(width * SCALE).append(' ').append(height * SCALE).append(" re S\n");
    }

    private void linePx(StringBuilder c, float x1, float y1, float x2, float y2, String color, float width) {
        c.append(color).append(" RG ").append(width * SCALE).append(" w ").append(x1 * SCALE).append(' ').append((IMAGE_HEIGHT - y1) * SCALE).append(" m ").append(x2 * SCALE).append(' ').append((IMAGE_HEIGHT - y2) * SCALE).append(" l S\n");
    }

    private void circlePx(StringBuilder c, float x, float y, float radius, String color, boolean fill, float width) {
        float k = 0.55228475f * radius, xx = x * SCALE, yy = (IMAGE_HEIGHT - y) * SCALE, r = radius * SCALE, kk = k * SCALE;
        c.append(color).append(fill ? " rg " : " RG ").append(width * SCALE).append(" w ")
                .append(xx + r).append(' ').append(yy).append(" m ").append(xx + r).append(' ').append(yy + kk).append(' ').append(xx + kk).append(' ').append(yy + r).append(' ').append(xx).append(' ').append(yy + r).append(" c ")
                .append(xx - kk).append(' ').append(yy + r).append(' ').append(xx - r).append(' ').append(yy + kk).append(' ').append(xx - r).append(' ').append(yy).append(" c ")
                .append(xx - r).append(' ').append(yy - kk).append(' ').append(xx - kk).append(' ').append(yy - r).append(' ').append(xx).append(' ').append(yy - r).append(" c ")
                .append(xx + kk).append(' ').append(yy - r).append(' ').append(xx + r).append(' ').append(yy - kk).append(' ').append(xx + r).append(' ').append(yy).append(" c ").append(fill ? "f\n" : "S\n");
    }

    private void textPx(StringBuilder c, float x, float baselineTop, float fontPixels, boolean bold, String value, String color) {
        c.append(color).append(" rg BT /").append(bold ? "F2" : "F1").append(' ').append(fontPixels * SCALE).append(" Tf ")
                .append(x * SCALE).append(' ').append(PAGE_HEIGHT - baselineTop * SCALE).append(" Td (").append(escape(safe(value))).append(") Tj ET\n");
    }

    private void textCenterPx(StringBuilder c, float center, float baselineTop, float fontPixels, boolean bold, String value, String color) {
        float width = value.length() * fontPixels * 0.51f;
        textPx(c, center - width / 2, baselineTop, fontPixels, bold, value, color);
    }

    private void textRightPx(StringBuilder c, float right, float baselineTop, float fontPixels, boolean bold, String value, String color) {
        float width = value.length() * fontPixels * 0.51f;
        textPx(c, right - width, baselineTop, fontPixels, bold, value, color);
    }

    private void moneyRightPx(StringBuilder c, float right, float baselineTop, float fontPixels, boolean bold, String value, String color) {
        float textWidth = value.length() * fontPixels * 0.51f;
        float start = right - textWidth;
        drawRupeePx(c, start - fontPixels * 1.10f, baselineTop, fontPixels * 0.92f, color);
        textPx(c, start, baselineTop, fontPixels, bold, value, color);
    }

    /** Draw a small rupee mark with vector strokes so it works with the built-in PDF fonts. */
    private void drawRupeePx(StringBuilder c, float x, float baselineTop, float size, String color) {
        textPx(c, x, baselineTop, size, true, "R", color);
        linePx(c, x - size * 0.04f, baselineTop - size * 0.84f, x + size * 0.82f, baselineTop - size * 0.84f, color, Math.max(1.4f, size * 0.11f));
        linePx(c, x - size * 0.04f, baselineTop - size * 0.54f, x + size * 0.82f, baselineTop - size * 0.54f, color, Math.max(1.4f, size * 0.11f));
    }

    private float fitSize(String value, float maxWidth, float desired, float minimum) {
        if (value == null) return desired;
        return Math.max(minimum, Math.min(desired, maxWidth / Math.max(1, value.length() * 0.51f)));
    }

    private void rectPt(StringBuilder c, float x, float y, float width, float height, String color) { c.append(color).append(" rg ").append(x).append(' ').append(y).append(' ').append(width).append(' ').append(height).append(" re f\n"); }
    private void linePt(StringBuilder c, float x1, float y1, float x2, float y2, String color, float width) { c.append(color).append(" RG ").append(width).append(" w ").append(x1).append(' ').append(y1).append(" m ").append(x2).append(' ').append(y2).append(" l S\n"); }

    private void textPt(StringBuilder c, float x, float y, float size, boolean bold, String value, String color) {
        c.append(color).append(" rg BT /").append(bold ? "F2" : "F1").append(' ').append(size).append(" Tf ").append(x).append(' ').append(y).append(" Td (").append(escape(safe(value))).append(") Tj ET\n");
    }

    private void textRightPt(StringBuilder c, float right, float y, float size, boolean bold, String value, String color) {
        textPt(c, right - value.length() * size * 0.51f, y, size, bold, value, color);
    }

    private float fitPointSize(String value, float maxWidth, float desired, float minimum) { return Math.max(minimum, Math.min(desired, maxWidth / Math.max(1, value.length() * 0.51f))); }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "-";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            result.append(ch >= 32 && ch <= 126 ? ch : '?');
        }
        return result.toString();
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String escape(String value) { return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"); }
    private byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private record TemplateImage(byte[] compressedPixels) { }
    private record PdfPage(byte[] content, float width, float height) { }
}
