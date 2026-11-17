package com.dicajakrye.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * 보기용(긴 변 1600px)·썸네일(긴 변 640px) JPEG을 만든다. (docs/ARCHITECTURE.md 3.1)
 * - 새로 인코딩하므로 EXIF(GPS 포함)가 모두 빠진다. 촬영 정보는 DB에서 따로 보여준다.
 * - 원래보다 크게 늘리지 않는다.
 * - 이미지를 메모리에 올리기 전에 헤더에서 크기를 먼저 읽고, 픽셀이 너무 많으면 거부한다. (이미지 폭탄 방지)
 */
@Component
public class ImageProcessor {

    public static final int DISPLAY_LONG_SIDE = 1600;
    public static final int THUMBNAIL_LONG_SIDE = 640;
    public static final long MAX_PIXELS = 40_000_000L;
    private static final double JPEG_QUALITY = 0.85;

    public record Processed(byte[] display, byte[] thumbnail, int sourceWidth, int sourceHeight) {
    }

    public static class ImageRejectedException extends RuntimeException {
        public ImageRejectedException(String message) {
            super(message);
        }
    }

    public Processed process(byte[] source) {
        int[] size = readSize(source);
        if ((long) size[0] * size[1] > MAX_PIXELS) {
            throw new ImageRejectedException("픽셀 수 초과: " + size[0] + "x" + size[1]);
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(source));
        } catch (IOException e) {
            throw new ImageRejectedException("이미지를 읽을 수 없음: " + e.getMessage());
        }
        if (image == null) {
            throw new ImageRejectedException("지원하지 않는 이미지 형식");
        }
        return new Processed(
                resize(image, DISPLAY_LONG_SIDE),
                resize(image, THUMBNAIL_LONG_SIDE),
                image.getWidth(), image.getHeight());
    }

    /** 헤더만 읽어서 가로·세로를 구한다. 픽셀 데이터는 읽지 않는다 */
    static int[] readSize(byte[] source) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (in == null) {
                throw new ImageRejectedException("지원하지 않는 이미지 형식");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new ImageRejectedException("지원하지 않는 이미지 형식");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ImageRejectedException("이미지 크기를 읽을 수 없음: " + e.getMessage());
        }
    }

    private static byte[] resize(BufferedImage image, int maxLongSide) {
        int longSide = Math.max(image.getWidth(), image.getHeight());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var builder = Thumbnails.of(image).imageType(BufferedImage.TYPE_INT_RGB);
            if (longSide > maxLongSide) {
                builder = builder.size(maxLongSide, maxLongSide); // 비율 유지하며 긴 변을 맞춤
            } else {
                builder = builder.scale(1.0); // 늘리지 않는다
            }
            builder.outputFormat("jpg").outputQuality(JPEG_QUALITY).toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ImageRejectedException("이미지 변환 실패: " + e.getMessage());
        }
    }
}
