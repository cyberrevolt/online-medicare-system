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

/** Stores return verification photos outside the public web directory. */
public final class ReturnPhotoStorage {
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public String store(Part part) throws IOException {
        if (part == null || part.getSize() == 0) throw new IllegalArgumentException("Choose a product photo to upload.");
        if (part.getSize() > MAX_BYTES) throw new IllegalArgumentException("Choose a product photo under 5 MB.");
        byte[] bytes;
        try (var input = part.getInputStream()) { bytes = input.readNBytes((int) MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Choose a product photo under 5 MB.");

        String extension;
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && (bytes[4] & 0xff) == 0x0d && (bytes[5] & 0xff) == 0x0a && (bytes[6] & 0xff) == 0x1a && (bytes[7] & 0xff) == 0x0a) extension = ".png";
        else if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) extension = ".jpg";
        else throw new IllegalArgumentException("Upload a product photo as JPEG or PNG.");

        BufferedImage image;
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) { image = ImageIO.read(input); }
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 6000 || image.getHeight() > 6000
                || (long) image.getWidth() * image.getHeight() > 20_000_000L)
            throw new IllegalArgumentException("Upload a valid JPEG or PNG product photo (maximum 20 megapixels).");

        String fileName = UUID.randomUUID() + extension;
        Path target = directory().resolve(fileName);
        Files.createDirectories(target.getParent());
        try { Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
        catch (IOException e) { Files.deleteIfExists(target); throw e; }
        return fileName;
    }

    public Path resolve(String fileName) {
        if (fileName == null || !fileName.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) return null;
        Path root = directory().toAbsolutePath().normalize();
        Path file = root.resolve(fileName).normalize();
        return file.startsWith(root) && Files.isRegularFile(file) ? file : null;
    }

    public void delete(String fileName) {
        Path file = resolve(fileName);
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (IOException ignored) { }
    }

    private Path directory() {
        String configured = System.getProperty("RETURN_PHOTO_UPLOAD_DIR");
        if (configured == null || configured.isBlank()) configured = System.getenv("RETURN_PHOTO_UPLOAD_DIR");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        return Path.of(System.getProperty("user.home"), "OnlineMedicData", "return-photos").toAbsolutePath().normalize();
    }
}
