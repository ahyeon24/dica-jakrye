package com.dicajakrye.upload;

import com.dicajakrye.common.ExifValues;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 업로드 사진의 EXIF를 읽는다 (metadata-extractor). 브라우저가 읽은 값은 미리보기용일 뿐이고 서버가 다시 읽어 판단한다.
 * 값 해석은 Commons 수집과 같은 규칙(ExifValues)을 쓴다.
 */
@Component
public class ExifReader {

    /** 제조사나 모델이 없으면 빈 값 (인스타·카톡 등에서 받은 파일) */
    public Optional<ExifInfo> read(byte[] jpeg) {
        Metadata metadata;
        try {
            metadata = JpegMetadataReader.readMetadata(new ByteArrayInputStream(jpeg));
        } catch (Exception e) {
            return Optional.empty();
        }
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        String make = clean(string(ifd0, ExifIFD0Directory.TAG_MAKE));
        String model = clean(string(ifd0, ExifIFD0Directory.TAG_MODEL));
        if (make == null || model == null) {
            return Optional.empty();
        }
        Integer orientation = ExifValues.integer(string(ifd0, ExifIFD0Directory.TAG_ORIENTATION));
        return Optional.of(new ExifInfo(
                make, model,
                ExifValues.flash(string(sub, ExifSubIFDDirectory.TAG_FLASH)),
                ExifValues.takenAt(string(sub, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)),
                ExifValues.decimal(string(sub, ExifSubIFDDirectory.TAG_FNUMBER), 1),
                ExifValues.exposure(string(sub, ExifSubIFDDirectory.TAG_EXPOSURE_TIME)),
                ExifValues.integer(string(sub, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)),
                ExifValues.decimal(string(sub, ExifSubIFDDirectory.TAG_FOCAL_LENGTH), 1),
                orientation == null ? 1 : orientation));
    }

    private static String string(Directory dir, int tag) {
        return dir == null ? null : dir.getString(tag);
    }

    /** 옛 디카 EXIF 끝의 NUL과 공백을 지운다. DB 컬럼 길이(100)에 맞춘다 */
    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String c = s.replace("\0", "").strip();
        if (c.isEmpty()) {
            return null;
        }
        return c.length() > 100 ? c.substring(0, 100) : c;
    }
}
