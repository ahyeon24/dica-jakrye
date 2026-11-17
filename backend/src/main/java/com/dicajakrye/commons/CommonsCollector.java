package com.dicajakrye.commons;

import com.dicajakrye.commons.CommonsFileFilter.Accepted;
import com.dicajakrye.commons.CommonsFileFilter.CameraRule;
import com.dicajakrye.commons.CommonsFileFilter.Candidate;
import com.dicajakrye.commons.CommonsFileFilter.Reject;
import com.dicajakrye.commons.CommonsFileFilter.Rejected;
import com.dicajakrye.image.ImageProcessor;
import com.dicajakrye.storage.ImageStorage;
import com.dicajakrye.storage.ImageStorage.Visibility;
import com.dicajakrye.storage.StorageKeys;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 기종 하나의 Commons 작례를 모은다. (docs/ARCHITECTURE.md 3.3)
 * 분류 파일 목록 → 걸러내기 → 작가별로 고르게 고르기 → 받아서 보기용·썸네일 만들기 → R2(로컬) 저장 → DB 저장
 */
@Service
public class CommonsCollector {

    private static final Logger log = LoggerFactory.getLogger(CommonsCollector.class);

    private final CommonsClient client;
    private final ImageProcessor images;
    private final ImageStorage storage;
    private final CommonsPhotoWriter writer;
    private final JdbcClient jdbc;
    private final CommonsProperties props;

    public CommonsCollector(CommonsClient client, ImageProcessor images, ImageStorage storage,
                            CommonsPhotoWriter writer, JdbcClient jdbc, CommonsProperties props) {
        this.client = client;
        this.images = images;
        this.storage = storage;
        this.writer = writer;
        this.jdbc = jdbc;
        this.props = props;
    }

    public record Result(String cameraSlug, int listed, int accepted, int saved, int failed,
                         Map<Reject, Integer> rejected) {
    }

    private record CameraInfo(long id, String slug, Integer nativeWidth, Integer nativeHeight) {
    }

    private record Pick(Candidate candidate, String category) {
    }

    public Result collect(long cameraId) {
        CameraInfo cam = jdbc.sql("SELECT id, slug, native_width, native_height FROM camera WHERE id = :id")
                .param("id", cameraId).query(CameraInfo.class).single();

        int existing = jdbc.sql("""
                        SELECT count(*) FROM photo WHERE camera_id = :id AND source = 'COMMONS' AND deleted_at IS NULL
                        """)
                .param("id", cameraId).query(Integer.class).single();
        int slots = props.perCamera() - existing;
        Map<Reject, Integer> rejected = new EnumMap<>(Reject.class);
        if (slots <= 0) {
            markCollected(cameraId);
            return new Result(cam.slug(), 0, 0, 0, 0, rejected);
        }

        CameraRule rule = rule(cam);
        // 지운 작례(저작권 요청 등)도 다시 가져오지 않도록, 삭제 여부와 관계없이 이미 본 pageId는 건너뛴다
        Set<Long> seenPageIds = new HashSet<>(jdbc.sql("SELECT m.commons_page_id FROM commons_meta m JOIN photo p ON p.id = m.photo_id WHERE p.camera_id = :id")
                .param("id", cameraId).query(Long.class).list());
        Map<String, Integer> byAuthor = new HashMap<>();
        jdbc.sql("""
                        SELECT m.author, count(*) AS n FROM commons_meta m JOIN photo p ON p.id = m.photo_id
                         WHERE p.camera_id = :id AND p.deleted_at IS NULL GROUP BY m.author
                        """)
                .param("id", cameraId)
                .query((rs, i) -> byAuthor.put(rs.getString("author"), rs.getInt("n")))
                .list();

        List<String> categories = jdbc.sql("SELECT category_name FROM camera_source_category WHERE camera_id = :id ORDER BY id")
                .param("id", cameraId).query(String.class).list();

        int listed = 0;
        Map<Long, Pick> accepted = new LinkedHashMap<>();
        for (String category : categories) {
            List<CommonsFile> files = client.listCategoryFiles(category, props.poolSize());
            listed += files.size();
            for (CommonsFile f : files) {
                if (seenPageIds.contains(f.pageId()) || accepted.containsKey(f.pageId())) {
                    continue;
                }
                switch (CommonsFileFilter.check(f, rule)) {
                    case Accepted a -> accepted.put(f.pageId(), new Pick(a.candidate(), category));
                    case Rejected r -> rejected.merge(r.reason(), 1, Integer::sum);
                }
            }
        }

        Map<Long, String> categoryOf = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        accepted.forEach((pageId, pick) -> {
            candidates.add(pick.candidate());
            categoryOf.put(pageId, pick.category());
        });
        List<Candidate> chosen = AuthorRoundRobin.pick(candidates, byAuthor, props.perAuthor(), slots);

        int saved = 0;
        int failed = 0;
        for (Candidate c : chosen) {
            if (save(cam, categoryOf.get(c.file().pageId()), c)) {
                saved++;
            } else {
                failed++;
            }
        }
        markCollected(cameraId);
        Result result = new Result(cam.slug(), listed, accepted.size(), saved, failed, rejected);
        log.info("Commons 수집 {}: 후보 {}장, 조건 통과 {}장, 저장 {}장, 실패 {}장, 제외 {}",
                cam.slug(), listed, accepted.size(), saved, failed, rejected);
        return result;
    }

    private boolean save(CameraInfo cam, String category, Candidate c) {
        long pageId = c.file().pageId();
        String displayKey = StorageKeys.commonsDisplay(pageId);
        String thumbKey = StorageKeys.commonsThumbnail(pageId);
        try {
            byte[] source = client.download(c.file().downloadUrl());
            ImageProcessor.Processed p = images.process(source);
            storage.put(Visibility.PUBLIC, displayKey, p.display(), "image/jpeg");
            storage.put(Visibility.PUBLIC, thumbKey, p.thumbnail(), "image/jpeg");
            try {
                writer.insert(cam.id(), category, c, displayKey, thumbKey);
            } catch (RuntimeException dbError) {
                storage.delete(Visibility.PUBLIC, displayKey); // DB 저장이 실패하면 파일도 지운다
                storage.delete(Visibility.PUBLIC, thumbKey);
                throw dbError;
            }
            return true;
        } catch (CommonsClient.CommonsUnavailableException e) {
            throw e; // Commons가 응답하지 않으면 이번 실행을 멈춘다
        } catch (RuntimeException e) {
            log.warn("Commons 작례 저장 실패 {} (pageId {}): {}", cam.slug(), pageId, e.getMessage());
            return false;
        }
    }

    private CameraRule rule(CameraInfo cam) {
        Set<String> keys = new HashSet<>(jdbc.sql("SELECT exif_make, exif_model FROM camera_exif_model WHERE camera_id = :id")
                .param("id", cam.id())
                .query((rs, i) -> CameraRule.key(rs.getString(1), rs.getString(2)))
                .list());
        Integer nativeLong = cam.nativeWidth() == null || cam.nativeHeight() == null
                ? null : Math.max(cam.nativeWidth(), cam.nativeHeight());
        return new CameraRule(keys, nativeLong, props.minLongSide(), ImageProcessor.MAX_PIXELS);
    }

    private void markCollected(long cameraId) {
        jdbc.sql("UPDATE camera SET commons_collected_at = now() WHERE id = :id").param("id", cameraId).update();
    }
}
