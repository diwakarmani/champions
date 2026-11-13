package com.propertyapp.storage;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.util.ImageSniffer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Turns an uploaded photo into what is stored (ADR-0016): checks the type by its bytes, refuses
 * decompression bombs before decoding, turns the picture upright from its EXIF orientation, then
 * re-encodes it. Re-encoding writes no metadata, so camera EXIF (GPS location, device serials) is
 * gone. Two sizes: full view (longest side at most 2048 px) and card (at most 800 px).
 */
public class ImageProcessor {

    public static final int FULL_MAX = 2048;
    public static final int CARD_MAX = 800;
    static final float JPEG_QUALITY = 0.85f;

    public static final String NOT_AN_IMAGE = "Only image files (JPEG, PNG, GIF, WebP) are allowed";

    private final long maxPixels;

    public ImageProcessor(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    /** Full-size and card-size bytes, both JPEG, or both PNG when the picture has transparency. */
    public record Result(byte[] full, byte[] card, String extension, String contentType, int width, int height) {
    }

    public Result process(byte[] input) {
        if (input == null || input.length == 0) throw new BadRequestException("File is empty");
        if (ImageSniffer.extensionFor(Arrays.copyOf(input, Math.min(12, input.length))).isEmpty()) {
            throw new BadRequestException(NOT_AN_IMAGE);
        }
        BufferedImage decoded = decode(input);
        BufferedImage upright = applyOrientation(decoded, orientation(input));

        BufferedImage full = scale(upright, FULL_MAX);
        boolean alpha = hasTransparency(full);
        BufferedImage card = scale(full, CARD_MAX);
        String ext = alpha ? ".png" : ".jpg";
        return new Result(encode(full, alpha), encode(card, alpha), ext,
                alpha ? "image/png" : "image/jpeg", full.getWidth(), full.getHeight());
    }

    private BufferedImage decode(byte[] input) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new BadRequestException(NOT_AN_IMAGE);
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                // Read from the header only: a 1 KB file can claim 100,000 x 100,000 px.
                if (width <= 0 || height <= 0 || width * height > maxPixels) {
                    throw new BadRequestException("This photo is too large. Use one under "
                            + (maxPixels / 1_000_000) + " megapixels.");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                // Read every n-th pixel of very large photos: a 48 MP picture decoded whole is about
                // 190 MB on a 512 MB server (S6 review). The result stays at least FULL_MAX wide.
                int step = subsampling(width, height);
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) throw new BadRequestException(NOT_AN_IMAGE);
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bad) throw bad;
            throw new BadRequestException("We could not read this image. Try saving it as JPEG or PNG.");
        }
    }

    /** Pixel step for decoding: the largest whole step that keeps the longest side at or above FULL_MAX. */
    static int subsampling(long width, long height) {
        return (int) Math.max(1, Math.max(width, height) / FULL_MAX);
    }

    /** EXIF orientation 1 to 8; 1 (as stored) when absent or unreadable. */
    static int orientation(byte[] input) {
        try {
            var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(input));
            var dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int value = dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                return value >= 1 && value <= 8 ? value : 1;
            }
        } catch (Exception ignored) {
            // No EXIF, or broken EXIF: keep the picture as stored.
        }
        return 1;
    }

    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { return src; }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, argb(src));
        Graphics2D g = out.createGraphics();
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    /** Fits the longest side into {@code max}, halving in steps so small sizes stay sharp. */
    static BufferedImage scale(BufferedImage src, int max) {
        int type = argb(src);
        int w = src.getWidth();
        int h = src.getHeight();
        double ratio = Math.min(1.0, (double) max / Math.max(w, h));
        int targetW = Math.max(1, (int) Math.round(w * ratio));
        int targetH = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage current = src;
        if (ratio == 1.0 && src.getType() == type) return src;
        do {
            int nextW = Math.max(targetW, current.getWidth() / 2);
            int nextH = Math.max(targetH, current.getHeight() / 2);
            if (current.getWidth() <= targetW) { nextW = targetW; nextH = targetH; }
            BufferedImage next = new BufferedImage(nextW, nextH, type);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(current, 0, 0, nextW, nextH, null);
            g.dispose();
            current = next;
        } while (current.getWidth() != targetW || current.getHeight() != targetH);
        return current;
    }

    private static int argb(BufferedImage src) {
        return src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) return false;
        int[] row = new int[image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, row.length, 1, row, 0, row.length);
            for (int px : row) {
                if ((px >>> 24) != 0xFF) return true;
            }
        }
        return false;
    }

    private static byte[] encode(BufferedImage image, boolean png) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (png) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            }
            BufferedImage rgb = image;
            if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                g.drawImage(image, 0, 0, null);
                g.dispose();
            }
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode image", e);
        }
    }
}
