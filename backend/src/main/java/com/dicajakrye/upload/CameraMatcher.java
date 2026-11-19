package com.dicajakrye.upload;

import com.dicajakrye.common.TextNormalizer;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * EXIF 제조사+모델로 기종을 찾는다. 두 값을 정규화해서 camera_exif_model과 비교한다. (ERD.md 4장)
 * 모델명으로 못 찾으면 제조사 내부 기종 번호(캐논 Model ID)로 한 번 더 찾는다. (camera_maker_model)
 * 둘 다 못 찾으면 미분류. 관리자가 나중에 연결한다.
 */
@Component
public class CameraMatcher {

    public record Match(long cameraId, String slug, String name, String nameKo, String brandName,
                        Integer nativeWidth, Integer nativeHeight) {

        /** 카메라 원래 해상도의 긴 변. 모르면 null */
        public Integer nativeLongSide() {
            return nativeWidth == null || nativeHeight == null ? null : Math.max(nativeWidth, nativeHeight);
        }
    }

    private final JdbcClient jdbc;

    public CameraMatcher(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT = """
            SELECT c.id AS camera_id, c.slug, c.name, c.name_ko, b.name AS brand_name,
                   c.native_width, c.native_height
              FROM camera c
              JOIN brand b ON b.id = c.brand_id
            """;

    public Optional<Match> find(String makeRaw, String modelRaw) {
        return find(makeRaw, modelRaw, null);
    }

    public Optional<Match> find(String makeRaw, String modelRaw, Long makerModelId) {
        String make = TextNormalizer.exif(makeRaw);
        String model = TextNormalizer.exif(modelRaw);
        if (make == null || model == null || make.isEmpty() || model.isEmpty()) {
            return Optional.empty();
        }
        Optional<Match> byName = jdbc.sql(SELECT + """
                         JOIN camera_exif_model e ON e.camera_id = c.id
                        WHERE e.exif_make = :make AND e.exif_model = :model
                        """)
                .param("make", make)
                .param("model", model)
                .query(Match.class)
                .optional();
        if (byName.isPresent() || makerModelId == null) {
            return byName;
        }
        return jdbc.sql(SELECT + """
                         JOIN camera_maker_model mm ON mm.camera_id = c.id
                        WHERE mm.exif_make = :make AND mm.maker_model_id = :id
                        """)
                .param("make", make)
                .param("id", makerModelId)
                .query(Match.class)
                .optional();
    }
}
