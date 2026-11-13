package com.propertyapp.storage;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.propertyapp.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR-0016 pipeline: type by bytes, size limits, upright, metadata removed, two sizes. */
class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor(50_000_000);

    @Test
    void cameraMetadataIncludingGpsIsRemoved() throws Exception {
        byte[] input = TestImages.jpegWithExif(40, 20, 1);
        Metadata before = ImageMetadataReader.readMetadata(new ByteArrayInputStream(input));
        assertThat(before.getFirstDirectoryOfType(GpsDirectory.class)).as("fixture carries GPS").isNotNull();

        ImageProcessor.Result result = processor.process(input);

        for (byte[] out : new byte[][]{result.full(), result.card()}) {
            Metadata after = ImageMetadataReader.readMetadata(new ByteArrayInputStream(out));
            assertThat(after.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
            assertThat(after.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
        }
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo(".jpg");
    }

    @Test
    void aPhoneShotTakenSidewaysIsStoredUpright() throws Exception {
        // Orientation 6: the viewer must turn the stored picture 90 degrees clockwise.
        ImageProcessor.Result result = processor.process(TestImages.jpegWithExif(80, 40, 6));
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result.full()));
        assertThat(out.getWidth()).isEqualTo(40);
        assertThat(out.getHeight()).isEqualTo(80);
        // The green top-left corner ends up top-right after a clockwise turn.
        assertThat(isGreen(out.getRGB(out.getWidth() - 2, 1))).isTrue();
        assertThat(isGreen(out.getRGB(1, 1))).isFalse();
    }

    @Test
    void everyOrientationGivesTheRightShape() {
        for (int o = 1; o <= 8; o++) {
            BufferedImage turned = ImageProcessor.applyOrientation(TestImages.picture(30, 10, false), o);
            if (o >= 5) {
                assertThat(turned.getWidth()).as("orientation %d", o).isEqualTo(10);
                assertThat(turned.getHeight()).as("orientation %d", o).isEqualTo(30);
            } else {
                assertThat(turned.getWidth()).as("orientation %d", o).isEqualTo(30);
            }
        }
    }

    @Test
    void largePhotosGetAFullAndACardSize() throws Exception {
        ImageProcessor.Result result = processor.process(TestImages.jpeg(3000, 1500));
        BufferedImage full = ImageIO.read(new ByteArrayInputStream(result.full()));
        BufferedImage card = ImageIO.read(new ByteArrayInputStream(result.card()));
        assertThat(full.getWidth()).isEqualTo(ImageProcessor.FULL_MAX);
        assertThat(full.getHeight()).isEqualTo(1024);
        assertThat(card.getWidth()).isEqualTo(ImageProcessor.CARD_MAX);
        assertThat(card.getHeight()).isEqualTo(400);
    }

    @Test
    void hugePhotosAreDecodedSubsampledButStillComeOutFullSize() throws Exception {
        assertThat(ImageProcessor.subsampling(2048, 1000)).isEqualTo(1);
        assertThat(ImageProcessor.subsampling(4095, 3000)).isEqualTo(1);
        assertThat(ImageProcessor.subsampling(8000, 6000)).isEqualTo(3);   // decoded 2667 x 2000, about 21 MB
        assertThat(ImageProcessor.subsampling(6000, 8000)).isEqualTo(3);
        assertThat(ImageProcessor.subsampling(12000, 9000)).isEqualTo(5);
        ImageProcessor.Result result = processor.process(TestImages.jpeg(8000, 6000));
        BufferedImage full = ImageIO.read(new ByteArrayInputStream(result.full()));
        assertThat(full.getWidth()).isEqualTo(ImageProcessor.FULL_MAX);
        assertThat(full.getHeight()).isEqualTo(1536);
    }

    @Test
    void sidewaysHugePhotoIsSubsampledAndStillTurnedUpright() throws Exception {
        ImageProcessor.Result result = processor.process(TestImages.jpegWithExif(6000, 3000, 6));
        BufferedImage full = ImageIO.read(new ByteArrayInputStream(result.full()));
        assertThat(full.getWidth()).isEqualTo(1024);
        assertThat(full.getHeight()).isEqualTo(ImageProcessor.FULL_MAX);
    }

    @Test
    void smallPhotosAreNotEnlarged() throws Exception {
        ImageProcessor.Result result = processor.process(TestImages.jpeg(300, 200));
        assertThat(ImageIO.read(new ByteArrayInputStream(result.full())).getWidth()).isEqualTo(300);
        assertThat(ImageIO.read(new ByteArrayInputStream(result.card())).getWidth()).isEqualTo(300);
    }

    @Test
    void transparencyKeepsPngAndOpaquePngBecomesJpeg() {
        assertThat(processor.process(TestImages.encode(TestImages.picture(40, 40, true), "png")).contentType())
                .isEqualTo("image/png");
        assertThat(processor.process(TestImages.encode(TestImages.picture(40, 40, false), "png")).contentType())
                .isEqualTo("image/jpeg");
    }

    @Test
    void gifAndWebpAreAccepted() throws Exception {
        assertThat(processor.process(TestImages.encode(TestImages.picture(40, 40, false), "gif")).width()).isEqualTo(40);
        ImageProcessor.Result webp = processor.process(TestImages.resource("sample-64x32.webp"));
        assertThat(webp.width()).isEqualTo(64);
        assertThat(webp.height()).isEqualTo(32);
        assertThat(ImageIO.read(new ByteArrayInputStream(webp.full()))).isNotNull();
    }

    @Test
    void scriptAndHtmlAreRefusedWhateverTheyClaim() {
        for (String text : new String[]{"<svg onload=alert(1)>", "<html><script>x</script>", ""}) {
            assertThatThrownBy(() -> processor.process(text.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void aTruncatedImageIsRefusedNotA500() {
        byte[] jpeg = TestImages.jpeg(100, 100);
        byte[] cut = java.util.Arrays.copyOf(jpeg, 40);
        assertThatThrownBy(() -> processor.process(cut)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void picturesOverThePixelLimitAreRefusedBeforeDecoding() {
        ImageProcessor small = new ImageProcessor(1_000);
        assertThatThrownBy(() -> small.process(TestImages.jpeg(40, 40)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("too large");
        // A PNG header that claims 100,000 x 100,000 px with no pixel data behind it.
        assertThatThrownBy(() -> processor.process(pngHeaderOnly(100_000, 100_000)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("too large");
    }

    private static byte[] pngHeaderOnly(int w, int h) {
        var ihdr = java.nio.ByteBuffer.allocate(13).putInt(w).putInt(h).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array();
        var crc = new java.util.zip.CRC32();
        crc.update("IHDR".getBytes(StandardCharsets.US_ASCII));
        crc.update(ihdr);
        return java.nio.ByteBuffer.allocate(8 + 4 + 4 + 13 + 4)
                .put(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
                .putInt(13).put("IHDR".getBytes(StandardCharsets.US_ASCII)).put(ihdr).putInt((int) crc.getValue())
                .array();
    }

    private static boolean isGreen(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return g > 150 && r < 100 && b < 100;
    }
}
