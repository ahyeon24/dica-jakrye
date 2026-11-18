package com.dicajakrye.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * EXIF 값을 DB에 저장할 형태로 바꾼다. Commons 수집과 사용자 업로드가 같은 규칙을 쓴다.
 * 값이 이상하면 예외 대신 null (알 수 없음)로 둔다.
 */
public final class ExifValues {

    private static final DateTimeFormatter EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    private ExifValues() {
    }

    /** EXIF Flash: 비트0이 1이면 발광 */
    public static Boolean flash(String v) {
        Integer n = integer(v);
        return n == null ? null : (n & 1) == 1;
    }

    public static LocalDateTime takenAt(String v) {
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
    public static BigDecimal decimal(String v, int scale) {
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
    public static String exposure(String v) {
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

    public static Integer integer(String v) {
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
