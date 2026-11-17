package com.dicajakrye.camera;

import java.util.List;

/** 기종 API 응답. docs/API.md 4장 */
public final class CameraResponses {

    private CameraResponses() {
    }

    public record SearchResult(List<SearchItem> items) {
    }

    /** matchedAlias: 대표 이름(또는 한글 이름)이 아닌 별칭으로 찾았을 때 그 별칭. 아니면 null */
    public record SearchItem(String slug, String name, String nameKo, String brandName, String matchedAlias,
                             long photoCount, String coverThumbnailUrl) {
    }

    public record CameraDetail(String slug, String name, String nameKo, BrandRef brand, Short releaseYear,
                               String sensorType, List<String> aliases, long photoCount, long flashPhotoCount,
                               long originalSizePhotoCount, String nativeResolution) {
    }

    public record BrandRef(String slug, String name, String nameKo) {
    }
}
