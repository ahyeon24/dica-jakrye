package com.dicajakrye.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * 목록 페이지네이션 커서. 마지막 항목의 (created_at, id)를 담는다. (docs/API.md 1.5)
 * 프론트는 내용을 해석하지 않고 그대로 돌려보낸다.
 * created_at은 PostgreSQL timestamptz와 같은 마이크로초 단위로 다룬다.
 */
public record Cursor(Instant createdAt, long id) {

    public static Cursor of(Instant createdAt, long id) {
        return new Cursor(createdAt.truncatedTo(ChronoUnit.MICROS), id);
    }

    public String encode() {
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, createdAt);
        String raw = micros + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 잘못된 커서면 INVALID_REQUEST */
    public static Cursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            Instant at = Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS);
            return new Cursor(at, Long.parseLong(parts[1]));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }
}
