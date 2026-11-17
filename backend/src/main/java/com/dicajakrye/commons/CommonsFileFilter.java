package com.dicajakrye.commons;

import com.dicajakrye.common.TextNormalizer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

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

    /** EXIF Flash: 비트0이 1이면 발광 */
    static Boolean flash(String v) {
        Integer n = integer(v);
        return n == null ? null : (n & 1) == 1;
    }

    static LocalDateTime takenAt(String v) {
        if (v == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(v.strip(), EXIF_DATE);
        } catch (RuntimeException e) {
            try {
                return LocalDateTime.parse(v.strip()); // ISO 형식으로 오는 경우
            } catch (RuntimeException e2) {
                return null;
            }
        }
    }

    /** "28/10", "2.8" 모두 처리 */
    static BigDecimal decimal(String v, int scale) {
        if (v == null) {
            return null;
        }
        try {
            String s = v.strip();
            BigDecimal d;
            if (s.contains("/")) {
                String[] p = s.split("/");
                BigDecimal den = new BigDecimal(p[1].strip());
                if (den.signum() == 0) {
                    return null;
                }
                d = new BigDecimal(p[0].strip()).divide(den, 6, RoundingMode.HALF_UP);
            } else {
                d = new BigDecimal(s);
            }
            if (d.signum() <= 0 || d.compareTo(BigDecimal.valueOf(999)) > 0) {
                return null;
            }
            return d.setScale(scale, RoundingMode.HALF_UP);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 셔터 속도는 "1/60" 같은 원문을 쓴다. 0.0166 같은 소수면 1/n으로 바꾼다 */
    static String exposure(String v) {
        if (v == null) {
            return null;
        }
        String s = v.strip();
        if (s.matches("\\d+/\\d+")) {
            return s;
        }
        BigDecimal d = decimal(s, 6);
        if (d == null) {
            return null;
        }
        if (d.compareTo(BigDecimal.ONE) >= 0) {
            return d.stripTrailingZeros().toPlainString();
        }
        return "1/" + BigDecimal.ONE.divide(d, 0, RoundingMode.HALF_UP).toPlainString();
    }

    static Integer integer(String v) {
        if (v == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(v.strip());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
