package com.onlinemedic.util;

import jakarta.servlet.http.Part;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/** Stores validated, re-encoded images outside the deployed web application. */
public final class ImageStorage {
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 6000;
    private static final long MAX_PIXELS = 20_000_000L;

    public String store(Part part) throws IOException {
        if (part == null || part.getSize() == 0) return null;
        if (part.getSize() > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Choose an image smaller than 5 MB.");

        BufferedImage image;
        String format;
        try (ImageInputStream input = ImageIO.createImageInputStream(part.getInputStream())) {
            if (input == null) throw new IllegalArgumentException("Choose a JPEG or PNG image.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Choose a JPEG or PNG image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals("jpeg") || format.equals("jpg") || format.equals("png")))
                    throw new IllegalArgumentException("Only JPEG and PNG images are supported.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS)
                    throw new IllegalArgumentException("Image dimensions are too large. Use an image up to 6,000 pixels per side.");
                image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("The selected image could not be decoded.");
            } finally { reader.dispose(); }
        }

        String extension = format.equals("png") ? ".png" : ".jpg";
        String filename = UUID.randomUUID() + extension;
        Path target = directory().resolve(filename);
        Files.createDirectories(target.getParent());
        if (!ImageIO.write(image, extension.equals(".png") ? "png" : "jpeg", target.toFile()))
            throw new IOException("No image encoder is available for the selected image.");
        return "/media/" + filename;
    }

    public Path resolve(String filename) throws IOException {
        if (filename == null || !filename.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) return null;
        Path root = directory().toAbsolutePath().normalize();
        Path file = root.resolve(filename).normalize();
        return file.startsWith(root) && Files.isRegularFile(file) ? file : null;
    }

    public void delete(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/media/")) return;
        try {
            Path file = resolve(imageUrl.substring("/media/".length()));
            if (file != null) Files.deleteIfExists(file);
        } catch (IOException ignored) { }
    }

    private Path directory() {
        String configured = System.getProperty("UPLOAD_DIR");
        if (configured == null || configured.isBlank()) configured = System.getenv("UPLOAD_DIR");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        return Path.of(System.getProperty("user.home"), "OnlineMedicData", "uploads").toAbsolutePath().normalize();
    }
}
