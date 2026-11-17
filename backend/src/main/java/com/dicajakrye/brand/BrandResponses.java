package com.dicajakrye.brand;

import java.util.List;

/** 브랜드 API 응답. docs/API.md 4장 */
public final class BrandResponses {

    private BrandResponses() {
    }

    public record BrandItem(String slug, String name, String nameKo, long cameraCount) {
    }

    public record BrandDetail(String slug, String name, String nameKo, List<CameraItem> cameras) {
    }

    public record CameraItem(String slug, String name, String nameKo, Short releaseYear, long photoCount,
                             String coverThumbnailUrl) {
    }
}
