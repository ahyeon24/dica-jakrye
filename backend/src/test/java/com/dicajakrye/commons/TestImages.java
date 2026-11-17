package com.dicajakrye.commons;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** 테스트용 JPEG 만들기 */
final class TestImages {

    private TestImages() {
    }

    static byte[] jpeg(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 120, 80));
        g.fillRect(0, 0, width, height);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static int[] size(byte[] jpeg) {
        try {
            BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
            return new int[]{img.getWidth(), img.getHeight()};
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** JPEG 안에 EXIF(APP1 "Exif") 블록이 있는지 */
    static boolean hasExif(byte[] jpeg) {
        for (int i = 0; i + 4 < jpeg.length; i++) {
            if (jpeg[i] == 'E' && jpeg[i + 1] == 'x' && jpeg[i + 2] == 'i' && jpeg[i + 3] == 'f' && jpeg[i + 4] == 0) {
                return true;
            }
        }
        return false;
    }
}
