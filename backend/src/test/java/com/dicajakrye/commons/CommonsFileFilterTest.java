package com.dicajakrye.commons;

import static org.assertj.core.api.Assertions.assertThat;

import com.dicajakrye.commons.CommonsFileFilter.Accepted;
import com.dicajakrye.commons.CommonsFileFilter.CameraRule;
import com.dicajakrye.commons.CommonsFileFilter.Reject;
import com.dicajakrye.commons.CommonsFileFilter.Rejected;
import com.dicajakrye.commons.CommonsFileFilter.Result;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommonsFileFilterTest {

    static final CameraRule IXUS30 = new CameraRule(
            Set.of(CameraRule.key("Canon", "Canon DIGITAL IXUS 30"), CameraRule.key("Canon", "Canon PowerShot SD200")),
            2048, 400, 40_000_000L);

    static CommonsFile file(String mime, String license, String artist, int w, int h, Map<String, String> meta) {
        return new CommonsFile(1L, "File:a.jpg", "https://commons.wikimedia.org/wiki/File:a.jpg", mime, w, h,
                "https://upload.wikimedia.org/a.jpg", artist, license, null, meta);
    }

    static Map<String, String> exif(String make, String model) {
        Map<String, String> m = new HashMap<>();
        m.put("Make", make);
        m.put("Model", model);
        return m;
    }

    static Result check(CommonsFile f) {
        return CommonsFileFilter.check(f, IXUS30);
    }

    @Test
    void 조건을_모두_통과하면_저장할_값을_뽑는다() {
        Map<String, String> m = exif("Canon", "Canon PowerShot SD200\u0000");
        m.put("Flash", "25");
        m.put("DateTimeOriginal", "2006:07:21 14:03:00");
        m.put("FNumber", "28/10");
        m.put("ExposureTime", "1/60");
        m.put("ISOSpeedRatings", "100");
        m.put("FocalLength", "5.8");
        Result r = check(file("image/jpeg", "CC BY-SA 2.0", "<a href=\"//x\">Joe &amp; Jones</a>", 2048, 1536, m));

        assertThat(r).isInstanceOf(Accepted.class);
        var c = ((Accepted) r).candidate();
        assertThat(c.author()).isEqualTo("Joe & Jones"); // HTML 제거
        assertThat(c.exifModelRaw()).isEqualTo("Canon PowerShot SD200"); // NUL 제거
        assertThat(c.flashFired()).isTrue();
        assertThat(c.takenAt()).isEqualTo(LocalDateTime.of(2006, 7, 21, 14, 3));
        assertThat(c.fNumber()).isEqualByComparingTo(new BigDecimal("2.8"));
        assertThat(c.exposureTime()).isEqualTo("1/60");
        assertThat(c.iso()).isEqualTo(100);
        assertThat(c.focalLength()).isEqualByComparingTo(new BigDecimal("5.8"));
        assertThat(c.isOriginalSize()).isTrue();
    }

    @Test
    void 허용하지_않는_라이선스는_뺀다() {
        Map<String, String> m = exif("Canon", "Canon DIGITAL IXUS 30");
        assertThat(check(file("image/jpeg", "GFDL", "Joe", 2048, 1536, m))).isEqualTo(new Rejected(Reject.LICENSE));
        assertThat(check(file("image/jpeg", null, "Joe", 2048, 1536, m))).isEqualTo(new Rejected(Reject.LICENSE));
        assertThat(check(file("image/jpeg", "Public domain", "Joe", 2048, 1536, m))).isInstanceOf(Accepted.class);
        assertThat(check(file("image/jpeg", "CC0", "Joe", 2048, 1536, m))).isInstanceOf(Accepted.class);
        assertThat(check(file("image/jpeg", "CC BY 4.0", "Joe", 2048, 1536, m))).isInstanceOf(Accepted.class);
    }

    @Test
    void 분류에_섞여_있는_다른_카메라_사진은_뺀다() {
        Result r = check(file("image/jpeg", "CC BY 2.0", "Joe", 2048, 1536, exif("Canon", "Canon IXY DIGITAL 40")));
        assertThat(r).isEqualTo(new Rejected(Reject.MODEL_MISMATCH));
    }

    @Test
    void 작가가_없거나_EXIF가_없거나_JPEG가_아니면_뺀다() {
        Map<String, String> m = exif("Canon", "Canon DIGITAL IXUS 30");
        assertThat(check(file("image/jpeg", "CC BY 2.0", "<span> </span>", 2048, 1536, m))).isEqualTo(new Rejected(Reject.NO_AUTHOR));
        assertThat(check(file("image/jpeg", "CC BY 2.0", "Joe", 2048, 1536, Map.of()))).isEqualTo(new Rejected(Reject.NO_EXIF_MODEL));
        assertThat(check(file("image/png", "CC BY 2.0", "Joe", 2048, 1536, m))).isEqualTo(new Rejected(Reject.NOT_JPEG));
    }

    @Test
    void 작은_사진은_받되_원본_크기가_아니라고_표시하고_너무_작으면_뺀다() {
        Map<String, String> m = exif("Canon", "Canon DIGITAL IXUS 30");
        Result small = check(file("image/jpeg", "CC BY 2.0", "Joe", 640, 480, m));
        assertThat(((Accepted) small).candidate().isOriginalSize()).isFalse();
        Result cropped = check(file("image/jpeg", "CC BY 2.0", "Joe", 1980, 1400, m)); // 95% 이상이면 원본으로 봄
        assertThat(((Accepted) cropped).candidate().isOriginalSize()).isTrue();
        assertThat(check(file("image/jpeg", "CC BY 2.0", "Joe", 399, 300, m))).isEqualTo(new Rejected(Reject.TOO_SMALL));
        assertThat(check(file("image/jpeg", "CC BY 2.0", "Joe", 8000, 6000, m))).isEqualTo(new Rejected(Reject.TOO_MANY_PIXELS));
    }

    @Test
    void 숫자_형식이_이상해도_죽지_않고_null() {
        assertThat(CommonsFileFilter.decimal("1/0", 1)).isNull();
        assertThat(CommonsFileFilter.decimal("abc", 1)).isNull();
        assertThat(CommonsFileFilter.exposure("0.0166667")).isEqualTo("1/60");
        assertThat(CommonsFileFilter.flash("16")).isFalse(); // 강제 발광 끔
        assertThat(CommonsFileFilter.flash(null)).isNull();
        assertThat(CommonsFileFilter.takenAt("0000:00:00 00:00:00")).isNull();
    }
}
