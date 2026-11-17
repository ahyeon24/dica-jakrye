package com.dicajakrye.commons;

import com.dicajakrye.commons.CommonsFileFilter.Candidate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Commons 작례 한 장을 photo + commons_meta에 한 트랜잭션으로 저장한다 */
@Component
public class CommonsPhotoWriter {

    private final JdbcClient jdbc;

    public CommonsPhotoWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long insert(long cameraId, String sourceCategory, Candidate c, String displayKey, String thumbnailKey) {
        CommonsFile f = c.file();
        Map<String, Object> p = new HashMap<>();
        p.put("camera", cameraId);
        p.put("makeRaw", c.exifMakeRaw());
        p.put("modelRaw", c.exifModelRaw());
        p.put("make", com.dicajakrye.common.TextNormalizer.exif(c.exifMakeRaw()));
        p.put("model", com.dicajakrye.common.TextNormalizer.exif(c.exifModelRaw()));
        p.put("flash", c.flashFired());
        p.put("takenAt", c.takenAt());
        p.put("fNumber", c.fNumber());
        p.put("exposure", c.exposureTime());
        p.put("iso", c.iso());
        p.put("focal", c.focalLength());
        p.put("width", f.width());
        p.put("height", f.height());
        p.put("original", c.isOriginalSize());
        p.put("display", displayKey);
        p.put("thumb", thumbnailKey);

        long photoId = jdbc.sql("""
                        INSERT INTO photo (source, camera_id, exif_make_raw, exif_model_raw, exif_make, exif_model,
                                           flash_fired, taken_at, f_number, exposure_time, iso, focal_length,
                                           width, height, is_original_size, display_key, thumbnail_key)
                        VALUES ('COMMONS', :camera, :makeRaw, :modelRaw, :make, :model,
                                :flash, :takenAt, :fNumber, :exposure, :iso, :focal,
                                :width, :height, :original, :display, :thumb)
                        RETURNING id
                        """)
                .params(p)
                .query(Long.class)
                .single();

        Map<String, Object> m = new HashMap<>();
        m.put("photo", photoId);
        m.put("page", f.pageId());
        m.put("title", truncate(f.title(), 300));
        m.put("url", truncate(f.pageUrl(), 500));
        m.put("author", c.author());
        m.put("license", truncate(f.licenseName().strip(), 50));
        m.put("licenseUrl", truncate(f.licenseUrl(), 300));
        m.put("category", sourceCategory);
        m.put("now", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.sql("""
                        INSERT INTO commons_meta (photo_id, commons_page_id, file_title, page_url, author,
                                                  license_name, license_url, source_category, fetched_at, last_checked_at)
                        VALUES (:photo, :page, :title, :url, :author, :license, :licenseUrl, :category, :now, :now)
                        """)
                .params(m)
                .update();
        return photoId;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
