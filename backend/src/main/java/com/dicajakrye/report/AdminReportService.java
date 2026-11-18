package com.dicajakrye.report;

import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.common.ImageUrls;
import com.dicajakrye.photo.PhotoDeleteService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 신고 처리 (API.md 8장). 처리는 작례 단위로, 그 작례의 미처리 신고를 함께 닫는다.
 * - 삭제: 작례 삭제 + 신고 RESOLVED/DELETED
 * - 기종 변경: 기종 바꾸고 다시 공개 + 신고 RESOLVED/CAMERA_CHANGED
 * - 기각: 다시 공개 + 신고 REJECTED
 */
@Service
public class AdminReportService {

    private static final int LIST_LIMIT = 500;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final PhotoDeleteService photoDeleteService;
    private final ImageUrls imageUrls;

    public AdminReportService(JdbcClient jdbc, TransactionTemplate tx, PhotoDeleteService photoDeleteService,
                              ImageUrls imageUrls) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.photoDeleteService = photoDeleteService;
        this.imageUrls = imageUrls;
    }

    record Row(long reportId, String reason, String detail, OffsetDateTime reportedAt, String reporterNickname,
               long photoId, String source, String thumbnailKey, OffsetDateTime hiddenAt,
               String cameraSlug, String cameraName, String uploaderNickname, String commonsAuthor) {
    }

    public record ReportItem(long id, String reason, String reasonLabel, String detail, OffsetDateTime reportedAt,
                             String reporterNickname) {
    }

    public record PhotoReports(long photoId, String source, String thumbnailUrl, boolean hidden,
                               OffsetDateTime hiddenAt, String cameraSlug, String cameraName, String credit,
                               OffsetDateTime firstReportedAt, List<ReportItem> reports) {
    }

    /** 미처리 신고를 작례별로 묶어, 먼저 신고된 작례부터 */
    public List<PhotoReports> pending() {
        List<Row> rows = jdbc.sql("""
                        SELECT r.id AS report_id, r.reason, r.detail, r.created_at AS reported_at,
                               ru.nickname AS reporter_nickname,
                               p.id AS photo_id, p.source, p.thumbnail_key, p.hidden_at,
                               c.slug AS camera_slug, c.name AS camera_name,
                               u.nickname AS uploader_nickname, m.author AS commons_author
                          FROM report r
                          JOIN photo p ON p.id = r.photo_id
                          JOIN users ru ON ru.id = r.reporter_id
                          LEFT JOIN camera c ON c.id = p.camera_id
                          LEFT JOIN users u ON u.id = p.uploader_id
                          LEFT JOIN commons_meta m ON m.photo_id = p.id
                         WHERE r.status = 'PENDING' AND p.deleted_at IS NULL
                         ORDER BY r.created_at, r.id
                         LIMIT :limit
                        """)
                .param("limit", LIST_LIMIT)
                .query(Row.class)
                .list();
        Map<Long, List<Row>> byPhoto = new LinkedHashMap<>();
        for (Row r : rows) {
            byPhoto.computeIfAbsent(r.photoId(), k -> new ArrayList<>()).add(r);
        }
        return byPhoto.values().stream().map(group -> {
            Row f = group.getFirst();
            List<ReportItem> reports = group.stream()
                    .map(r -> new ReportItem(r.reportId(), r.reason(), ReportReason.valueOf(r.reason()).label(),
                            r.detail(), r.reportedAt(), r.reporterNickname()))
                    .toList();
            return new PhotoReports(f.photoId(), f.source(), imageUrls.publicUrl(f.thumbnailKey()),
                    f.hiddenAt() != null, f.hiddenAt(), f.cameraSlug(), f.cameraName(),
                    f.uploaderNickname() != null ? f.uploaderNickname() : f.commonsAuthor(),
                    f.reportedAt(), reports);
        }).toList();
    }

    public void delete(long photoId, long adminId) {
        requirePending(photoId);
        photoDeleteService.deleteAsAdmin(photoId);
        close(photoId, adminId, "RESOLVED", "DELETED");
    }

    public void changeCamera(long photoId, String cameraSlug, long adminId) {
        tx.executeWithoutResult(status -> {
            requirePending(photoId);
            Long cameraId = jdbc.sql("SELECT id FROM camera WHERE slug = :s")
                    .param("s", cameraSlug == null ? "" : cameraSlug)
                    .query(Long.class)
                    .optional()
                    .orElseThrow(() -> new ApiException(ErrorCode.CAMERA_NOT_FOUND));
            // 원본 크기 여부도 새 기종 해상도로 다시 계산한다 (긴 변 95% 기준)
            jdbc.sql("""
                            UPDATE photo p
                               SET camera_id = c.id,
                                   is_original_size = CASE
                                       WHEN c.native_width IS NULL OR p.width IS NULL THEN NULL
                                       ELSE greatest(p.width, p.height) >= greatest(c.native_width, c.native_height) * 0.95
                                   END,
                                   hidden_at = NULL,
                                   updated_at = now()
                              FROM camera c
                             WHERE c.id = :cameraId AND p.id = :photoId
                            """)
                    .param("cameraId", cameraId)
                    .param("photoId", photoId)
                    .update();
            close(photoId, adminId, "RESOLVED", "CAMERA_CHANGED");
        });
    }

    public void reject(long photoId, long adminId) {
        tx.executeWithoutResult(status -> {
            requirePending(photoId);
            jdbc.sql("UPDATE photo SET hidden_at = NULL, updated_at = now() WHERE id = :id")
                    .param("id", photoId)
                    .update();
            close(photoId, adminId, "REJECTED", null);
        });
    }

    /** 신고와 관계없는 강제 삭제 (Commons 저작권자 요청 등). 남은 미처리 신고는 삭제로 닫는다 */
    public void forceDelete(long photoId, long adminId) {
        photoDeleteService.deleteAsAdmin(photoId);
        close(photoId, adminId, "RESOLVED", "DELETED");
    }

    private void requirePending(long photoId) {
        boolean any = jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM report r JOIN photo p ON p.id = r.photo_id
                                        WHERE r.photo_id = :id AND r.status = 'PENDING' AND p.deleted_at IS NULL)
                        """)
                .param("id", photoId)
                .query(Boolean.class)
                .single();
        if (!any) {
            throw new ApiException(ErrorCode.REPORT_NOT_FOUND);
        }
    }

    private void close(long photoId, long adminId, String status, String action) {
        jdbc.sql("""
                        UPDATE report SET status = :status, action = :action, handled_by = :admin, handled_at = now()
                         WHERE photo_id = :id AND status = 'PENDING'
                        """)
                .param("status", status)
                .param("action", action)
                .param("admin", adminId)
                .param("id", photoId)
                .update();
    }
}
