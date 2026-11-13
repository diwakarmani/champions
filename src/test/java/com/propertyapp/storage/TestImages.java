package com.propertyapp.storage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/** Builds small images in memory, including a JPEG carrying EXIF orientation and a GPS block. */
public final class TestImages {

    private TestImages() {
    }

    /** Left half red, right half blue; the top-left 4x4 is green so rotation can be checked. */
    public static BufferedImage picture(int w, int h, boolean alpha) {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        if (alpha) {
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(0, 0, w, h);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
        }
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, alpha ? h / 2 : h);
        g.setColor(Color.BLUE);
        g.fillRect(w / 2, 0, w - w / 2, alpha ? h / 2 : h);
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, Math.max(1, w / 8), Math.max(1, h / 8));
        g.dispose();
        return img;
    }

    public static byte[] encode(BufferedImage img, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(img, format, out)) throw new IllegalStateException("No writer for " + format);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] jpeg(int w, int h) {
        return encode(picture(w, h, false), "jpeg");
    }

    /** A JPEG with an APP1 EXIF block: Orientation and a GPS latitude reference, as phones write. */
    public static byte[] jpegWithExif(int w, int h, int orientation) {
        byte[] jpeg = jpeg(w, h);
        ByteBuffer tiff = ByteBuffer.allocate(56);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) 2);                                           // IFD0, two entries
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38); // GPS IFD at 38
        tiff.putInt(0);
        tiff.putShort((short) 1);                                           // GPS IFD, one entry
        tiff.putShort((short) 0x0001).putShort((short) 2).putInt(2).put((byte) 'N').put((byte) 0).putShort((short) 0);
        tiff.putInt(0);
        byte[] tiffBytes = java.util.Arrays.copyOf(tiff.array(), tiff.position());
        byte[] header = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + header.length + tiffBytes.length;
        ByteBuffer out = ByteBuffer.allocate(jpeg.length + 2 + length);
        out.put(jpeg, 0, 2);                                                // SOI
        out.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length).put(header).put(tiffBytes);
        out.put(jpeg, 2, jpeg.length - 2);
        return out.array();
    }

    public static byte[] resource(String name) {
        try (var in = TestImages.class.getResourceAsStream("/images/" + name)) {
            if (in == null) throw new IllegalStateException("Missing test image " + name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
