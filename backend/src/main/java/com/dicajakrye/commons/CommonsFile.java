package com.dicajakrye.commons;

import java.util.Map;

/**
 * Commons API에서 받은 파일 하나의 정보.
 *
 * @param pageId         파일 페이지 ID (중복 수집 방지, 정리 배치에서 존재 확인)
 * @param title          파일 이름 (File:...)
 * @param pageUrl        파일 설명 페이지 링크 (출처 표시용)
 * @param mime           image/jpeg 등
 * @param width          원본 가로 px
 * @param height         원본 세로 px
 * @param downloadUrl    받아 올 주소 (긴 변 1600px 근처 축소본, 원본이 작으면 원본)
 * @param artistHtml     작가 (HTML이 섞여 있음)
 * @param licenseName    라이선스 이름 (CC BY-SA 4.0 등)
 * @param licenseUrl     라이선스 설명 링크 (없을 수 있음)
 * @param metadata       EXIF (commonmetadata 이름 → 값, 단순 값만)
 */
public record CommonsFile(
        long pageId,
        String title,
        String pageUrl,
        String mime,
        int width,
        int height,
        String downloadUrl,
        String artistHtml,
        String licenseName,
        String licenseUrl,
        Map<String, String> metadata
) {
}
