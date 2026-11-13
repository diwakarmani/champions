package com.propertyapp.storage;

import com.propertyapp.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageKeysAndRefsTest {

    @TempDir Path dir;

    private static final String KEY = "images/123e4567-e89b-12d3-a456-426614174000.jpg";

    @Test
    void keysAndCardKeys() {
        assertThat(StorageKeys.isKey(KEY)).isTrue();
        assertThat(StorageKeys.cardKeyFor(KEY)).isEqualTo("images/123e4567-e89b-12d3-a456-426614174000_card.jpg");
        assertThat(StorageKeys.cardKeyFor(StorageKeys.cardKeyFor(KEY))).isNull();
        assertThat(StorageKeys.isKey("images/../../etc/passwd")).isFalse();
        assertThat(StorageKeys.isKey("https://cdn.example/images/x.jpg")).isFalse();
        assertThat(StorageKeys.cardKeyFor("https://images.unsplash.com/photo-1")).isNull();
    }

    @Test
    void urlsFromClientsBecomeKeysAndKeysBecomeUrls() {
        var s3Like = new FixedUrlStorage("https://img.champion.example/");
        ImageRefs refs = new ImageRefs(s3Like, "images.unsplash.com", "https://api.champion.example");

        assertThat(refs.toStored("https://img.champion.example/" + KEY)).isEqualTo(KEY);
        assertThat(refs.toPublicUrl(KEY)).isEqualTo("https://img.champion.example/" + KEY);
        // Stock photos and old local uploads on this server pass through both ways.
        assertThat(refs.toStored("https://images.unsplash.com/photo-1?w=800")).isEqualTo("https://images.unsplash.com/photo-1?w=800");
        assertThat(refs.toStored("https://api.champion.example/uploads/abc.jpg")).isEqualTo("https://api.champion.example/uploads/abc.jpg");
        assertThat(refs.toPublicUrl("https://api.champion.example/uploads/abc.jpg")).isEqualTo("https://api.champion.example/uploads/abc.jpg");
        // A key sent through the local base also maps (a request made while local storage was active).
        assertThat(refs.toStored("https://api.champion.example/uploads/" + KEY)).isEqualTo(KEY);
        assertThat(refs.toStored(null)).isNull();
        assertThat(refs.toStored(" ")).isEqualTo(" ");
    }

    @Test
    void photosOnOtherHostsAreRefused() {
        ImageRefs refs = new ImageRefs(new FixedUrlStorage("https://img.champion.example/"), "images.unsplash.com",
                "https://api.champion.example");
        for (String url : new String[]{
                "https://tracker.example/pixel.jpg",
                "http://images.unsplash.com/photo-1",                 // stock host, but not https
                "https://img.champion.example/../secret.jpg",
                "https://img.champion.example/images/not-a-uuid.jpg",
                "https://images.unsplash.com.evil.example/x.jpg",
                "https://api.champion.example/uploads/../../etc/passwd",
                "https://other.example/uploads/abc.jpg"}) {
            assertThatThrownBy(() -> refs.toStored(url)).as(url).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void localStorageStaysInsideItsFolder() {
        LocalDiskStorage local = new LocalDiskStorage(dir.toString(), "http://localhost:8080/");
        assertThat(local.publicUrl(KEY)).isEqualTo("http://localhost:8080/uploads/" + KEY);
        assertThatThrownBy(() -> local.put("../outside.jpg", new byte[]{1}, "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> local.get("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializerFallsBackToTheStoredValueWithoutSpring() {
        ImageRefs.install(null);
        assertThat(ImageRefs.resolveCurrent(KEY)).isEqualTo(KEY);
    }

    /** Storage whose only job here is its public URL. */
    record FixedUrlStorage(String base) implements StorageService {
        public void put(String key, byte[] bytes, String contentType) { }
        public java.util.Optional<byte[]> get(String key) { return java.util.Optional.empty(); }
        public boolean exists(String key) { return false; }
        public void delete(String key) { }
        public String publicUrl(String key) { return base + key; }
        public String kind() { return "fixed"; }
    }
}
