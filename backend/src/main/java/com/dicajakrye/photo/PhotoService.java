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

    record DetailRow(long id, String source, String displayKey, String thumbnailKey, Integer width, Integer height,
                     Boolean isOriginalSize, OffsetDateTime createdAt,
                     LocalDateTime takenAt, Boolean flashFired, BigDecimal fNumber, String exposureTime,
                     Integer iso, BigDecimal focalLength,
                     String cameraSlug, String cameraName, String cameraNameKo, String brandName,
                     String uploaderNickname, String uploaderImageKey,
                     String commonsAuthor, String licenseName, String licenseUrl, String pageUrl) {
    }

    @Transactional(readOnly = true)
    public PhotoDetailResponse detail(long id) {
        DetailRow r = jdbc.sql("""
                        SELECT p.id, p.source, p.display_key, p.thumbnail_key, p.width, p.height,
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
                false); // TODO 로그인을 만들면 현재 사용자와 uploader_id를 비교한다
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
