package com.dicajakrye.photo;

import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.common.ImageUrls;
import com.dicajakrye.photo.PhotoDetailResponse.CameraRef;
import com.dicajakrye.photo.PhotoDetailResponse.Commons;
import com.dicajakrye.photo.PhotoDetailResponse.Exif;
import com.dicajakrye.photo.PhotoDetailResponse.Uploader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoService {

    private final JdbcClient jdbc;
    private final ImageUrls imageUrls;

    public PhotoService(JdbcClient jdbc, ImageUrls imageUrls) {
        this.jdbc = jdbc;
        this.imageUrls = imageUrls;
    }

    record DetailRow(long id, String source, Long uploaderId, String displayKey, String thumbnailKey, Integer width, Integer height,
                     Boolean isOriginalSize, OffsetDateTime createdAt,
                     LocalDateTime takenAt, Boolean flashFired, BigDecimal fNumber, String exposureTime,
                     Integer iso, BigDecimal focalLength,
                     String cameraSlug, String cameraName, String cameraNameKo, String brandName,
                     String uploaderNickname, String uploaderImageKey,
                     String commonsAuthor, String licenseName, String licenseUrl, String pageUrl) {
    }

    /** 로그인하지 않았으면 viewerId는 null */
    @Transactional(readOnly = true)
    public PhotoDetailResponse detail(long id, Long viewerId) {
        DetailRow r = jdbc.sql("""
                        SELECT p.id, p.source, p.uploader_id, p.display_key, p.thumbnail_key, p.width, p.height,
                               p.is_original_size, p.created_at,
                               p.taken_at, p.flash_fired, p.f_number, p.exposure_time, p.iso, p.focal_length,
                               c.slug AS camera_slug, c.name AS camera_name, c.name_ko AS camera_name_ko,
                               b.name AS brand_name,
                               u.nickname AS uploader_nickname, u.profile_image_key AS uploader_image_key,
                               m.author AS commons_author, m.license_name, m.license_url, m.page_url
                          FROM photo p
                          LEFT JOIN camera c ON c.id = p.camera_id
                          LEFT JOIN brand b ON b.id = c.brand_id
                          LEFT JOIN users u ON u.id = p.uploader_id
                          LEFT JOIN commons_meta m ON m.photo_id = p.id
                         WHERE p.id = :id
                           AND p.deleted_at IS NULL
                        """)
                .param("id", id)
                .query(DetailRow.class)
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.PHOTO_NOT_FOUND));

        CameraRef camera = r.cameraSlug() == null ? null
                : new CameraRef(r.cameraSlug(), r.cameraName(), r.cameraNameKo(), r.brandName());
        Uploader uploader = r.uploaderNickname() == null ? null
                : new Uploader(r.uploaderNickname(), imageUrls.publicUrl(r.uploaderImageKey()));
        Commons commons = r.commonsAuthor() == null ? null
                : new Commons(r.commonsAuthor(), r.licenseName(), r.licenseUrl(), r.pageUrl(), true);
        Exif exif = new Exif(r.takenAt(), r.flashFired(), r.fNumber(), r.exposureTime(), r.iso(), r.focalLength());

        return new PhotoDetailResponse(r.id(), r.source(), imageUrls.publicUrl(r.displayKey()),
                imageUrls.publicUrl(r.thumbnailKey()), r.width(), r.height(), r.isOriginalSize(),
                camera, exif, uploader, commons, r.createdAt(),
                viewerId != null && viewerId.equals(r.uploaderId()));
    }

    /** 이전·다음 작례. 순서는 기종 작례 격자와 같다 (최신순). 필터도 같게 적용한다. */
    public record AdjacentResponse(PhotoDetailResponse prev, PhotoDetailResponse next) {
    }

    record Position(Long cameraId, OffsetDateTime createdAt) {
    }

    /**
     * prev = 격자에서 바로 앞(더 최신), next = 바로 뒤(더 오래됨).
     * 미분류 작례는 기종 순서가 없으므로 둘 다 null.
     */
    @Transactional(readOnly = true)
    public AdjacentResponse adjacent(long id, Boolean flash, boolean originalOnly, Long viewerId) {
        Position pos = jdbc.sql("SELECT camera_id, created_at FROM photo WHERE id = :id AND deleted_at IS NULL")
                .param("id", id)
                .query(Position.class)
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.PHOTO_NOT_FOUND));
        if (pos.cameraId() == null) {
            return new AdjacentResponse(null, null);
        }
        Long prevId = neighborId(pos, id, flash, originalOnly, true);
        Long nextId = neighborId(pos, id, flash, originalOnly, false);
        return new AdjacentResponse(prevId == null ? null : detail(prevId, viewerId), nextId == null ? null : detail(nextId, viewerId));
    }

    private Long neighborId(Position pos, long id, Boolean flash, boolean originalOnly, boolean newer) {
        StringBuilder sql = new StringBuilder("""
                SELECT id FROM photo
                 WHERE camera_id = :cameraId
                   AND deleted_at IS NULL
                """);
        if (flash != null) {
            sql.append(" AND flash_fired = :flash");
        }
        if (originalOnly) {
            sql.append(" AND is_original_size = true");
        }
        if (newer) {
            sql.append(" AND (created_at > :at OR (created_at = :at AND id > :id)) ORDER BY created_at ASC, id ASC");
        } else {
            sql.append(" AND (created_at < :at OR (created_at = :at AND id < :id)) ORDER BY created_at DESC, id DESC");
        }
        sql.append(" LIMIT 1");
        var spec = jdbc.sql(sql.toString())
                .param("cameraId", pos.cameraId())
                .param("at", pos.createdAt())
                .param("id", id);
        if (flash != null) {
            spec = spec.param("flash", flash);
        }
        return spec.query(Long.class).optional().orElse(null);
    }

    /** 조회수 +1. MVP는 DB에서 바로 올린다. 사용자가 많아지면 바꾼다 (docs/API.md, ARCHITECTURE.md 3.8) */
    @Transactional
    public void increaseView(long id) {
        int updated = jdbc.sql("UPDATE photo SET view_count = view_count + 1 WHERE id = :id AND deleted_at IS NULL")
                .param("id", id)
                .update();
        if (updated == 0) {
            throw new ApiException(ErrorCode.PHOTO_NOT_FOUND);
        }
    }
}
