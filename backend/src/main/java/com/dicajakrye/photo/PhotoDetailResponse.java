package com.dicajakrye.photo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/** 작례 상세. docs/API.md 5장 첫 API */
public record PhotoDetailResponse(
        long id,
        String source,
        String displayUrl,
        String thumbnailUrl,
        Integer width,
        Integer height,
        Boolean isOriginalSize,
        CameraRef camera,        // 미분류면 null
        Exif exif,
        Uploader uploader,       // Commons면 null
        Commons commons,         // 업로드면 null
        OffsetDateTime createdAt,
        boolean isMine,
        boolean hiddenByReport   // 신고로 가려짐. 올린 사람·관리자에게만 이 상태로 보인다
) {
    public record CameraRef(String slug, String name, String nameKo, String brandName) {
    }

    /** EXIF에 있는 만큼만. 없는 값은 null */
    public record Exif(LocalDateTime takenAt, Boolean flashFired, BigDecimal fNumber, String exposureTime,
                       Integer iso, BigDecimal focalLength) {
    }

    public record Uploader(String nickname, String profileImageUrl) {
    }

    /** 출처 표시(TASL)에 필요한 값. modified는 크기를 줄였으므로 항상 true */
    public record Commons(String author, String licenseName, String licenseUrl, String pageUrl, boolean modified) {
    }
}
