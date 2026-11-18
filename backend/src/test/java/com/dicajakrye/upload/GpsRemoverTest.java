package com.dicajakrye.upload;

import static org.assertj.core.api.Assertions.assertThat;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GpsRemoverTest {

    final GpsRemover remover = new GpsRemover();
    final ExifReader reader = new ExifReader();

    @Test
    void 원본에서_GPS와_XMP만_지우고_나머지_EXIF는_남긴다() throws Exception {
        byte[] src = UploadTestImages.digicamJpeg(400, 300, "Canon", "Canon DIGITAL IXUS 30", 1, true);
        Metadata before = JpegMetadataReader.readMetadata(new ByteArrayInputStream(src));
        assertThat(before.getFirstDirectoryOfType(GpsDirectory.class)).isNotNull(); // 테스트 사진에 GPS가 들어 있는지 먼저 확인
        assertThat(before.getFirstDirectoryOfType(XmpDirectory.class)).isNotNull();

        byte[] stripped = remover.strip(src);

        Metadata after = JpegMetadataReader.readMetadata(new ByteArrayInputStream(stripped));
        assertThat(after.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
        assertThat(after.getFirstDirectoryOfType(XmpDirectory.class)).isNull();
        assertThat(after.getFirstDirectoryOfType(ExifIFD0Directory.class).getString(ExifIFD0Directory.TAG_MODEL))
                .isEqualTo("Canon DIGITAL IXUS 30");
        assertThat(reader.read(stripped)).get().extracting(ExifInfo::takenAt)
                .isEqualTo(LocalDateTime.of(2006, 5, 24, 14, 10, 5));
    }

    @Test
    void GPS가_없으면_그대로() {
        byte[] src = UploadTestImages.digicamJpeg(400, 300, "Canon", "Canon DIGITAL IXUS 30", 1, false);
        assertThat(remover.strip(src)).isEqualTo(src);
    }

    @Test
    void 촬영_정보를_읽는다() {
        byte[] src = UploadTestImages.digicamJpeg(400, 300, "Canon", "Canon DIGITAL IXUS 30", 6, false);
        ExifInfo info = reader.read(src).orElseThrow();
        assertThat(info.makeRaw()).isEqualTo("Canon");
        assertThat(info.modelRaw()).isEqualTo("Canon DIGITAL IXUS 30");
        assertThat(info.flashFired()).isTrue();
        assertThat(info.fNumber()).isEqualByComparingTo(new BigDecimal("2.8"));
        assertThat(info.exposureTime()).isEqualTo("1/60");
        assertThat(info.iso()).isEqualTo(100);
        assertThat(info.focalLength()).isEqualByComparingTo(new BigDecimal("5.8"));
        assertThat(info.orientation()).isEqualTo(6);
    }

    @Test
    void 제조사나_모델이_없으면_빈_값() {
        assertThat(reader.read(UploadTestImages.plainJpeg(100, 100))).isEmpty();
        assertThat(reader.read(new byte[]{1, 2, 3})).isEmpty();
    }
}
