package com.dicajakrye.photo;

/** 작례 요약 (목록용). docs/API.md 7.1 */
public record PhotoSummaryResponse(
        long id,
        String source,
        String thumbnailUrl,
        Integer width,
        Integer height,
        Boolean flashFired,
        Boolean isOriginalSize,
        CameraRef camera,         // 미분류면 null (프로필 피드)
        String credit
) {
    public record CameraRef(String slug, String name, String nameKo) {
    }
}
