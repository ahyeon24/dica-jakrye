package com.dicajakrye.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 별칭·EXIF 정규화 규칙 (docs/ERD.md 4장).
 * 시드 생성기(backend/scripts/gen_seed_cameras.py)와 결과가 똑같아야 한다.
 * 두 쪽 모두 같은 예시로 테스트한다. (TextNormalizerTest)
 */
public final class TextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern SPACE_OR_HYPHEN = Pattern.compile("[\\s\\-]");

    private TextNormalizer() {
    }

    /** EXIF 제조사·모델: NUL 제거 → 앞뒤 공백 제거·연속 공백 하나로 → 소문자 */
    public static String exif(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replace("\u0000", "");
        s = WHITESPACE.matcher(s).replaceAll(" ").strip();
        return s.toLowerCase(Locale.ROOT);
    }

    /** 검색 별칭·검색어: EXIF 규칙 + 공백·하이픈 모두 제거 */
    public static String alias(String raw) {
        String s = exif(raw);
        return s == null ? null : SPACE_OR_HYPHEN.matcher(s).replaceAll("");
    }
}
