package com.dicajakrye;

import com.dicajakrye.auth.LoginUser;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/** 테스트용 로그인 세션. 실제처럼 DB(spring_session)에 저장하고 SESSION 쿠키를 돌려준다 */
public final class TestSessions {

    private TestSessions() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Cookie loggedIn(SessionRepository sessions, long userId) {
        Session session = sessions.createSession();
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(auth));
        sessions.save(session);
        return cookie(session.getId());
    }

    /** Spring Session 기본 쿠키는 세션 id를 base64로 담는다 */
    public static Cookie cookie(String sessionId) {
        return new Cookie("SESSION", Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8)));
    }
}
