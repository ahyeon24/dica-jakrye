package com.dicajakrye.photo;

import com.dicajakrye.common.Cursor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 작례 목록 읽기 쿼리. (created_at, id) 커서 방식 페이지네이션 */
@Repository
public class PhotoQueries {

    private final JdbcClient jdbc;

    public PhotoQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 작례 요약 한 줄. credit = 업로드면 닉네임, Commons면 작가 */
    public record PhotoSummaryRow(long id, String source, String thumbnailKey, Integer width, Integer height,
                                  Boolean flashFired, Boolean isOriginalSize, OffsetDateTime createdAt,
                                  String uploaderNickname, String commonsAuthor) {

        public String credit() {
            return uploaderNickname != null ? uploaderNickname : commonsAuthor;
        }
    }

    /**
     * 기종 작례 목록.
     * @param flash null이면 전체, true/false면 플래시 정보가 그 값인 작례만 (정보 없는 작례는 빠짐)
     * @param originalOnly true면 원본 크기 작례만
     * @param limit 페이지 크기 + 1 (다음 페이지가 있는지 보려고 하나 더 가져온다)
     */
    public List<PhotoSummaryRow> byCamera(long cameraId, Boolean flash, boolean originalOnly, Cursor cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.source, p.thumbnail_key, p.width, p.height, p.flash_fired, p.is_original_size,
                       p.created_at, u.nickname AS uploader_nickname, m.author AS commons_author
                  FROM photo p
                  LEFT JOIN users u ON u.id = p.uploader_id
                  LEFT JOIN commons_meta m ON m.photo_id = p.id
                 WHERE p.camera_id = :cameraId
                   AND p.deleted_at IS NULL
                """);
        if (flash != null) {
            sql.append(" AND p.flash_fired = :flash");
        }
        if (originalOnly) {
            sql.append(" AND p.is_original_size = true");
        }
        if (cursor != null) {
            sql.append(" AND (p.created_at < :cursorAt OR (p.created_at = :cursorAt AND p.id < :cursorId))");
        }
        sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT :limit");

        var spec = jdbc.sql(sql.toString())
                .param("cameraId", cameraId)
                .param("limit", limit);
        if (flash != null) {
            spec = spec.param("flash", flash);
        }
        if (cursor != null) {
            spec = spec.param("cursorAt", OffsetDateTime.ofInstant(cursor.createdAt(), ZoneOffset.UTC))
                    .param("cursorId", cursor.id());
        }
        return spec.query(PhotoSummaryRow.class).list();
    }
}
