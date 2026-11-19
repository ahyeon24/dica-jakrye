package com.dicajakrye.camera;

import static com.dicajakrye.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.dicajakrye.TestSessions;
import com.dicajakrye.TestcontainersConfiguration;
import com.dicajakrye.common.TextNormalizer;
import com.dicajakrye.upload.CameraMatcher;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 캐논 내부 번호로 기종 찾기, 미분류 작례 연결 (PRD F5, API.md 8장, CAMERAS.md 6장) */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UnclassifiedApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CameraMatcher matcher;

    @SuppressWarnings("rawtypes")
    @Autowired
    SessionRepository sessions;

    @Test
    void 모델명이_처음_보는_이름이어도_캐논_내부_번호가_같으면_그_기종으로_찾는다() {
        assertThat(matcher.find("Canon", "Canon IXY DIGITAL 999", 0x01550000L)).get()
                .extracting(CameraMatcher.Match::slug).isEqualTo("canon-ixus-30");
        // 번호를 모르거나, 등록 안 된 번호거나, 캐논이 아니면 못 찾는다
        assertThat(matcher.find("Canon", "Canon IXY DIGITAL 999", null)).isEmpty();
        assertThat(matcher.find("Canon", "Canon IXY DIGITAL 999", 0x09990000L)).isEmpty();
        assertThat(matcher.find("NIKON", "COOLPIX X", 0x01550000L)).isEmpty();
        // 모델명이 맞으면 번호보다 모델명이 먼저
        assertThat(matcher.find("Canon", "Canon PowerShot G9", 0x01550000L)).get()
                .extracting(CameraMatcher.Match::slug).isEqualTo("canon-powershot-g9");
    }

    @Test
    void 내부_번호로_확인된_일본판_이름은_번호가_없어도_찾는다() {
        assertThat(matcher.find("Canon", "Canon IXY DIGITAL 510 IS", null)).get()
                .extracting(CameraMatcher.Match::slug).isEqualTo("canon-ixus-110-is");
        assertThat(matcher.find("Canon", "Canon IXY DIGITAL 40", null)).get()
                .extracting(CameraMatcher.Match::slug).isEqualTo("canon-ixus-30");
        // 이름은 비슷해도 다른 기종이다 (번호 0x02640000)
        assertThat(matcher.find("Canon", "Canon IXY DIGITAL 110 IS", null)).isEmpty();
    }

    @Test
    void 관리자만_볼_수_있다() {
        assertThat(mvc.get().uri("/api/admin/unclassified").exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/api/admin/unclassified").cookie(session(insertUser("디카러버", "USER"))).exchange())
                .hasStatus(403);
        assertThat(link(session(insertUser("디카러버2", "USER")), "{\"exifMake\":\"Canon\",\"exifModel\":\"x\",\"cameraSlug\":\"canon-ixus-30\"}"))
                .hasStatus(403);
    }

    @Test
    void 미분류_목록은_EXIF_모델별로_묶고_후보를_추천한다() {
        long owner = insertUser("디카러버", "USER");
        insertUnclassified(owner, "Canon", "Canon IXUS 145", null, 4608, 3456);
        insertUnclassified(owner, "Canon", "Canon IXUS 145", null, 4000, 3000);
        insertUnclassified(owner, "Canon", "Canon IXY DIGITAL 999", 0x01550000L, 2048, 1536);
        insertUnclassified(owner, "SONY", "DSC-T10 (Black)", null, 3072, 2304);

        MvcTestResult r = mvc.get().uri("/api/admin/unclassified").cookie(admin()).exchange();
        assertThat(r).hasStatusOk();
        // 많은 것부터
        assertThat(r).bodyJson().extractingPath("$[0].exifModel").isEqualTo("Canon IXUS 145");
        assertThat(r).bodyJson().extractingPath("$[0].count").isEqualTo(2);
        assertThat(r).bodyJson().extractingPath("$[0].maxWidth").isEqualTo(4608);
        assertThat(r).bodyJson().extractingPath("$[0].brandSlug").isEqualTo("canon");
        assertThat(r).bodyJson().extractingPath("$[0].samples.length()").isEqualTo(2);
        // 숫자 조각이 겹치는 기종이 없으면 추천하지 않는다 (IXUS 30을 추천하지 않음)
        assertThat(r).bodyJson().extractingPath("$[0].candidates").asArray().isEmpty();
        // 캐논 내부 번호가 같은 기종
        assertThat(r).bodyJson().extractingPath("$[?(@.exifModel == 'Canon IXY DIGITAL 999')].candidates[0].slug")
                .asArray().containsExactly("canon-ixus-30");
        assertThat(r).bodyJson().extractingPath("$[?(@.exifModel == 'Canon IXY DIGITAL 999')].candidates[0].reason")
                .asArray().containsExactly("MAKER_MODEL_ID");
        assertThat(r).bodyJson().extractingPath("$[?(@.exifModel == 'Canon IXY DIGITAL 999')].makerModelId")
                .asArray().containsExactly("0x01550000");
        // 같은 브랜드에서 숫자 조각(t10)이 겹치는 기종
        assertThat(r).bodyJson().extractingPath("$[?(@.exifModel == 'DSC-T10 (Black)')].candidates[0].slug")
                .asArray().containsExactly("sony-dsc-t10");
    }

    @Test
    void 기존_기종에_연결하면_같은_이름과_같은_내부_번호의_작례가_모두_옮겨지고_다음부터는_자동으로_붙는다() {
        long owner = insertUser("디카러버", "USER");
        long a = insertUnclassified(owner, "Canon", "Canon IXY DIGITAL 777", 0x07770000L, 2048, 1536);
        long b = insertUnclassified(owner, "Canon", "Canon IXY DIGITAL 777", 0x07770000L, 800, 600);
        long c = insertUnclassified(owner, "Canon", "Canon PowerShot SD777", 0x07770000L, 2048, 1536); // 같은 번호, 다른 이름
        long other = insertUnclassified(owner, "Canon", "Canon IXUS 145", null, 4608, 3456);

        MvcTestResult r = link(admin(), """
                {"exifMake":"Canon","exifModel":"Canon IXY DIGITAL 777","cameraSlug":"canon-ixus-30"}""");
        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.cameraSlug").isEqualTo("canon-ixus-30");
        assertThat(r).bodyJson().extractingPath("$.moved").isEqualTo(3);

        assertThat(cameraOf(a)).isEqualTo("canon-ixus-30");
        assertThat(cameraOf(c)).isEqualTo("canon-ixus-30");
        assertThat(cameraOf(other)).isEqualTo("미분류");
        // 원본 크기 여부를 IXUS 30 해상도(2048)로 다시 계산
        assertThat(originalSize(a)).isTrue();
        assertThat(originalSize(b)).isFalse();
        // 최근 올라온 작례에 나온다
        assertThat(mvc.get().uri("/api/photos/recent").exchange())
                .bodyJson().extractingPath("$.items[?(@.id == " + a + ")]").asArray().hasSize(1);
        // 다음부터는 이름으로도, 내부 번호로도 자동으로 붙는다
        assertThat(matcher.find("Canon", "Canon IXY DIGITAL 777", null)).get()
                .extracting(CameraMatcher.Match::slug).isEqualTo("canon-ixus-30");
        assertThat(matcher.find("Canon", "Canon 다른 이름", 0x07770000L)).get()
                .extracting(CameraMatcher.Match::slug).isEqualTo("canon-ixus-30");
    }

    @Test
    void 새_기종을_만들어_연결한다() {
        long owner = insertUser("디카러버", "USER");
        long p = insertUnclassified(owner, "Canon", "Canon IXUS 145", null, 4608, 3456);

        MvcTestResult r = link(admin(), """
                {"exifMake":"Canon","exifModel":"Canon IXUS 145",
                 "newCamera":{"brandSlug":"canon","name":"IXUS 145","nameKo":"익서스 145","sensorType":"CCD",
                              "nativeWidth":4608,"nativeHeight":3456,"releaseYear":2014}}""");
        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.cameraSlug").isEqualTo("canon-ixus-145");
        assertThat(r).bodyJson().extractingPath("$.moved").isEqualTo(1);
        assertThat(cameraOf(p)).isEqualTo("canon-ixus-145");
        assertThat(originalSize(p)).isTrue();

        assertThat(mvc.get().uri("/api/cameras/canon-ixus-145").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/api/cameras/search?q=익서스145").exchange())
                .bodyJson().extractingPath("$.items[0].slug").isEqualTo("canon-ixus-145");
        assertThat(mvc.get().uri("/api/admin/unclassified").cookie(admin()).exchange())
                .bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void 연결할_수_없는_경우() {
        Cookie admin = admin();
        // 이미 다른 기종에 연결된 EXIF 값
        assertThat(link(admin, """
                {"exifMake":"Canon","exifModel":"Canon DIGITAL IXUS 30","cameraSlug":"canon-ixus-40"}"""))
                .bodyJson().extractingPath("$.code").isEqualTo("EXIF_MODEL_ALREADY_MAPPED");
        // 같은 이름의 기종이 이미 있음
        assertThat(link(admin, """
                {"exifMake":"Canon","exifModel":"Canon X","newCamera":{"brandSlug":"canon","name":"IXUS 30"}}"""))
                .bodyJson().extractingPath("$.code").isEqualTo("CAMERA_ALREADY_EXISTS");
        // 기존 기종과 새 기종을 같이 보내거나 둘 다 안 보냄
        assertThat(link(admin, """
                {"exifMake":"Canon","exifModel":"Canon X","cameraSlug":"canon-ixus-30","newCamera":{"brandSlug":"canon","name":"X"}}"""))
                .hasStatus(400);
        assertThat(link(admin, "{\"exifMake\":\"Canon\",\"exifModel\":\"Canon X\"}")).hasStatus(400);
        // 없는 기종·브랜드, 잘못된 값
        assertThat(link(admin, "{\"exifMake\":\"Canon\",\"exifModel\":\"Canon X\",\"cameraSlug\":\"nope\"}")).hasStatus(404);
        assertThat(link(admin, """
                {"exifMake":"Canon","exifModel":"Canon X","newCamera":{"brandSlug":"nope","name":"X"}}""")).hasStatus(404);
        assertThat(link(admin, """
                {"exifMake":"Canon","exifModel":"Canon X","newCamera":{"brandSlug":"canon","name":"X","sensorType":"LCD"}}"""))
                .hasStatus(400);
        assertThat(link(admin, """
                {"exifMake":"Canon","exifModel":"Canon X","newCamera":{"brandSlug":"canon","name":"X","nativeWidth":100}}"""))
                .hasStatus(400);
        // 실패한 요청은 아무것도 남기지 않는다
        assertThat(jdbc.sql("SELECT count(*) FROM camera WHERE slug = 'canon-x'").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM camera_exif_model WHERE exif_model = 'canon x'").query(Integer.class).single())
                .isZero();
    }

    // ---------- 도우미 ----------

    private MvcTestResult link(Cookie session, String json) {
        return mvc.post().uri("/api/admin/exif-models").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(json).with(csrf()).exchange();
    }

    private Cookie admin() {
        Long id = jdbc.sql("SELECT id FROM users WHERE nickname = '관리하는사람'").query(Long.class).optional()
                .orElseGet(() -> insertUser("관리하는사람", "ADMIN"));
        return session(id);
    }

    private Cookie session(long userId) {
        return TestSessions.loggedIn(sessions, userId);
    }

    private long insertUser(String nickname, String role) {
        return jdbc.sql("INSERT INTO users (provider, provider_user_id, nickname, role) VALUES ('GOOGLE', :n, :n, :r) RETURNING id")
                .param("n", nickname).param("r", role).query(Long.class).single();
    }

    private long insertUnclassified(long uploaderId, String make, String model, Long makerModelId, int width, int height) {
        return jdbc.sql("""
                        INSERT INTO photo (source, uploader_id, camera_id, exif_make_raw, exif_model_raw, exif_make, exif_model,
                                           maker_model_id, width, height, original_key, display_key, thumbnail_key)
                        VALUES ('UPLOAD', :u, NULL, :makeRaw, :modelRaw, :make, :model, :mid, :w, :h,
                                'originals/t.jpg', 'uploads/display/t.jpg', 'uploads/thumb/t.jpg')
                        RETURNING id
                        """)
                .param("u", uploaderId)
                .param("makeRaw", make).param("modelRaw", model)
                .param("make", TextNormalizer.exif(make)).param("model", TextNormalizer.exif(model))
                .param("mid", makerModelId)
                .param("w", width).param("h", height)
                .query(Long.class).single();
    }

    private String cameraOf(long photoId) {
        return jdbc.sql("SELECT coalesce(c.slug, '미분류') FROM photo p LEFT JOIN camera c ON c.id = p.camera_id WHERE p.id = :id")
                .param("id", photoId).query(String.class).single();
    }

    private Boolean originalSize(long photoId) {
        return jdbc.sql("SELECT is_original_size FROM photo WHERE id = :id").param("id", photoId).query(Boolean.class).single();
    }
}
