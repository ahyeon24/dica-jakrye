package com.dicajakrye.commons;

import static com.dicajakrye.common.ExifValues.decimal;
import static com.dicajakrye.common.ExifValues.exposure;
import static com.dicajakrye.common.ExifValues.flash;
import static com.dicajakrye.common.ExifValues.integer;
import static com.dicajakrye.common.ExifValues.takenAt;

import com.dicajakrye.common.TextNormalizer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;

/**
 * Commons 파일을 작례로 쓸 수 있는지 거르고, 저장할 값을 뽑는다. (docs/ARCHITECTURE.md 3.3)
 * 외부 값은 사용자 입력처럼 다룬다: 작가 정보의 HTML은 저장 전에 지운다. (docs/SECURITY.md 3장)
 */
public final class CommonsFileFilter {

    /** 허용 라이선스: 퍼블릭 도메인, CC0, CC BY, CC BY-SA (버전 무관) */
    private static final Pattern ALLOWED_LICENSE =
            Pattern.compile("^(public domain|pd|cc0|cc by(-sa)? [0-9.]+).*", Pattern.CASE_INSENSITIVE);

    public enum Reject { NOT_JPEG, LICENSE, NO_AUTHOR, NO_EXIF_MODEL, MODEL_MISMATCH, TOO_SMALL, TOO_MANY_PIXELS }

    /** 기종의 판별 기준 */
    public record CameraRule(Set<String> exifKeys, Integer nativeLongSide, int minLongSide, long maxPixels) {
        /** exifKeys 원소: 정규화한 "제조사|모델" */
        public static String key(String make, String model) {
            return TextNormalizer.exif(make) + "|" + TextNormalizer.exif(model);
        }
    }

    public record Candidate(CommonsFile file, String author, String exifMakeRaw, String exifModelRaw,
                            Boolean flashFired, LocalDateTime takenAt, BigDecimal fNumber, String exposureTime,
                            Integer iso, BigDecimal focalLength, Boolean isOriginalSize) {
    }

    public sealed interface Result permits Accepted, Rejected {
    }

    public record Accepted(Candidate candidate) implements Result {
    }

    public record Rejected(Reject reason) implements Result {
    }

    private CommonsFileFilter() {
    }

    public static Result check(CommonsFile f, CameraRule rule) {
        if (!"image/jpeg".equals(f.mime())) {
            return new Rejected(Reject.NOT_JPEG);
        }
        if (f.licenseName() == null || !ALLOWED_LICENSE.matcher(f.licenseName().strip()).matches()) {
            return new Rejected(Reject.LICENSE);
        }
        String author = plainText(f.artistHtml());
        if (author == null) {
            return new Rejected(Reject.NO_AUTHOR);
        }
        String make = f.metadata().get("Make");
        String model = f.metadata().get("Model");
        if (make == null || model == null) {
            return new Rejected(Reject.NO_EXIF_MODEL);
        }
        if (!rule.exifKeys().contains(CameraRule.key(make, model))) {
            return new Rejected(Reject.MODEL_MISMATCH); // Commons 분류만 믿지 않는다
        }
        int longSide = Math.max(f.width(), f.height());
        if (longSide < rule.minLongSide()) {
            return new Rejected(Reject.TOO_SMALL);
        }
        if ((long) f.width() * f.height() > rule.maxPixels()) {
            return new Rejected(Reject.TOO_MANY_PIXELS);
        }
        Boolean original = rule.nativeLongSide() == null ? null : longSide >= rule.nativeLongSide() * 0.95;
        Map<String, String> m = f.metadata();
        return new Accepted(new Candidate(f, author, clean(make), clean(model),
                flash(m.get("Flash")), takenAt(m.get("DateTimeOriginal")),
                decimal(m.get("FNumber"), 1), exposure(m.get("ExposureTime")),
                integer(m.get("ISOSpeedRatings")), decimal(m.get("FocalLength"), 1),
                original));
    }

    /** HTML 태그를 지우고 글자만. 비어 있으면 null. 300자까지 */
    static String plainText(String html) {
        if (html == null) {
            return null;
        }
        String text = Jsoup.parse(html).text().replaceAll("\\s+", " ").strip();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > 300 ? text.substring(0, 300) : text;
    }

    private static String clean(String s) {
        String c = s.replace("\u0000", "").strip();
        return c.length() > 100 ? c.substring(0, 100) : c;
    }
}
