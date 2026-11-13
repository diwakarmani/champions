package com.propertyapp.util;

import java.util.Optional;

/**
 * Identifies an image by its first bytes, never by the client's file name or Content-Type.
 * Uploads are served from /uploads/ on the API origin, so a file named {@code x.svg} or
 * {@code x.html} sent as {@code image/png} would otherwise be stored and served as script.
 */
public final class ImageSniffer {

    private ImageSniffer() {
    }

    /** The extension to store the file under, or empty when the bytes are not a supported raster image. */
    public static Optional<String> extensionFor(byte[] head) {
        if (head == null) return Optional.empty();
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) return Optional.of(".jpg");
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return Optional.of(".png");
        if (startsWith(head, 'G', 'I', 'F', '8')) return Optional.of(".gif");
        if (head.length >= 12 && startsWith(head, 'R', 'I', 'F', 'F')
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return Optional.of(".webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}
