package com.propertyapp.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/** Files under {@code app.upload.dir}, served by WebConfig at {@code <base-url>/uploads/}. Dev and tests only. */
public class LocalDiskStorage implements StorageService {

    private final Path root;
    private final String baseUrl;

    public LocalDiskStorage(String uploadDir, String baseUrl) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = stripSlash(baseUrl) + "/uploads/";
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + key, e);
        }
    }

    @Override
    public String publicUrl(String key) {
        return baseUrl + key;
    }

    @Override
    public String kind() {
        return "local";
    }

    /** Refuses keys that would leave the upload folder. */
    private Path resolve(String key) {
        Path file = root.resolve(key).normalize();
        if (key.isBlank() || !file.startsWith(root) || file.equals(root)) {
            throw new IllegalArgumentException("Bad storage key");
        }
        return file;
    }

    static String stripSlash(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }
}
