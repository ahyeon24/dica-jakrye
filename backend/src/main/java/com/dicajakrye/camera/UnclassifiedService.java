package com.dicajakrye.camera;

import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.common.ImageUrls;
import com.dicajakrye.common.TextNormalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미분류 작례 연결 (PRD F5, API.md 8장).
 * - 목록: EXIF (제조사, 모델)별로 묶고, 연결할 기종 후보를 추천한다.
 *   후보 1) 같은 캐논 내부 번호로 등록된 기종  2) 같은 브랜드에서 숫자가 들어간 이름 조각이 겹치는 기종
 * - 연결: 기존 기종에 붙이거나 새 기종을 만들어 붙인다. EXIF 모델명을 등록해서 앞으로 올라오는 사진도 자동으로 붙는다.
 * 판단은 컴퓨터가 돕고, 확정은 관리자가 한다. (이름만 보고 짝지으면 틀린다: IXY DIGITAL 110 IS ≠ IXUS 110 IS)
 */
@Service
public class UnclassifiedService {

    private static final int GROUP_LIMIT = 200;
    private static final int SAMPLES_PER_GROUP = 4;
    private static final int MAX_CANDIDATES = 3;
    /** 내부 번호를 읽는 제조사 (정규화한 EXIF 제조사). ExifReader와 맞춘다 */
    private static final String CANON = "canon";
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern SLUG_UNSAFE = Pattern.compile("[^a-z0-9]+");

    private final JdbcClient jdbc;
    private final ImageUrls imageUrls;

    public UnclassifiedService(JdbcClient jdbc, ImageUrls imageUrls) {
        this.jdbc = jdbc;
        this.imageUrls = imageUrls;
    }

    // ---------- 목록 ----------

    public record Sample(long id, String thumbnailUrl) {
    }

    public record Candidate(String slug, String name, String nameKo, String brandName, String reason) {
    }

    public record Group(String exifMake, String exifModel, int count, OffsetDateTime lastUploadedAt,
                        String makerModelId, Integer maxWidth, Integer maxHeight, String brandSlug,
                        List<Sample> samples, List<Candidate> candidates) {
    }

    record GroupRow(String make, String model, String makeRaw, String modelRaw, int count,
                    OffsetDateTime lastUploadedAt, Integer maxWidth, Integer maxHeight, String makerModelIds) {
    }

    record SampleRow(String make, String model, long id, String thumbnailKey) {
    }

    record CameraRow(long id, String slug, String name, String nameKo, String brandSlug, String brandName,
                     String aliases) {
    }

    record BrandRow(String slug, String name) {
    }

    @Transactional(readOnly = true)
    public List<Group> list() {
        List<GroupRow> groups = jdbc.sql("""
                        SELECT exif_make AS make, exif_model AS model,
                               min(exif_make_raw) AS make_raw, min(exif_model_raw) AS model_raw,
                               count(*) AS count, max(created_at) AS last_uploaded_at,
                               max(width) AS max_width, max(height) AS max_height,
                               string_agg(DISTINCT maker_model_id::text, ',') AS maker_model_ids
                          FROM photo
                         WHERE camera_id IS NULL AND deleted_at IS NULL AND exif_make IS NOT NULL AND exif_model IS NOT NULL
                         GROUP BY exif_make, exif_model
                         ORDER BY count(*) DESC, max(created_at) DESC
                         LIMIT :limit
                        """)
                .param("limit", GROUP_LIMIT)
                .query(GroupRow.class)
                .list();
        if (groups.isEmpty()) {
            return List.of();
        }

        Map<String, List<Sample>> samples = new HashMap<>();
        jdbc.sql("""
                        SELECT make, model, id, thumbnail_key FROM (
                            SELECT exif_make AS make, exif_model AS model, id, thumbnail_key,
                                   row_number() OVER (PARTITION BY exif_make, exif_model ORDER BY created_at DESC, id DESC) AS rn
                              FROM photo
                             WHERE camera_id IS NULL AND deleted_at IS NULL
                        ) t WHERE rn <= :n
                        """)
                .param("n", SAMPLES_PER_GROUP)
                .query(SampleRow.class)
                .list()
                .forEach(r -> samples.computeIfAbsent(key(r.make(), r.model()), k -> new ArrayList<>())
                        .add(new Sample(r.id(), imageUrls.publicUrl(r.thumbnailKey()))));

        List<BrandRow> brands = jdbc.sql("SELECT slug, name FROM brand").query(BrandRow.class).list();
        List<CameraRow> cameras = jdbc.sql("""
                        SELECT c.id, c.slug, c.name, c.name_ko, b.slug AS brand_slug, b.name AS brand_name,
                               string_agg(a.alias, '|') AS aliases
                          FROM camera c
                          JOIN brand b ON b.id = c.brand_id
                          LEFT JOIN camera_alias a ON a.camera_id = c.id
                         GROUP BY c.id, b.slug, b.name
                        """)
                .query(CameraRow.class)
                .list();
        Map<Long, CameraRow> byMakerId = new HashMap<>();
        Map<Long, CameraRow> camerasById = new HashMap<>();
        cameras.forEach(c -> camerasById.put(c.id(), c));
        jdbc.sql("SELECT maker_model_id, camera_id FROM camera_maker_model WHERE exif_make = :make")
                .param("make", CANON)
                .query((rs, i) -> Map.entry(rs.getLong(1), rs.getLong(2)))
                .list()
                .forEach(e -> byMakerId.put(e.getKey(), camerasById.get(e.getValue())));

        return groups.stream().map(g -> {
            String brandSlug = guessBrand(g.make(), brands);
            List<Candidate> candidates = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            List<Long> ids = g.makerModelIds() == null ? List.of()
                    : Arrays.stream(g.makerModelIds().split(",")).map(Long::valueOf).toList();
            if (CANON.equals(g.make())) {
                for (Long id : ids) {
                    CameraRow c = byMakerId.get(id);
                    if (c != null && seen.add(c.slug())) {
                        candidates.add(candidate(c, "MAKER_MODEL_ID"));
                    }
                }
            }
            similar(g.model(), brandSlug, cameras).stream()
                    .filter(c -> seen.add(c.slug()))
                    .limit(MAX_CANDIDATES)
                    .forEach(c -> candidates.add(candidate(c, "SIMILAR_NAME")));
            String makerModelId = ids.size() == 1 ? hex(ids.getFirst()) : null;
            return new Group(g.makeRaw(), g.modelRaw(), g.count(), g.lastUploadedAt(), makerModelId,
                    g.maxWidth(), g.maxHeight(), brandSlug,
                    samples.getOrDefault(key(g.make(), g.model()), List.of()), candidates);
        }).toList();
    }

    private static Candidate candidate(CameraRow c, String reason) {
        return new Candidate(c.slug(), c.name(), c.nameKo(), c.brandName(), reason);
    }

    /** EXIF 제조사에 브랜드 이름이 들어 있으면 그 브랜드 ("eastman kodak company" → kodak) */
    static String guessBrand(String make, List<BrandRow> brands) {
        if (make == null) {
            return null;
        }
        return brands.stream()
                .filter(b -> make.contains(b.name().toLowerCase(Locale.ROOT)))
                .map(BrandRow::slug)
                .findFirst()
                .orElse(null);
    }

    /**
     * 같은 브랜드에서 숫자가 들어간 이름 조각("145", "s630", "t10")이 겹치는 기종. 겹치는 조각이 많은 순.
     * 숫자 없이 "ixus"만 겹치는 건 후보로 보지 않는다 (IXUS 145 → IXUS 30을 추천하면 오히려 헷갈린다)
     */
    static List<CameraRow> similar(String model, String brandSlug, List<CameraRow> cameras) {
        if (brandSlug == null) {
            return List.of();
        }
        Set<String> modelTokens = tokens(model);
        if (modelTokens.stream().noneMatch(UnclassifiedService::hasDigit)) {
            return List.of();
        }
        record Scored(CameraRow camera, int score) {
        }
        return cameras.stream()
                .filter(c -> brandSlug.equals(c.brandSlug()))
                .map(c -> {
                    Set<String> camTokens = tokens(c.name() + "|" + (c.aliases() == null ? "" : c.aliases()));
                    int digitHits = 0;
                    int hits = 0;
                    for (String t : modelTokens) {
                        if (camTokens.contains(t)) {
                            hits++;
                            if (hasDigit(t)) {
                                digitHits++;
                            }
                        }
                    }
                    return new Scored(c, digitHits == 0 ? 0 : digitHits * 10 + hits);
                })
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .map(Scored::camera)
                .toList();
    }

    private static Set<String> tokens(String s) {
        String n = TextNormalizer.exif(s);
        if (n == null) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>(Arrays.asList(NON_ALNUM.split(n)));
        out.remove("");
        return out;
    }

    private static boolean hasDigit(String s) {
        return s.chars().anyMatch(Character::isDigit);
    }

    private static String hex(long id) {
        return String.format("0x%08X", id);
    }

    private static String key(String make, String model) {
        return make + "\u0000" + model;
    }

    // ---------- 연결 ----------

    public record NewCamera(String brandSlug, String name, String nameKo, String sensorType,
                            Integer nativeWidth, Integer nativeHeight, Integer releaseYear) {
    }

    public record Linked(String cameraSlug, int moved) {
    }

    /**
     * EXIF (제조사, 모델)을 기종에 연결하고, 같은 값의 미분류 작례를 모두 그 기종으로 옮긴다.
     * cameraSlug가 있으면 기존 기종, 없으면 newCamera로 기종을 새로 만든다.
     * 그 묶음의 사진이 캐논 내부 번호 하나만 가지고 있으면 그 번호도 등록해서, 이름이 다른 같은 기종도 앞으로 자동으로 붙게 한다.
     */
    @Transactional
    public Linked link(String exifMakeRaw, String exifModelRaw, String cameraSlug, NewCamera newCamera) {
        String make = TextNormalizer.exif(exifMakeRaw);
        String model = TextNormalizer.exif(exifModelRaw);
        if (make == null || model == null || make.isEmpty() || model.isEmpty()
                || make.length() > 100 || model.length() > 100) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        if ((cameraSlug == null) == (newCamera == null)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST); // 둘 중 하나만
        }
        long cameraId = cameraSlug != null ? cameraIdBySlug(cameraSlug) : createCamera(newCamera);

        Long mapped = jdbc.sql("SELECT camera_id FROM camera_exif_model WHERE exif_make = :make AND exif_model = :model")
                .param("make", make)
                .param("model", model)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (mapped != null && mapped != cameraId) {
            throw new ApiException(ErrorCode.EXIF_MODEL_ALREADY_MAPPED);
        }
        if (mapped == null) {
            jdbc.sql("INSERT INTO camera_exif_model (camera_id, exif_make, exif_model) VALUES (:c, :make, :model)")
                    .param("c", cameraId)
                    .param("make", make)
                    .param("model", model)
                    .update();
        }

        Long makerModelId = null;
        if (CANON.equals(make)) {
            List<Long> ids = jdbc.sql("""
                            SELECT DISTINCT maker_model_id FROM photo
                             WHERE camera_id IS NULL AND deleted_at IS NULL AND exif_make = :make AND exif_model = :model
                               AND maker_model_id IS NOT NULL
                            """)
                    .param("make", make)
                    .param("model", model)
                    .query(Long.class)
                    .list();
            if (ids.size() == 1) { // 여러 개면 섞인 묶음일 수 있어 등록하지 않는다
                makerModelId = ids.getFirst();
                jdbc.sql("""
                                INSERT INTO camera_maker_model (camera_id, exif_make, maker_model_id)
                                VALUES (:c, :make, :id) ON CONFLICT (exif_make, maker_model_id) DO NOTHING
                                """)
                        .param("c", cameraId)
                        .param("make", make)
                        .param("id", makerModelId)
                        .update();
            }
        }

        // 같은 EXIF 모델명, 또는 같은 캐논 내부 번호인 미분류 작례를 옮긴다. 원본 크기 여부도 기종 해상도로 계산
        int moved = jdbc.sql("""
                        UPDATE photo p
                           SET camera_id = c.id,
                               is_original_size = CASE
                                   WHEN c.native_width IS NULL OR p.width IS NULL THEN NULL
                                   ELSE greatest(p.width, p.height) >= greatest(c.native_width, c.native_height) * 0.95
                               END,
                               updated_at = now()
                          FROM camera c
                         WHERE c.id = :c AND p.camera_id IS NULL AND p.deleted_at IS NULL AND p.exif_make = :make
                           AND (p.exif_model = :model OR (CAST(:mid AS bigint) IS NOT NULL AND p.maker_model_id = :mid))
                        """)
                .param("c", cameraId)
                .param("make", make)
                .param("model", model)
                .param("mid", makerModelId)
                .update();
        String slug = jdbc.sql("SELECT slug FROM camera WHERE id = :id").param("id", cameraId).query(String.class).single();
        return new Linked(slug, moved);
    }

    private long cameraIdBySlug(String slug) {
        return jdbc.sql("SELECT id FROM camera WHERE slug = :s")
                .param("s", slug)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.CAMERA_NOT_FOUND));
    }

    /** 기종 추가. 주소 이름(slug)은 "브랜드-이름"으로 만든다. 이름·한글 이름은 검색 별칭에도 넣는다 */
    private long createCamera(NewCamera n) {
        String name = n.name() == null ? "" : n.name().strip();
        String nameKo = n.nameKo() == null || n.nameKo().isBlank() ? null : n.nameKo().strip();
        String sensor = n.sensorType() == null || n.sensorType().isBlank() ? null : n.sensorType().strip();
        if (name.isEmpty() || name.length() > 100 || (nameKo != null && nameKo.length() > 100)
                || (sensor != null && !sensor.equals("CCD") && !sensor.equals("CMOS"))
                || (n.nativeWidth() == null) != (n.nativeHeight() == null)
                || (n.nativeWidth() != null && (n.nativeWidth() <= 0 || n.nativeHeight() <= 0
                        || n.nativeWidth() > 20000 || n.nativeHeight() > 20000))
                || (n.releaseYear() != null && (n.releaseYear() < 1990 || n.releaseYear() > 2030))) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        record BrandRef(long id, String slug) {
        }
        BrandRef brand = jdbc.sql("SELECT id, slug FROM brand WHERE slug = :s")
                .param("s", n.brandSlug() == null ? "" : n.brandSlug())
                .query(BrandRef.class)
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.BRAND_NOT_FOUND));
        String namePart = SLUG_UNSAFE.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("^-+|-+$", "");
        if (namePart.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        String slug = brand.slug() + "-" + namePart;
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM camera WHERE slug = :slug OR (brand_id = :b AND name = :name))")
                .param("slug", slug)
                .param("b", brand.id())
                .param("name", name)
                .query(Boolean.class)
                .single();
        if (exists) {
            throw new ApiException(ErrorCode.CAMERA_ALREADY_EXISTS);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("b", brand.id());
        params.put("name", name);
        params.put("ko", nameKo);
        params.put("slug", slug);
        params.put("year", n.releaseYear());
        params.put("sensor", sensor);
        params.put("w", n.nativeWidth());
        params.put("h", n.nativeHeight());
        long id = jdbc.sql("""
                        INSERT INTO camera (brand_id, name, name_ko, slug, release_year, sensor_type, native_width, native_height)
                        VALUES (:b, :name, :ko, :slug, :year, :sensor, :w, :h)
                        RETURNING id
                        """)
                .params(params)
                .query(Long.class)
                .single();
        for (String alias : nameKo == null ? List.of(name) : List.of(name, nameKo)) {
            jdbc.sql("""
                            INSERT INTO camera_alias (camera_id, alias, normalized) VALUES (:c, :alias, :n)
                            ON CONFLICT (camera_id, normalized) DO NOTHING
                            """)
                    .param("c", id)
                    .param("alias", alias)
                    .param("n", TextNormalizer.alias(alias))
                    .update();
        }
        return id;
    }
}
