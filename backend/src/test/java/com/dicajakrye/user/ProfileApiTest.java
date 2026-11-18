package com.dicajakrye.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.dicajakrye.TestSessions;
import com.dicajakrye.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 홈 "최근 올라온 작례"와 프로필 (API.md 3·6장). 작례는 DB에 직접 넣는다 (파일은 필요 없음) */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @SuppressWarnings("rawtypes")
    @Autowired
    SessionRepository sessions;

    @Test
    void 최근_올라온_작례는_기종이_정해진_사용자_업로드만_최신순() {
        long me = insertUser("디카러버");
        long a = insertUpload(me, "canon-ixus-30", 1);
        long b = insertUpload(me, "sony-dsc-t1", 2);
        insertUpload(me, null, 3);                                    // 미분류: 홈에서 뺌
        long deleted = insertUpload(me, "canon-ixus-30", 4);
        jdbc.sql("UPDATE photo SET deleted_at = now() WHERE id = :id").param("id", deleted).update();

        MvcTestResult r = mvc.get().uri("/api/photos/recent").exchange();
        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly((int) b, (int) a);
        assertThat(r).bodyJson().extractingPath("$.items[0].camera.slug").isEqualTo("sony-dsc-t1");
        assertThat(r).bodyJson().extractingPath("$.items[0].credit").isEqualTo("디카러버");
        assertThat(r).bodyJson().extractingPath("$.items[*].source").asArray().containsOnly("UPLOAD");
    }

    @Test
    void 프로필과_프로필_피드는_미분류도_보여주고_페이지로_나눈다() throws Exception {
        long me = insertUser("디카러버");
        long other = insertUser("똑딱이");
        long a = insertUpload(me, "canon-ixus-30", 1);
        long unclassified = insertUpload(me, null, 2);
        insertUpload(other, "canon-ixus-30", 3);

        MvcTestResult profile = mvc.get().uri("/api/users/{n}", "디카러버").exchange();
        assertThat(profile).hasStatusOk();
        assertThat(profile).bodyJson().extractingPath("$.nickname").isEqualTo("디카러버");
        assertThat(profile).bodyJson().extractingPath("$.photoCount").isEqualTo(2);
        assertThat(profile).bodyJson().extractingPath("$.isMe").isEqualTo(false);
        assertThat(mvc.get().uri("/api/users/{n}", "디카러버").cookie(TestSessions.loggedIn(sessions, me)).exchange())
                .bodyJson().extractingPath("$.isMe").isEqualTo(true);

        MvcTestResult first = mvc.get().uri("/api/users/{n}/photos", "디카러버").param("size", "1").exchange();
        assertThat(first).bodyJson().extractingPath("$.items[0].id").isEqualTo((int) unclassified);
        assertThat(first).bodyJson().extractingPath("$.items[0].camera").isNull();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");
        MvcTestResult second = mvc.get().uri("/api/users/{n}/photos", "디카러버").param("size", "1")
                .param("cursor", cursor).exchange();
        assertThat(second).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly((int) a);
        assertThat(second).bodyJson().extractingPath("$.nextCursor").isNull();
    }

    @Test
    void 없거나_탈퇴했거나_규칙에_안_맞는_닉네임은_404_USER_NOT_FOUND() {
        long gone = insertUser("떠난사람");
        jdbc.sql("UPDATE users SET deleted_at = now() WHERE id = :id").param("id", gone).update();

        assertThat(mvc.get().uri("/api/users/{n}", "떠난사람").exchange())
                .bodyJson().extractingPath("$.code").isEqualTo("USER_NOT_FOUND");
        assertThat(mvc.get().uri("/api/users/{n}", "없는사람").exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/users/{n}/photos", "a b").exchange()).hasStatus(404);
    }

    // ---------- 도우미 ----------

    private long insertUser(String nickname) {
        return jdbc.sql("INSERT INTO users (provider, provider_user_id, nickname) VALUES ('GOOGLE', :n, :n) RETURNING id")
                .param("n", nickname).query(Long.class).single();
    }

    /** order가 클수록 최근 */
    private long insertUpload(long uploaderId, String cameraSlug, int order) {
        Map<String, Object> p = new HashMap<>();
        p.put("u", uploaderId);
        p.put("slug", cameraSlug);
        p.put("at", OffsetDateTime.of(2026, 11, 1, 0, order, 0, 0, ZoneOffset.UTC));
        p.put("k", "uploads/thumb/t" + order + "-" + uploaderId + ".jpg");
        return jdbc.sql("""
                        INSERT INTO photo (source, uploader_id, camera_id, original_key, display_key, thumbnail_key, created_at)
                        VALUES ('UPLOAD', :u, (SELECT id FROM camera WHERE slug = :slug), 'originals/x.jpg', :k, :k, :at)
                        RETURNING id
                        """).params(p).query(Long.class).single();
    }
}
