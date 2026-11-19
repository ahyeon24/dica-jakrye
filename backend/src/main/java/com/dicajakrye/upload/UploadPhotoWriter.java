package com.dicajakrye.upload;

import com.dicajakrye.common.TextNormalizer;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UploadPhotoWriter {

    public record NewPhoto(long uploaderId, Long cameraId, ExifInfo exif, int width, int height,
                           Boolean isOriginalSize, String originalKey, String displayKey, String thumbnailKey) {
    }

    private final JdbcClient jdbc;

    public UploadPhotoWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long insert(NewPhoto p) {
        Map<String, Object> params = new HashMap<>();
        params.put("uploader", p.uploaderId());
        params.put("camera", p.cameraId());
        params.put("makeRaw", p.exif().makeRaw());
        params.put("modelRaw", p.exif().modelRaw());
        params.put("make", TextNormalizer.exif(p.exif().makeRaw()));
        params.put("model", TextNormalizer.exif(p.exif().modelRaw()));
        params.put("flash", p.exif().flashFired());
        params.put("takenAt", p.exif().takenAt());
        params.put("fNumber", p.exif().fNumber());
        params.put("exposure", p.exif().exposureTime());
        params.put("iso", p.exif().iso());
        params.put("focal", p.exif().focalLength());
        params.put("width", p.width());
        params.put("height", p.height());
        params.put("orig", p.isOriginalSize());
        params.put("originalKey", p.originalKey());
        params.put("displayKey", p.displayKey());
        params.put("thumbKey", p.thumbnailKey());
        params.put("makerModelId", p.exif().makerModelId());
        return jdbc.sql("""
                        INSERT INTO photo (source, uploader_id, camera_id, exif_make_raw, exif_model_raw, exif_make, exif_model,
                                           flash_fired, taken_at, f_number, exposure_time, iso, focal_length,
                                           width, height, is_original_size, original_key, display_key, thumbnail_key, maker_model_id)
                        VALUES ('UPLOAD', :uploader, :camera, :makeRaw, :modelRaw, :make, :model,
                                :flash, :takenAt, :fNumber, :exposure, :iso, :focal,
                                :width, :height, :orig, :originalKey, :displayKey, :thumbKey, :makerModelId)
                        RETURNING id
                        """)
                .params(params)
                .query(Long.class)
                .single();
    }

    /** 최근 1시간 업로드 수. 지운 작례도 센다 (지우고 다시 올리는 식으로 제한을 피하지 못하게) */
    public int recentUploadCount(long uploaderId) {
        return jdbc.sql("""
                        SELECT count(*) FROM photo
                         WHERE uploader_id = :u AND created_at > now() - interval '1 hour'
                        """)
                .param("u", uploaderId)
                .query(Integer.class)
                .single();
    }
}
