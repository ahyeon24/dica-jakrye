package com.dicajakrye.upload;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.jpeg.xmp.JpegXmpRewriter;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

/** 업로드 테스트용 JPEG: 실제 디카 사진처럼 EXIF(기종·노출·GPS)를 넣어 만든다 */
public final class UploadTestImages {

    private UploadTestImages() {
    }

    /** 왼쪽 위 1/4은 빨강, 나머지는 파랑. 회전 확인용 */
    public static byte[] plainJpeg(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height / 2);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** IXUS 30처럼 보이는 사진. orientation 6 = 세로로 찍음. withGps면 위치 정보와 XMP도 넣는다 */
    public static byte[] digicamJpeg(int width, int height, String make, String model, int orientation,
                                     boolean withGps) {
        try {
            TiffOutputSet set = new TiffOutputSet();
            TiffOutputDirectory root = set.getOrCreateRootDirectory();
            root.add(TiffTagConstants.TIFF_TAG_MAKE, make);
            root.add(TiffTagConstants.TIFF_TAG_MODEL, model);
            root.add(TiffTagConstants.TIFF_TAG_ORIENTATION, (short) orientation);
            TiffOutputDirectory exif = set.getOrCreateExifDirectory();
            exif.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, "2006:05:24 14:10:05");
            exif.add(ExifTagConstants.EXIF_TAG_FLASH, (short) 1);
            exif.add(ExifTagConstants.EXIF_TAG_FNUMBER, RationalNumber.valueOf(2.8));
            exif.add(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME, new RationalNumber(1, 60));
            exif.add(ExifTagConstants.EXIF_TAG_ISO, (short) 100);
            exif.add(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH, new RationalNumber(58, 10));
            if (withGps) {
                set.setGpsInDegrees(127.0276, 37.4979);
            }
            byte[] jpeg = plainJpeg(width, height);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                new ExifRewriter().updateExifMetadataLossless(jpeg, out, set);
                jpeg = out.toByteArray();
            }
            if (withGps) {
                String xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                        + "<rdf:Description xmlns:exif=\"http://ns.adobe.com/exif/1.0/\" exif:GPSLatitude=\"37,29.874N\"/>"
                        + "</rdf:RDF></x:xmpmeta>";
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    new JpegXmpRewriter().updateXmpXml(jpeg, out, xmp);
                    jpeg = out.toByteArray();
                }
            }
            return jpeg;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
