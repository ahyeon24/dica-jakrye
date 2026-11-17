package com.dicajakrye.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dicajakrye.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회 API 통합 테스트. Testcontainers PostgreSQL + V1(스키마) + V2(기종 26개 시드).
 * 작례는 테스트 안에서 넣고, 테스트가 끝나면 롤백한다. (@Transactional)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReadApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Nested
    class 브랜드 {

        @Test
        void 목록은_기종이_많은_브랜드부터() {
            MvcTestResult r = mvc.get().uri("/api/brands").exchange();
            assertThat(r).hasStatusOk();
            assertThat(r).bodyJson().extractingPath("$.length()").isEqualTo(9);
            assertThat(r).bodyJson().extractingPath("$[0].slug").isEqualTo("canon");
            assertThat(r).bodyJson().extractingPath("$[0].nameKo").isEqualTo("캐논");
            assertThat(r).bodyJson().extractingPath("$[0].cameraCount").isEqualTo(9);
        }

        @Test
        void 브랜드_페이지는_기종을_전부_보여준다() {
            MvcTestResult r = mvc.get().uri("/api/brands/canon").exchange();
            assertThat(r).hasStatusOk();
            assertThat(r).bodyJson().extractingPath("$.cameras.length()").isEqualTo(9);
        }

        @Test
        void 없는_브랜드는_404_BRAND_NOT_FOUND() {
            MvcTestResult r = mvc.get().uri("/api/brands/leica").exchange();
            assertThat(r).hasStatus(404);
            assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("BRAND_NOT_FOUND");
        }
    }

    @Nested
    class 검색 {

        @Test
        void 한글_별칭으로_찾는다() {
            MvcTestResult r = mvc.get().uri("/api/cameras/search").param("q", "익서스").exchange();
            assertThat(r).hasStatusOk();
            assertThat(r).bodyJson().extractingPath("$.items.length()").isEqualTo(7); // 익서스 30·40·55·60·70·75·110
            assertThat(r).bodyJson().extractingPath("$.items[*].brandName")
                    .asArray().containsOnly("Canon");
        }

        @Test
        void 띄어쓰기와_대소문자를_무시하고_대표_이름으로_찾으면_matchedAlias는_null() {
            MvcTestResult r = mvc.get().uri("/api/cameras/search").param("q", "ixus30").exchange();
            assertThat(r).bodyJson().extractingPath("$.items[0].slug").isEqualTo("canon-ixus-30");
            assertThat(r).bodyJson().extractingPath("$.items[0].matchedAlias").isNull();
        }

        @Test
        void 다른_나라_이름으로_찾으면_어떤_별칭으로_찾았는지_알려준다() {
            MvcTestResult r = mvc.get().uri("/api/cameras/search").param("q", "SD 200").exchange();
            assertThat(r).bodyJson().extractingPath("$.items[0].slug").isEqualTo("canon-ixus-30");
            assertThat(r).bodyJson().extractingPath("$.items[0].matchedAlias").isEqualTo("PowerShot SD200");
        }

        @Test
        void 국내_판매명으로도_찾는다() {
            MvcTestResult r = mvc.get().uri("/api/cameras/search").param("q", "케녹스").exchange();
            assertThat(r).bodyJson().extractingPath("$.items[0].slug").isEqualTo("samsung-kenox-s630");
        }

        @Test
        void 브랜드_이름으로_찾으면_그_브랜드_기종이_나온다() {
            MvcTestResult r = mvc.get().uri("/api/cameras/search").param("q", "캐논").exchange();
            assertThat(r).bodyJson().extractingPath("$.items.length()").isEqualTo(9);
        }

        @Test
        void LIKE_특수문자는_글자_그대로_검색된다() {
            MvcTestResult r = mvc.get().uri("/api/cameras/search").param("q", "%").exchange();
            assertThat(r).hasStatusOk();
            assertThat(r).bodyJson().extractingPath("$.items.length()").isEqualTo(0);
        }

        @Test
        void 검색어가_너무_길면_400() {
            MvcTestResult r = mvc.get().uri("/api/cameras/search").param("q", "가".repeat(51)).exchange();
            assertThat(r).hasStatus(400);
            assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        }
    }

    @Nested
    class 기종_정보 {

        @Test
        void 원래_해상도와_별칭을_보여준다() {
            MvcTestResult r = mvc.get().uri("/api/cameras/canon-ixus-30").exchange();
            assertThat(r).hasStatusOk();
            assertThat(r).bodyJson().extractingPath("$.nativeResolution").isEqualTo("2048×1536");
            assertThat(r).bodyJson().extractingPath("$.nameKo").isEqualTo("익서스 30");
            assertThat(r).bodyJson().extractingPath("$.brand.slug").isEqualTo("canon");
            // 대표 이름·한글 이름은 별칭 목록에서 뺀다
            assertThat(r).bodyJson().extractingPath("$.aliases").asArray()
                    .contains("PowerShot SD200")
                    .doesNotContain("IXUS 30", "익서스 30");
            assertThat(r).bodyJson().extractingPath("$.photoCount").isEqualTo(0);
        }

        @Test
        void 없는_기종은_404_CAMERA_NOT_FOUND() {
            MvcTestResult r = mvc.get().uri("/api/cameras/canon-ixus-999").exchange();
            assertThat(r).hasStatus(404);
            assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("CAMERA_NOT_FOUND");
        }
    }

    @Nested
    class 기종_작례_목록 {

        @Test
        void 작례가_없으면_빈_목록() {
            MvcTestResult r = mvc.get().uri("/api/cameras/canon-ixus-30/photos").exchange();
            assertThat(r).hasStatusOk();
            assertThat(r).bodyJson().extractingPath("$.items.length()").isEqualTo(0);
            assertThat(r).bodyJson().extractingPath("$.nextCursor").isNull();
        }

        @Test
        void 커서로_다음_페이지를_이어서_가져온다() throws Exception {
            insertCommonsPhotos("canon-ixus-30", 30);

            MvcTestResult first = mvc.get().uri("/api/cameras/canon-ixus-30/photos").exchange();
            assertThat(first).bodyJson().extractingPath("$.items.length()").isEqualTo(24);
            assertThat(first).bodyJson().extractingPath("$.items[0].credit").isEqualTo("작가 30"); // 최신순
            assertThat(first).bodyJson().extractingPath("$.items[0].thumbnailUrl")
                    .asString().endsWith("/thumbs/t30.jpg");
            String cursor = extract(first, "$.nextCursor");
            assertThat(cursor).isNotNull();

            MvcTestResult second = mvc.get().uri("/api/cameras/canon-ixus-30/photos").param("cursor", cursor).exchange();
            assertThat(second).bodyJson().extractingPath("$.items.length()").isEqualTo(6);
            assertThat(second).bodyJson().extractingPath("$.items[0].credit").isEqualTo("작가 6");
            assertThat(second).bodyJson().extractingPath("$.nextCursor").isNull();
        }

        @Test
        void 플래시_필터와_원본_크기_필터를_같이_쓸_수_있다() {
            insertCommonsPhotos("canon-ixus-30", 30); // 3의 배수 번째는 플래시, 짝수 번째는 원본 크기

            MvcTestResult flash = mvc.get().uri("/api/cameras/canon-ixus-30/photos")
                    .param("flash", "true").exchange();
            assertThat(flash).bodyJson().extractingPath("$.items.length()").isEqualTo(10);

            MvcTestResult both = mvc.get().uri("/api/cameras/canon-ixus-30/photos")
                    .param("flash", "true").param("originalOnly", "true").exchange();
            assertThat(both).bodyJson().extractingPath("$.items.length()").isEqualTo(5); // 6, 12, 18, 24, 30

            MvcTestResult detail = mvc.get().uri("/api/cameras/canon-ixus-30").exchange();
            assertThat(detail).bodyJson().extractingPath("$.photoCount").isEqualTo(30);
            assertThat(detail).bodyJson().extractingPath("$.flashPhotoCount").isEqualTo(10);
            assertThat(detail).bodyJson().extractingPath("$.originalSizePhotoCount").isEqualTo(15);
        }

        @Test
        void 페이지_크기가_범위를_벗어나거나_커서가_이상하면_400() {
            assertThat(mvc.get().uri("/api/cameras/canon-ixus-30/photos").param("size", "0").exchange()).hasStatus(400);
            assertThat(mvc.get().uri("/api/cameras/canon-ixus-30/photos").param("size", "49").exchange()).hasStatus(400);
            assertThat(mvc.get().uri("/api/cameras/canon-ixus-30/photos").param("cursor", "!!").exchange()).hasStatus(400);
            assertThat(mvc.get().uri("/api/cameras/canon-ixus-30/photos").param("flash", "maybe").exchange()).hasStatus(400);
        }
    }

    // ---------- 테스트 데이터 ----------

    /** Commons 작례 n장. i번째: 1분 간격, i%3==0이면 플래시, 짝수면 원본 크기 */
    @Nested
    class 작례_상세 {

        @Test
        void Commons_작례는_출처_표시_정보와_촬영_정보를_준다() {
            insertCommonsPhotos("canon-ixus-30", 1);
            long id = jdbc.sql("SELECT id FROM photo ORDER BY id DESC LIMIT 1").query(Long.class).single();
            jdbc.sql("""
                    UPDATE photo SET taken_at = '2007-05-03 18:22:10', f_number = 2.8, exposure_time = '1/60',
                                     iso = 100, focal_length = 5.4
                     WHERE id = :id
                    """).param("id", id).update();

            MvcTestResult r = mvc.get().uri("/api/photos/{id}", id).exchange();
            assertThat(r).hasStatusOk();
            assertThat(r).bodyJson().extractingPath("$.source").isEqualTo("COMMONS");
            assertThat(r).bodyJson().extractingPath("$.displayUrl").asString().endsWith("/display/d1.jpg");
            assertThat(r).bodyJson().extractingPath("$.camera.slug").isEqualTo("canon-ixus-30");
            assertThat(r).bodyJson().extractingPath("$.camera.brandName").isEqualTo("Canon");
            assertThat(r).bodyJson().extractingPath("$.exif.takenAt").isEqualTo("2007-05-03T18:22:10");
            assertThat(r).bodyJson().extractingPath("$.exif.exposureTime").isEqualTo("1/60");
            assertThat(r).bodyJson().extractingPath("$.exif.iso").isEqualTo(100);
            assertThat(r).bodyJson().extractingPath("$.uploader").isNull();
            assertThat(r).bodyJson().extractingPath("$.commons.author").isEqualTo("작가 1");
            assertThat(r).bodyJson().extractingPath("$.commons.licenseName").isEqualTo("CC BY-SA 4.0");
            assertThat(r).bodyJson().extractingPath("$.commons.modified").isEqualTo(true);
            assertThat(r).bodyJson().extractingPath("$.isMine").isEqualTo(false);
        }

        @Test
        void 삭제된_작례와_없는_작례는_404_PHOTO_NOT_FOUND() {
            insertCommonsPhotos("canon-ixus-30", 1);
            long id = jdbc.sql("SELECT id FROM photo ORDER BY id DESC LIMIT 1").query(Long.class).single();
            jdbc.sql("UPDATE photo SET deleted_at = now() WHERE id = :id").param("id", id).update();

            assertThat(mvc.get().uri("/api/photos/{id}", id).exchange()).bodyJson()
                    .extractingPath("$.code").isEqualTo("PHOTO_NOT_FOUND");
            assertThat(mvc.get().uri("/api/photos/999999999").exchange()).hasStatus(404);
            assertThat(mvc.post().uri("/api/photos/{id}/views", id).exchange()).hasStatus(404);
        }

        @Test
        void 조회수가_오른다() {
            insertCommonsPhotos("canon-ixus-30", 1);
            long id = jdbc.sql("SELECT id FROM photo ORDER BY id DESC LIMIT 1").query(Long.class).single();

            assertThat(mvc.post().uri("/api/photos/{id}/views", id).exchange()).hasStatus(204);
            assertThat(mvc.post().uri("/api/photos/{id}/views", id).exchange()).hasStatus(204);
            int views = jdbc.sql("SELECT view_count FROM photo WHERE id = :id").param("id", id)
                    .query(Integer.class).single();
            assertThat(views).isEqualTo(2);
        }

        @Test
        void 이전_다음은_격자_순서와_필터를_따른다() throws Exception {
            insertCommonsPhotos("canon-ixus-30", 6); // 6이 가장 최신. 3·6은 플래시
            MvcTestResult list = mvc.get().uri("/api/cameras/canon-ixus-30/photos").exchange();
            long first = Long.parseLong(extract(list, "$.items[0].id"));
            long second = Long.parseLong(extract(list, "$.items[1].id"));
            long last = Long.parseLong(extract(list, "$.items[5].id"));

            MvcTestResult r = mvc.get().uri("/api/photos/{id}/adjacent", second).exchange();
            assertThat(r).hasStatusOk();
            assertThat(r).bodyJson().extractingPath("$.prev.id").isEqualTo((int) first);
            assertThat(r).bodyJson().extractingPath("$.next.id").isEqualTo((int) Long.parseLong(extract(list, "$.items[2].id")));
            assertThat(r).bodyJson().extractingPath("$.prev.commons.author").isEqualTo("작가 6");

            assertThat(mvc.get().uri("/api/photos/{id}/adjacent", first).exchange()).bodyJson()
                    .extractingPath("$.prev").isNull();
            assertThat(mvc.get().uri("/api/photos/{id}/adjacent", last).exchange()).bodyJson()
                    .extractingPath("$.next").isNull();

            // 플래시 필터: 6 다음은 3
            MvcTestResult f = mvc.get().uri("/api/photos/{id}/adjacent", first).param("flash", "true").exchange();
            assertThat(f).bodyJson().extractingPath("$.next.commons.author").isEqualTo("작가 3");
        }

        @Test
        void 숫자가_아닌_id는_400() {
            assertThat(mvc.get().uri("/api/photos/abc").exchange()).hasStatus(400);
        }
    }

    private void insertCommonsPhotos(String cameraSlug, int n) {
        long cameraId = jdbc.sql("SELECT id FROM camera WHERE slug = :s").param("s", cameraSlug).query(Long.class).single();
        Instant base = Instant.parse("2026-11-01T00:00:00Z");
        for (int i = 1; i <= n; i++) {
            long photoId = jdbc.sql("""
                            INSERT INTO photo (source, camera_id, flash_fired, is_original_size, width, height,
                                               display_key, thumbnail_key, created_at)
                            VALUES ('COMMONS', :camera, :flash, :orig, 2048, 1536, :display, :thumb, :at)
                            RETURNING id
                            """)
                    .params(Map.of("camera", cameraId, "flash", i % 3 == 0, "orig", i % 2 == 0,
                            "display", "display/d" + i + ".jpg", "thumb", "thumbs/t" + i + ".jpg",
                            "at", OffsetDateTime.ofInstant(base.plusSeconds(60L * i), ZoneOffset.UTC)))
                    .query(Long.class).single();
            jdbc.sql("""
                            INSERT INTO commons_meta (photo_id, commons_page_id, file_title, page_url, author,
                                                      license_name, source_category)
                            VALUES (:id, :page, :title, :url, :author, 'CC BY-SA 4.0', 'Canon Digital IXUS 30')
                            """)
                    .params(Map.of("id", photoId, "page", 900000L + i, "title", "File:t" + i + ".jpg",
                            "url", "https://commons.wikimedia.org/wiki/File:t" + i + ".jpg", "author", "작가 " + i))
                    .update();
        }
    }

    private static String extract(MvcTestResult r, String path) throws Exception {
        Object v = JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
        return v == null ? null : v.toString();
    }
}
