package com.dicajakrye.upload;

import static org.assertj.core.api.Assertions.assertThat;

import com.drew.metadata.Metadata;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusMakernoteDirectory;
import org.junit.jupiter.api.Test;

/** 캐논 내부 기종 번호 읽기 (CAMERAS.md 6장) */
class ExifReaderTest {

    @Test
    void 캐논_MakerNote의_Model_ID를_읽는다() {
        Metadata md = new Metadata();
        CanonMakernoteDirectory canon = new CanonMakernoteDirectory();
        canon.setLong(CanonMakernoteDirectory.TAG_MODEL_ID, 0x02570000L);
        md.addDirectory(canon);
        assertThat(ExifReader.makerModelId(md)).isEqualTo(0x02570000L);
    }

    @Test
    void 캐논_MakerNote가_없거나_번호가_없으면_null() {
        assertThat(ExifReader.makerModelId(new Metadata())).isNull(); // 편집 프로그램을 거친 사진

        Metadata noId = new Metadata();
        noId.addDirectory(new CanonMakernoteDirectory());
        assertThat(ExifReader.makerModelId(noId)).isNull();

        Metadata olympus = new Metadata(); // 다른 제조사는 읽지 않는다
        olympus.addDirectory(new OlympusMakernoteDirectory());
        assertThat(ExifReader.makerModelId(olympus)).isNull();
    }
}
