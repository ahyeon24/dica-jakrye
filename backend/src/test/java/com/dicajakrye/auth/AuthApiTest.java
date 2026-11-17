package com.dicajakrye.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static com.dicajakrye.TestCsrf.csrf;

import com.dicajakrye.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인·가입·CSRF 통합 테스트. 세션은 실제처럼 DB(spring_session)에 저장되고 SESSION 쿠키로 오간다.
 * 구글·카카오 서버와 주고받는 부분은 Spring Security가 처리하므로, 그 뒤(로그인 성공 처리)부터 확인한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    static final String SIGNUP_BODY = """
            {"nickname": "%s", "agreedTerms": [
              {"type": "TERMS_OF_SERVICE", "version": "2026-11-01"},
              {"type": "PRIVACY_COLLECTION", "version": "2026-11-01"}]}
            """;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    // JDBC 세션 구현 클래스가 공개되지 않아 원시 타입으로 받는다
    @SuppressWarnings("rawtypes")
    @Autowired
    SessionRepository sessions;

    @Autowired
    OAuthLoginSuccessHandler successHandler;

    /** 로그인 성공 처리를 직접 부르면 테스트 스레드에 로그인 상태가 남아 다음 요청에 섞이므로 비운다 */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class 로그인_필요 {

        @Test
        void 로그인_안_했으면_me는_401_UNAUTHORIZED() {
            MvcTestResult r = mvc.get().uri("/api/me").exchange();
            assertThat(r).hasStatus(401);
            assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
        }

        @Test
        void CSRF_토큰_없는_쓰기_요청은_403_CSRF_TOKEN_INVALID() {
            MvcTestResult r = mvc.post().uri("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY.formatted("디카러버")).exchange();
            assertThat(r).hasStatus(403);
            assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("CSRF_TOKEN_INVALID");
        }

        @Test
        void 쿠키와_헤더의_CSRF_토큰이_다르면_403() {
            MvcTestResult r = mvc.post().uri("/api/auth/logout")
                    .cookie(new Cookie("XSRF-TOKEN", "a")).header("X-XSRF-TOKEN", "b").exchange();
            assertThat(r).hasStatus(403);
            assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("CSRF_TOKEN_INVALID");
        }

        @Test
        void csrf_주소를_부르면_XSRF_TOKEN_쿠키를_받는다() {
            MvcTestResult r = mvc.get().uri("/api/csrf").exchange();
            assertThat(r).hasStatus(204);
            Cookie cookie = r.getResponse().getCookie("XSRF-TOKEN");
            assertThat(cookie).isNotNull();
            assertThat(cookie.isHttpOnly()).isFalse(); // 프론트 JS가 읽어서 헤더에 넣어야 한다
        }

        @Test
        void 로그인_안_한_방문자에게는_세션을_만들지_않는다() {
            int before = sessionCount();
            mvc.get().uri("/api/brands").exchange();
            mvc.get().uri("/api/me").exchange();
            assertThat(sessionCount()).isEqualTo(before);
        }
    }

    @Nested
    class 소셜_로그인_성공_처리 {

        @Test
        void 처음_온_사람은_회원을_만들지_않고_가입_화면으로() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.getSession().setAttribute(AuthSession.REDIRECT, "/cameras/canon-ixus-30");
            MockHttpServletResponse res = new MockHttpServletResponse();

            successHandler.onAuthenticationSuccess(req, res, kakaoToken(4242L));

            assertThat(res.getRedirectedUrl()).isEqualTo("http://localhost:3000/signup");
            assertThat(req.getSession().getAttribute(AuthSession.PENDING_SIGNUP))
                    .isEqualTo(new PendingSignup("KAKAO", "4242"));
            assertThat(userCount()).isZero();
            assertThat(CurrentUser.id()).isEmpty();
        }

        @Test
        void 가입한_회원은_로그인되고_원래_페이지로() throws Exception {
            long userId = insertUser("KAKAO", "4242", "디카러버");
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.getSession().setAttribute(AuthSession.REDIRECT, "/cameras/canon-ixus-30");
            MockHttpServletResponse res = new MockHttpServletResponse();

            successHandler.onAuthenticationSuccess(req, res, kakaoToken(4242L));

            assertThat(res.getRedirectedUrl()).isEqualTo("http://localhost:3000/cameras/canon-ixus-30");
            var context = (SecurityContextImpl) req.getSession()
                    .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            assertThat(context.getAuthentication().getPrincipal()).isEqualTo(new LoginUser(userId));
        }

        private OAuth2AuthenticationToken kakaoToken(long kakaoId) {
            var authorities = List.of(new SimpleGrantedAuthority("OAUTH2_USER"));
            var user = new DefaultOAuth2User(authorities, Map.of("id", kakaoId), "id");
            return new OAuth2AuthenticationToken(user, authorities, "kakao");
        }
    }

    @Nested
    class 가입 {

        @Test
        void 가입_대기_정보가_없으면_401_SIGNUP_SESSION_NOT_FOUND() {
            MvcTestResult r = mvc.get().uri("/api/auth/signup").exchange();
            assertThat(r).hasStatus(401);
            assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("SIGNUP_SESSION_NOT_FOUND");
        }

        @Test
        void 닉네임과_약관_동의로_가입하면_로그인된다() {
            Cookie pending = pendingSession("KAKAO", "4242", "/cameras/canon-ixus-30");

            MvcTestResult info = mvc.get().uri("/api/auth/signup").cookie(pending).exchange();
            assertThat(info).hasStatusOk();
            assertThat(info).bodyJson().extractingPath("$.provider").isEqualTo("KAKAO");
            assertThat(info).bodyJson().extractingPath("$.terms.length()").isEqualTo(2);
            assertThat(info).bodyJson().extractingPath("$.redirect").isEqualTo("/cameras/canon-ixus-30");

            MvcTestResult r = signup(pending, SIGNUP_BODY.formatted("디카러버"));
            assertThat(r).hasStatus(201);
            assertThat(r).bodyJson().extractingPath("$.nickname").isEqualTo("디카러버");
            assertThat(r).bodyJson().extractingPath("$.provider").isEqualTo("KAKAO");

            // 세션 id가 새로 발급된다 (세션 고정 공격 방지)
            Cookie loggedIn = r.getResponse().getCookie("SESSION");
            assertThat(loggedIn).isNotNull();
            assertThat(loggedIn.getValue()).isNotEqualTo(pending.getValue());

            MvcTestResult me = mvc.get().uri("/api/me").cookie(loggedIn).exchange();
            assertThat(me).hasStatusOk();
            assertThat(me).bodyJson().extractingPath("$.nickname").isEqualTo("디카러버");
            assertThat(me).bodyJson().extractingPath("$.photoCount").isEqualTo(0);

            // 약관 동의 기록 2건, 가입 대기 정보는 지워짐
            assertThat(jdbc.sql("SELECT count(*) FROM terms_agreement").query(Integer.class).single()).isEqualTo(2);
            assertThat(mvc.get().uri("/api/auth/signup").cookie(loggedIn).exchange()).hasStatus(401);
        }

        @Test
        void 약관이_빠지거나_버전이_다르거나_닉네임이_규칙에_안_맞으면_400() {
            Cookie pending = pendingSession("GOOGLE", "g-1", null);

            assertThat(signup(pending, """
                    {"nickname": "디카러버", "agreedTerms": [{"type": "TERMS_OF_SERVICE", "version": "2026-11-01"}]}
                    """)).bodyJson().extractingPath("$.code").isEqualTo("TERMS_NOT_AGREED");
            assertThat(signup(pending, SIGNUP_BODY.formatted("디카러버").replace("2026-11-01", "2025-01-01")))
                    .bodyJson().extractingPath("$.code").isEqualTo("TERMS_VERSION_MISMATCH");
            assertThat(signup(pending, SIGNUP_BODY.formatted("a")))
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_NICKNAME");
            assertThat(signup(pending, SIGNUP_BODY.formatted("<script>")))
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_NICKNAME");
            assertThat(userCount()).isZero();
        }

        @Test
        void 이미_쓰는_닉네임이나_예약어는_409_NICKNAME_DUPLICATED() {
            insertUser("GOOGLE", "g-1", "디카러버");
            Cookie pending = pendingSession("KAKAO", "4242", null);

            assertThat(signup(pending, SIGNUP_BODY.formatted("디카러버")))
                    .bodyJson().extractingPath("$.code").isEqualTo("NICKNAME_DUPLICATED");
            assertThat(signup(pending, SIGNUP_BODY.formatted("Admin")))
                    .bodyJson().extractingPath("$.code").isEqualTo("NICKNAME_DUPLICATED");
        }

        @Test
        void 닉네임_중복_확인() {
            insertUser("GOOGLE", "g-1", "디카러버");
            assertThat(mvc.get().uri("/api/users/nickname-availability").param("nickname", "디카러버").exchange())
                    .bodyJson().extractingPath("$.available").isEqualTo(false);
            assertThat(mvc.get().uri("/api/users/nickname-availability").param("nickname", "관리자").exchange())
                    .bodyJson().extractingPath("$.available").isEqualTo(false);
            assertThat(mvc.get().uri("/api/users/nickname-availability").param("nickname", "익서스_30").exchange())
                    .bodyJson().extractingPath("$.available").isEqualTo(true);
            assertThat(mvc.get().uri("/api/users/nickname-availability").param("nickname", "a b").exchange())
                    .hasStatus(400);
        }

        private MvcTestResult signup(Cookie session, String body) {
            return mvc.post().uri("/api/auth/signup").cookie(session).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        }
    }

    @Nested
    class 로그인_상태 {

        @Test
        void 로그아웃하면_세션이_끝난다() {
            long userId = insertUser("GOOGLE", "g-1", "디카러버");
            Cookie session = loggedInSession(userId);
            assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatusOk();

            assertThat(mvc.post().uri("/api/auth/logout").cookie(session).with(csrf()).exchange()).hasStatus(204);
            assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(401);
        }

        @Test
        void 탈퇴한_회원의_세션은_바로_401() {
            long userId = insertUser("GOOGLE", "g-1", "디카러버");
            Cookie session = loggedInSession(userId);
            jdbc.sql("UPDATE users SET deleted_at = now() WHERE id = :id").param("id", userId).update();

            assertThat(mvc.get().uri("/api/me").cookie(session).exchange()).hasStatus(401);
        }

        @Test
        void 닉네임을_바꿀_수_있다() {
            long userId = insertUser("GOOGLE", "g-1", "디카러버");
            Cookie session = loggedInSession(userId);

            MvcTestResult r = mvc.patch().uri("/api/me").cookie(session).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"똑딱이\"}").exchange();
            assertThat(r).hasStatusOk();
            assertThat(r).bodyJson().extractingPath("$.nickname").isEqualTo("똑딱이");
        }

        @Test
        void 내가_올린_작례면_isMine이_true() {
            long me = insertUser("GOOGLE", "g-1", "디카러버");
            long other = insertUser("KAKAO", "k-1", "똑딱이");
            long photoId = jdbc.sql("""
                            INSERT INTO photo (source, uploader_id, original_key, display_key, thumbnail_key)
                            VALUES ('UPLOAD', :u, 'originals/x.jpg', 'display/x.jpg', 'thumbs/x.jpg') RETURNING id
                            """).param("u", me).query(Long.class).single();

            assertThat(mvc.get().uri("/api/photos/{id}", photoId).cookie(loggedInSession(me)).exchange())
                    .bodyJson().extractingPath("$.isMine").isEqualTo(true);
            assertThat(mvc.get().uri("/api/photos/{id}", photoId).cookie(loggedInSession(other)).exchange())
                    .bodyJson().extractingPath("$.isMine").isEqualTo(false);
            assertThat(mvc.get().uri("/api/photos/{id}", photoId).exchange())
                    .bodyJson().extractingPath("$.isMine").isEqualTo(false);
        }
    }

    // ---------- 도우미 ----------

    private long insertUser(String provider, String providerUserId, String nickname) {
        return jdbc.sql("INSERT INTO users (provider, provider_user_id, nickname) VALUES (:p, :pid, :n) RETURNING id")
                .param("p", provider).param("pid", providerUserId).param("n", nickname)
                .query(Long.class).single();
    }

    private int userCount() {
        return jdbc.sql("SELECT count(*) FROM users").query(Integer.class).single();
    }

    private int sessionCount() {
        return jdbc.sql("SELECT count(*) FROM spring_session").query(Integer.class).single();
    }

    /** 소셜 로그인만 끝난 상태의 세션 (DB에 저장) */
    @SuppressWarnings("unchecked")
    private Cookie pendingSession(String provider, String providerUserId, String redirect) {
        Session session = sessions.createSession();
        session.setAttribute(AuthSession.PENDING_SIGNUP, new PendingSignup(provider, providerUserId));
        if (redirect != null) {
            session.setAttribute(AuthSession.REDIRECT, redirect);
        }
        sessions.save(session);
        return sessionCookie(session.getId());
    }

    /** 로그인한 상태의 세션 (DB에 저장) */
    @SuppressWarnings("unchecked")
    private Cookie loggedInSession(long userId) {
        Session session = sessions.createSession();
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(auth));
        sessions.save(session);
        return sessionCookie(session.getId());
    }

    /** Spring Session 기본 쿠키는 세션 id를 base64로 담는다 */
    private static Cookie sessionCookie(String sessionId) {
        return new Cookie("SESSION",
                Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8)));
    }
}
