package com.dicajakrye.upload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.jpeg.xmp.JpegXmpRewriter;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 비공개로 보관하는 원본에서 위치 정보를 지운다. 픽셀은 다시 인코딩하지 않는다. (ARCHITECTURE.md 3.1)
 * - EXIF의 GPS 영역을 뺀다. 나머지 EXIF(기종·노출 등)는 남긴다.
 * - XMP는 통째로 지운다. 편집 프로그램이 위치를 XMP에 적는 경우가 있다.
 * - EXIF 구조가 깨져서 GPS만 골라 지울 수 없으면 EXIF 전체를 지운다. (촬영 정보는 이미 DB에 있다)
 */
@Component
public class GpsRemover {

    private static final Logger log = LoggerFactory.getLogger(GpsRemover.class);

    public byte[] strip(byte[] jpeg) {
        byte[] noXmp = removeXmp(jpeg);
        try {
            return removeGps(noXmp);
        } catch (Exception e) {
            log.warn("GPS만 지우지 못해 EXIF 전체를 지움: {}", e.toString());
            return removeAllExif(noXmp);
        }
    }

    private static byte[] removeGps(byte[] jpeg) throws IOException {
        ImageMetadata metadata = Imaging.getMetadata(jpeg);
        if (!(metadata instanceof JpegImageMetadata jpegMeta) || jpegMeta.getExif() == null) {
            return jpeg; // EXIF가 없으면 GPS도 없다
        }
        TiffOutputSet original = jpegMeta.getExif().getOutputSet();
        if (original.getGpsDirectory() == null) {
            return jpeg;
        }
        // GPS 영역을 뺀 새 EXIF를 만든다
        TiffOutputSet withoutGps = new TiffOutputSet(original.byteOrder);
        for (TiffOutputDirectory dir : original.getDirectories()) {
            if (dir.getType() != TiffDirectoryConstants.DIRECTORY_TYPE_GPS) {
                withoutGps.addDirectory(dir);
            }
        }
        withoutGps.removeField(ExifTagConstants.EXIF_TAG_GPSINFO);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length)) {
            new ExifRewriter().updateExifMetadataLossless(jpeg, out, withoutGps);
            return out.toByteArray();
        }
    }

    private static byte[] removeXmp(byte[] jpeg) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length)) {
            new JpegXmpRewriter().removeXmpXml(jpeg, out);
            return out.toByteArray();
        } catch (Exception e) {
            return jpeg;
        }
    }

    private static byte[] removeAllExif(byte[] jpeg) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length)) {
            new ExifRewriter().removeExifMetadata(jpeg, out);
            return out.toByteArray();
        } catch (Exception e) {
            // 지울 수 없을 만큼 깨진 파일은 원본을 보관하지 않는다
            throw new IllegalStateException("원본 EXIF를 지우지 못함", e);
        }
    }
}
