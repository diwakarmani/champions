package com.propertyapp.controller;

import com.propertyapp.exception.BadRequestException;
import com.propertyapp.storage.ImageProcessor;
import com.propertyapp.storage.ImageUploadService;
import com.propertyapp.storage.LocalDiskStorage;
import com.propertyapp.storage.StorageService;
import com.propertyapp.storage.TestImages;
import com.propertyapp.util.ImageSniffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S11 review: uploads were stored under the client's file extension and trusted its Content-Type. */
class FileUploadControllerTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8);

    @TempDir Path dir;

    FileUploadController controller() {
        StorageService storage = new LocalDiskStorage(dir.toString(), "http://api.test");
        return new FileUploadController(new ImageUploadService(new ImageProcessor(50_000_000), storage), storage);
    }

    @Test
    void scriptDisguisedAsAPngIsRefusedAndNothingIsStored() throws Exception {
        for (String name : new String[] {"x.svg", "x.html", "x.png"}) {
            // The processor's 400 reaches the client through GlobalExceptionHandler.
            assertThatThrownBy(() -> controller().uploadImage(new MockMultipartFile("file", name, "image/png", SVG)))
                    .isInstanceOf(BadRequestException.class);
        }
        try (var files = Files.walk(dir)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void realImageIsReEncodedUnderANewNameNotTheClientsName() throws Exception {
        byte[] png = TestImages.encode(TestImages.picture(40, 30, false), "png");
        var res = controller().uploadImage(new MockMultipartFile("file", "evil.html", "image/png", png));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = res.getBody().getData();
        // An opaque PNG is stored as JPEG (ADR-0016), with its card size next to it.
        assertThat(url).matches("http://api\\.test/uploads/images/[0-9a-f-]{36}\\.jpg").doesNotContain("evil");
        try (var files = Files.walk(dir)) {
            assertThat(files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).sorted())
                    .hasSize(2).anySatisfy(n -> assertThat(n).endsWith("_card.jpg"));
        }
    }

    @Test
    void storageOutageIsA503NotA500() {
        StorageService down = new LocalDiskStorage(dir.toString(), "http://api.test") {
            @Override
            public void put(String key, byte[] bytes, String contentType) {
                throw new java.io.UncheckedIOException(new java.net.SocketException("Unexpected end of file from server"));
            }
        };
        var c = new FileUploadController(new ImageUploadService(new ImageProcessor(50_000_000), down), down);
        var res = c.uploadImage(new MockMultipartFile("file", "a.jpg", "image/jpeg", TestImages.jpeg(20, 20)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().getMessage()).contains("try again");
    }

    @Test
    void declaredTypeOutsideTheAllowListIsStillRefused() {
        var res = controller().uploadImage(new MockMultipartFile("file", "x.png", "image/svg+xml", PNG));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void sniffer() {
        assertThat(ImageSniffer.extensionFor(PNG)).contains(".png");
        assertThat(ImageSniffer.extensionFor(JPEG)).contains(".jpg");
        assertThat(ImageSniffer.extensionFor(WEBP)).contains(".webp");
        assertThat(ImageSniffer.extensionFor("GIF89a".getBytes(StandardCharsets.US_ASCII))).contains(".gif");
        assertThat(ImageSniffer.extensionFor(SVG)).isEmpty();
        assertThat(ImageSniffer.extensionFor(new byte[] {(byte) 0x89, 'P'})).isEmpty();
        assertThat(ImageSniffer.extensionFor("RIFF0000WAVE".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageSniffer.extensionFor(null)).isEmpty();
    }
}
