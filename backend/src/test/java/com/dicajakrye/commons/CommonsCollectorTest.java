package com.dicajakrye.commons;

import static org.assertj.core.api.Assertions.assertThat;

import com.dicajakrye.TestcontainersConfiguration;
import com.dicajakrye.commons.CommonsFileFilter.Reject;
import com.dicajakrye.storage.ImageStorage;
import com.dicajakrye.storage.ImageStorage.Visibility;
import com.dicajakrye.storage.StorageKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

/**
 * Commons 수집 전체 흐름 테스트. 실제 Commons 대신 가짜 클라이언트를 쓰고, 이미지는 임시 폴더에 저장한다.
 */
@Import({TestcontainersConfiguration.class, CommonsCollectorTest.FakeCommonsConfig.class})
@SpringBootTest
class CommonsCollectorTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.storage.local.dir", () -> storageDir.toString());
    }

    @Autowired
    CommonsCollector collector;

    @Autowired
    FakeCommonsClient fake;

    @Autowired
    ImageStorage storage;

    @Autowired
    JdbcClient jdbc;

    long ixus30;

    @BeforeEach
    void setUp() {
        ixus30 = jdbc.sql("SELECT id FROM camera WHERE slug = 'canon-ixus-30'").query(Long.class).single();
        fake.files.clear();
        fake.failDownloads.clear();
        List<CommonsFile> list = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            list.add(file(1000 + i, "작가 A", "CC BY-SA 2.0", "Canon DIGITAL IXUS 30", 2048, 1536));
        }
        for (int i = 1; i <= 3; i++) {
            list.add(file(2000 + i, "<b>작가 B</b>", "CC BY 2.0", "Canon PowerShot SD200", 1600, 1200));
        }
        list.add(file(3001, "작가 C", "CC BY 2.0", "Canon IXY DIGITAL 110 IS", 2048, 1536)); // 이름은 비슷해도 다른 카메라 (CAMERAS.md 6장)
        list.add(file(3002, "작가 D", "GFDL", "Canon DIGITAL IXUS 30", 2048, 1536));      // 라이선스
        list.add(file(3003, "작가 E", "CC0", "Canon DIGITAL IXUS 30", 300, 225));          // 너무 작음
        fake.files.put("Canon Digital IXUS 30", list);
    }

    @AfterEach
    void cleanUp() throws Exception {
        FileSystemUtils.deleteRecursively(storageDir.resolve("public")); // 테스트끼리 파일이 남지 않게
        jdbc.sql("DELETE FROM commons_meta").update();
        jdbc.sql("DELETE FROM photo WHERE source = 'COMMONS'").update();
        jdbc.sql("UPDATE camera SET commons_collected_at = NULL").update();
    }

    @Test
    void 조건을_통과한_사진을_작가별로_고르게_저장한다() throws Exception {
        CommonsCollector.Result r = collector.collect(ixus30);

        assertThat(r.saved()).isEqualTo(13); // 작가 A 10장(제한) + 작가 B 3장
        assertThat(r.rejected()).containsEntry(Reject.MODEL_MISMATCH, 1)
                .containsEntry(Reject.LICENSE, 1)
                .containsEntry(Reject.TOO_SMALL, 1);

        Map<String, Integer> byAuthor = new HashMap<>();
        jdbc.sql("SELECT author, count(*) AS n FROM commons_meta GROUP BY author")
                .query((rs, i) -> byAuthor.put(rs.getString(1), rs.getInt(2))).list();
        assertThat(byAuthor).containsEntry("작가 A", 10).containsEntry("작가 B", 3); // HTML 제거됨

        // 파일: 공개 저장소에 보기용·썸네일, EXIF 없음
        assertThat(storage.exists(Visibility.PUBLIC, StorageKeys.commonsDisplay(2001))).isTrue();
        byte[] thumb = Files.readAllBytes(storageDir.resolve("public").resolve(StorageKeys.commonsThumbnail(2001)));
        assertThat(TestImages.hasExif(thumb)).isFalse();
        assertThat(TestImages.size(thumb)).containsExactly(640, 480);

        // DB 값: 원본 크기 판별 (IXUS 30 원래 해상도 2048)
        Boolean original1600 = jdbc.sql("""
                SELECT p.is_original_size FROM photo p JOIN commons_meta m ON m.photo_id = p.id WHERE m.commons_page_id = 2001
                """).query(Boolean.class).single();
        assertThat(original1600).isFalse();

        Object collectedAt = jdbc.sql("SELECT commons_collected_at FROM camera WHERE id = :id")
                .param("id", ixus30).query(Object.class).single();
        assertThat(collectedAt).isNotNull();
    }

    @Test
    void 다시_실행해도_같은_사진을_또_넣지_않는다() {
        collector.collect(ixus30);
        CommonsCollector.Result second = collector.collect(ixus30);
        assertThat(second.saved()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM photo WHERE source = 'COMMONS'").query(Integer.class).single())
                .isEqualTo(13);
    }

    @Test
    void 다운로드에_실패한_사진은_건너뛰고_파일도_남기지_않는다() {
        fake.failDownloads.add(2001L);
        CommonsCollector.Result r = collector.collect(ixus30);
        assertThat(r.saved()).isEqualTo(12);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(storage.exists(Visibility.PUBLIC, StorageKeys.commonsDisplay(2001))).isFalse();
    }

    // ---------- 가짜 Commons ----------

    static CommonsFile file(long pageId, String artist, String license, String model, int w, int h) {
        Map<String, String> meta = new HashMap<>();
        meta.put("Make", "Canon");
        meta.put("Model", model);
        meta.put("Flash", pageId % 2 == 0 ? "1" : "0");
        return new CommonsFile(pageId, "File:" + pageId + ".jpg",
                "https://commons.wikimedia.org/wiki/File:" + pageId + ".jpg", "image/jpeg", w, h,
                "https://upload.wikimedia.org/" + pageId + "?w=" + w + "&h=" + h, artist, license,
                "https://creativecommons.org/", meta);
    }

    static class FakeCommonsClient implements CommonsClient {
        final Map<String, List<CommonsFile>> files = new ConcurrentHashMap<>();
        final Set<Long> failDownloads = ConcurrentHashMap.newKeySet();

        @Override
        public List<CommonsFile> listCategoryFiles(String category, int max) {
            return files.getOrDefault(category, List.of());
        }

        @Override
        public byte[] download(String url) {
            long pageId = Long.parseLong(url.substring(url.lastIndexOf('/') + 1, url.indexOf('?')));
            if (failDownloads.contains(pageId)) {
                throw new RuntimeException("다운로드 실패 (테스트)");
            }
            int w = Integer.parseInt(url.replaceAll(".*w=(\\d+).*", "$1"));
            int h = Integer.parseInt(url.replaceAll(".*h=(\\d+).*", "$1"));
            return TestImages.jpeg(Math.min(w, 1600), Math.min(h, 1200)); // Commons 축소본 흉내
        }

        @Override
        public List<Long> missingPageIds(List<Long> pageIds) {
            return List.of();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeCommonsConfig {
        @Bean
        @Primary
        FakeCommonsClient fakeCommonsClient() {
            return new FakeCommonsClient();
        }
    }
}
