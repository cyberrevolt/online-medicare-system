package com.onlinemedic.util;

import jakarta.servlet.http.Part;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Stores patient prescription documents outside the public web directory. */
public final class PrescriptionStorage {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public record StoredFile(String fileName, String mediaType) { }

    public StoredFile store(Part part) throws IOException {
        if (part == null || part.getSize() == 0) return null;
        if (part.getSize() > MAX_BYTES) throw new IllegalArgumentException("Choose a prescription file under 5 MB.");
        byte[] bytes;
        try (var input = part.getInputStream()) { bytes = input.readNBytes((int) MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Choose a prescription file under 5 MB.");

        String extension;
        String mediaType;
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') {
            extension = ".pdf";
            mediaType = "application/pdf";
        } else if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G' && (bytes[4] & 0xff) == 0x0d && (bytes[5] & 0xff) == 0x0a && (bytes[6] & 0xff) == 0x1a && (bytes[7] & 0xff) == 0x0a) {
            extension = ".png";
            mediaType = "image/png";
            BufferedImage image;
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) { image = ImageIO.read(input); }
            validateImage(image);
        } else if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            extension = ".jpg";
            mediaType = "image/jpeg";
            BufferedImage image;
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) { image = ImageIO.read(input); }
            validateImage(image);
        } else throw new IllegalArgumentException("Upload a clear PDF, JPEG, or PNG prescription image.");

        String fileName = UUID.randomUUID() + extension;
        Path target = directory().resolve(fileName);
        Files.createDirectories(target.getParent());
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return new StoredFile(fileName, mediaType);
    }

    public Path resolve(String fileName) {
        if (fileName == null || !fileName.matches("[0-9a-fA-F-]{36}\\.(pdf|jpg|png)")) return null;
        Path root = directory().toAbsolutePath().normalize();
        Path file = root.resolve(fileName).normalize();
        return file.startsWith(root) && Files.isRegularFile(file) ? file : null;
    }

    public void delete(String fileName) {
        Path file = resolve(fileName);
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (IOException ignored) { }
    }

    private void validateImage(BufferedImage image) {
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 6000 || image.getHeight() > 6000 || (long) image.getWidth() * image.getHeight() > 20_000_000L)
            throw new IllegalArgumentException("Upload a clear PDF, JPEG, or PNG prescription image.");
    }

    private Path directory() {
        String configured = System.getProperty("PRESCRIPTION_UPLOAD_DIR");
        if (configured == null || configured.isBlank()) configured = System.getenv("PRESCRIPTION_UPLOAD_DIR");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        return Path.of(System.getProperty("user.home"), "OnlineMedicData", "prescriptions").toAbsolutePath().normalize();
    }
}
