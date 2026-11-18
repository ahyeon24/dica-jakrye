package com.dicajakrye.camera;

import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 목록·검색 화면용 읽기 쿼리. 작례 수와 대표 썸네일을 함께 계산해야 해서 SQL로 직접 조회한다.
 * 쓰기(등록·수정)는 JPA 엔티티로 한다.
 */
@Repository
public class CameraQueries {

    private final JdbcClient jdbc;

    public CameraQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 기종 목록용 요약. coverThumbnailKey: 대표 작례가 있으면 그것, 없으면 최신 작례 */
    public record CameraSummaryRow(long id, String slug, String name, String nameKo, Short releaseYear,
                                   String brandName, long photoCount, String coverThumbnailKey) {
    }

    private static final String SUMMARY_SELECT = """
            SELECT c.id, c.slug, c.name, c.name_ko, c.release_year, b.name AS brand_name,
                   (SELECT count(*) FROM photo p WHERE p.camera_id = c.id AND p.deleted_at IS NULL AND p.hidden_at IS NULL) AS photo_count,
                   COALESCE(
                       (SELECT p.thumbnail_key FROM photo p WHERE p.id = c.cover_photo_id AND p.deleted_at IS NULL AND p.hidden_at IS NULL),
                       (SELECT p.thumbnail_key FROM photo p WHERE p.camera_id = c.id AND p.deleted_at IS NULL AND p.hidden_at IS NULL
                         ORDER BY p.created_at DESC, p.id DESC LIMIT 1)
                   ) AS cover_thumbnail_key
              FROM camera c
              JOIN brand b ON b.id = c.brand_id
            """;

    public List<CameraSummaryRow> summariesByBrand(long brandId) {
        return jdbc.sql(SUMMARY_SELECT + " WHERE c.brand_id = :brandId ORDER BY photo_count DESC, c.name")
                .param("brandId", brandId)
                .query(CameraSummaryRow.class)
                .list();
    }

    public List<CameraSummaryRow> summariesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SUMMARY_SELECT + " WHERE c.id IN (:ids)")
                .param("ids", ids)
                .query(CameraSummaryRow.class)
                .list();
    }

    /** 검색: 정규화된 별칭에 검색어가 들어 있는 별칭들 */
    public record AliasMatchRow(long cameraId, String alias, String normalized) {
    }

    public List<AliasMatchRow> aliasesContaining(String normalizedQuery) {
        return jdbc.sql("""
                        SELECT a.camera_id, a.alias, a.normalized
                          FROM camera_alias a
                         WHERE a.normalized LIKE :pattern ESCAPE '\\'
                        """)
                .param("pattern", "%" + escapeLike(normalizedQuery) + "%")
                .query(AliasMatchRow.class)
                .list();
    }

    /** 검색: 브랜드 이름(영문·한글)이 검색어와 같으면 그 브랜드의 기종 id */
    public List<Long> cameraIdsByBrandName(String normalizedQuery) {
        return jdbc.sql("""
                        SELECT c.id FROM camera c JOIN brand b ON b.id = c.brand_id
                         WHERE lower(replace(b.name, ' ', '')) = :q OR replace(b.name_ko, ' ', '') = :q
                        """)
                .param("q", normalizedQuery)
                .query(Long.class)
                .list();
    }

    /** 기종 정보 화면의 별칭 목록 (등록 순서) */
    public List<String> aliases(long cameraId) {
        return jdbc.sql("SELECT alias FROM camera_alias WHERE camera_id = :id ORDER BY id")
                .param("id", cameraId)
                .query(String.class)
                .list();
    }

    public record PhotoCountsRow(long total, long flash, long originalSize) {
    }

    public PhotoCountsRow photoCounts(long cameraId) {
        return jdbc.sql("""
                        SELECT count(*) AS total,
                               count(*) FILTER (WHERE flash_fired) AS flash,
                               count(*) FILTER (WHERE is_original_size) AS original_size
                          FROM photo
                         WHERE camera_id = :id AND deleted_at IS NULL AND hidden_at IS NULL
                        """)
                .param("id", cameraId)
                .query(PhotoCountsRow.class)
                .single();
    }

    /** LIKE 패턴의 특수문자(%, _, \)를 글자 그대로 검색되게 바꾼다 */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
