package com.dicajakrye.report;

import static com.dicajakrye.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.dicajakrye.TestSessions;
import com.dicajakrye.TestcontainersConfiguration;
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

/** 신고와 관리자 처리 (PRD F8, API.md 5·8장) */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @SuppressWarnings("rawtypes")
    @Autowired
    SessionRepository sessions;

    @Test
    void 초상권_신고는_접수_즉시_가리고_올린_사람에게만_보인다() {
        long owner = insertUser("디카러버", "USER");
        long reporter = insertUser("신고자", "USER");
        long photo = insertUpload(owner, "canon-ixus-30");
        int countBefore = cameraCount("canon-ixus-30");

        MvcTestResult r = report(reporter, photo, "PORTRAIT_RIGHTS", "제가 나온 사진인데 동의한 적 없어요.");
        assertThat(r).hasStatus(201);
        assertThat(r).bodyJson().extractingPath("$.hidden").isEqualTo(true);

        // 다른 사람에게는 없는 작례
        assertThat(mvc.get().uri("/api/photos/{id}", photo).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/photos/{id}", photo).cookie(session(reporter)).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/cameras/canon-ixus-30/photos").exchange())
                .bodyJson().extractingPath("$.items[?(@.id == " + photo + ")]").asArray().isEmpty();
        assertThat(cameraCount("canon-ixus-30")).isEqualTo(countBefore - 1);
        assertThat(mvc.get().uri("/api/users/{n}", "디카러버").exchange())
                .bodyJson().extractingPath("$.photoCount").isEqualTo(0);
        // 올린 사람에게는 보이고, 가려졌다고 알려 준다
        MvcTestResult mine = mvc.get().uri("/api/photos/{id}", photo).cookie(session(owner)).exchange();
        assertThat(mine).hasStatusOk();
        assertThat(mine).bodyJson().extractingPath("$.hiddenByReport").isEqualTo(true);
        assertThat(mvc.get().uri("/api/me/hidden-photos").cookie(session(owner)).exchange())
                .bodyJson().extractingPath("$.items[0].id").isEqualTo((int) photo);
    }

    @Test
    void 다른_사유는_가리지_않는다() {
        long owner = insertUser("디카러버", "USER");
        long photo = insertUpload(owner, "canon-ixus-30");

        MvcTestResult r = report(insertUser("신고자", "USER"), photo, "WRONG_CAMERA", null);
        assertThat(r).bodyJson().extractingPath("$.hidden").isEqualTo(false);
        assertThat(mvc.get().uri("/api/photos/{id}", photo).exchange()).hasStatusOk();
    }

    @Test
    void 신고할_수_없는_경우() {
        long owner = insertUser("디카러버", "USER");
        long reporter = insertUser("신고자", "USER");
        long photo = insertUpload(owner, "canon-ixus-30");

        assertThat(report(owner, photo, "INAPPROPRIATE", null))
                .bodyJson().extractingPath("$.code").isEqualTo("CANNOT_REPORT_OWN_PHOTO");
        assertThat(report(reporter, photo, "OTHER", "  ")).hasStatus(400);          // 기타는 설명 필수
        assertThat(report(reporter, photo, "NOPE", null)).hasStatus(400);           // 없는 사유
        assertThat(report(reporter, photo, "OTHER", "가".repeat(501))).hasStatus(400);
        assertThat(report(reporter, photo, "INAPPROPRIATE", null)).hasStatus(201);
        assertThat(report(reporter, photo, "INAPPROPRIATE", null))
                .bodyJson().extractingPath("$.code").isEqualTo("ALREADY_REPORTED");
        assertThat(mvc.post().uri("/api/photos/{id}/reports", photo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"INAPPROPRIATE\"}").with(csrf()).exchange()).hasStatus(401);
    }

    @Test
    void 하루_10건까지만_신고할_수_있다() {
        long owner = insertUser("디카러버", "USER");
        long reporter = insertUser("신고자", "USER");
        for (int i = 0; i < 10; i++) {
            assertThat(report(reporter, insertUpload(owner, "canon-ixus-30"), "WRONG_CAMERA", null)).hasStatus(201);
        }
        MvcTestResult r = report(reporter, insertUpload(owner, "canon-ixus-30"), "WRONG_CAMERA", null);
        assertThat(r).hasStatus(429);
        assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("REPORT_RATE_LIMITED");
    }

    @Test
    void 관리자만_신고를_처리할_수_있다() {
        long user = insertUser("디카러버", "USER");
        assertThat(mvc.get().uri("/api/admin/reports").cookie(session(user)).exchange()).hasStatus(403);
        assertThat(mvc.get().uri("/api/admin/reports").exchange()).hasStatus(401);

        // 권한은 요청마다 DB에서 본다: 회수하면 바로 막힌다
        long admin = insertUser("관리하는사람", "ADMIN");
        Cookie adminSession = session(admin);
        assertThat(mvc.get().uri("/api/admin/reports").cookie(adminSession).exchange()).hasStatusOk();
        jdbc.sql("UPDATE users SET role = 'USER' WHERE id = :id").param("id", admin).update();
        assertThat(mvc.get().uri("/api/admin/reports").cookie(adminSession).exchange()).hasStatus(403);
    }

    @Test
    void 기각하면_다시_보이고_삭제하면_사라진다() {
        long owner = insertUser("디카러버", "USER");
        long r1 = insertUser("신고자1", "USER");
        long r2 = insertUser("신고자2", "USER");
        long admin = insertUser("관리하는사람", "ADMIN");
        long a = insertUpload(owner, "canon-ixus-30");
        long b = insertUpload(owner, "canon-ixus-30");
        report(r1, a, "PORTRAIT_RIGHTS", null);
        report(r2, a, "INAPPROPRIATE", null);   // 가려진 작례는 신고할 수 없음 → 404
        report(r1, b, "STOLEN", "제 사진이에요");

        MvcTestResult list = mvc.get().uri("/api/admin/reports").cookie(session(admin)).exchange();
        assertThat(list).bodyJson().extractingPath("$.length()").isEqualTo(2);
        assertThat(list).bodyJson().extractingPath("$[0].photoId").isEqualTo((int) a);
        assertThat(list).bodyJson().extractingPath("$[0].hidden").isEqualTo(true);
        assertThat(list).bodyJson().extractingPath("$[0].reports[0].reasonLabel").isEqualTo("초상권 침해");

        // 기각: 다시 공개, 신고 REJECTED
        assertThat(adminPost(admin, "/api/admin/reports/photos/" + a + "/reject", null)).hasStatus(204);
        assertThat(mvc.get().uri("/api/photos/{id}", a).exchange()).hasStatusOk();
        assertThat(reportStatuses(a)).containsOnly("REJECTED");
        assertThat(adminPost(admin, "/api/admin/reports/photos/" + a + "/reject", null)).hasStatus(404); // 남은 신고 없음

        // 삭제: 사라짐, 신고 RESOLVED/DELETED
        assertThat(adminPost(admin, "/api/admin/reports/photos/" + b + "/delete", null)).hasStatus(204);
        assertThat(mvc.get().uri("/api/photos/{id}", b).cookie(session(owner)).exchange()).hasStatus(404);
        assertThat(reportStatuses(b)).containsOnly("RESOLVED");
        assertThat(mvc.get().uri("/api/admin/reports").cookie(session(admin)).exchange())
                .bodyJson().extractingPath("$.length()").isEqualTo(0);
    }

    @Test
    void 기종_틀림은_기종을_바꿔_처리한다() {
        long owner = insertUser("디카러버", "USER");
        long admin = insertUser("관리하는사람", "ADMIN");
        long photo = insertUpload(owner, "canon-ixus-30");
        report(insertUser("신고자", "USER"), photo, "WRONG_CAMERA", null);

        assertThat(adminPost(admin, "/api/admin/reports/photos/" + photo + "/change-camera",
                "{\"cameraSlug\":\"sony-dsc-t1\"}")).hasStatus(204);
        assertThat(mvc.get().uri("/api/photos/{id}", photo).exchange())
                .bodyJson().extractingPath("$.camera.slug").isEqualTo("sony-dsc-t1");
        assertThat(adminPost(admin, "/api/admin/reports/photos/" + photo + "/change-camera",
                "{\"cameraSlug\":\"nope\"}")).hasStatus(404);
    }

    // ---------- 도우미 ----------

    private MvcTestResult report(long reporterId, long photoId, String reason, String detail) {
        String json = "{\"reason\":\"" + reason + "\"" + (detail == null ? "" : ",\"detail\":\"" + detail + "\"") + "}";
        return mvc.post().uri("/api/photos/{id}/reports", photoId).cookie(session(reporterId))
                .contentType(MediaType.APPLICATION_JSON).content(json).with(csrf()).exchange();
    }

    private MvcTestResult adminPost(long adminId, String path, String json) {
        var req = mvc.post().uri(path).cookie(session(adminId)).with(csrf());
        if (json != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return req.exchange();
    }

    private Cookie session(long userId) {
        return TestSessions.loggedIn(sessions, userId);
    }

    private long insertUser(String nickname, String role) {
        return jdbc.sql("INSERT INTO users (provider, provider_user_id, nickname, role) VALUES ('GOOGLE', :n, :n, :r) RETURNING id")
                .param("n", nickname).param("r", role).query(Long.class).single();
    }

    private long insertUpload(long uploaderId, String cameraSlug) {
        return jdbc.sql("""
                        INSERT INTO photo (source, uploader_id, camera_id, width, height, original_key, display_key, thumbnail_key)
                        VALUES ('UPLOAD', :u, (SELECT id FROM camera WHERE slug = :slug), 400, 300,
                                'originals/report-test.jpg', 'uploads/display/report-test.jpg', 'uploads/thumb/report-test.jpg')
                        RETURNING id
                        """).param("u", uploaderId).param("slug", cameraSlug).query(Long.class).single();
    }

    private int cameraCount(String slug) {
        try {
            String json = mvc.get().uri("/api/cameras/{s}", slug).exchange().getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            Object count = com.jayway.jsonpath.JsonPath.read(json, "$.photoCount");
            return ((Number) count).intValue();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private java.util.List<String> reportStatuses(long photoId) {
        return jdbc.sql("SELECT status FROM report WHERE photo_id = :id").param("id", photoId).query(String.class).list();
    }
}
