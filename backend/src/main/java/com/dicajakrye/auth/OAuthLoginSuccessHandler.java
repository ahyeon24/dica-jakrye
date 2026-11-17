package com.dicajakrye.auth;

import com.dicajakrye.user.UserQueries;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 구글·카카오 로그인이 끝났을 때 (API.md 2장 로그인 흐름)
 * - 이미 가입한 회원: 세션에 회원 id를 저장하고 원래 보던 페이지로
 * - 처음 온 사람: 회원은 만들지 않고 가입 대기 정보만 세션에 저장한 뒤 /signup 으로
 * 제공자에게서 받은 값 중 회원번호(google: sub, kakao: id)만 쓴다.
 */
@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserQueries users;
    private final SessionLogin sessionLogin;
    private final String frontUrl;

    public OAuthLoginSuccessHandler(UserQueries users, SessionLogin sessionLogin,
                                    @Value("${app.front-url}") String frontUrl) {
        this.users = users;
        this.sessionLogin = sessionLogin;
        this.frontUrl = frontUrl.endsWith("/") ? frontUrl.substring(0, frontUrl.length() - 1) : frontUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId().toUpperCase(Locale.ROOT); // GOOGLE / KAKAO
        String providerUserId = token.getName();

        var user = users.findActiveByProvider(provider, providerUserId);
        if (user.isPresent()) {
            String redirect = AuthSession.redirect(request);
            request.getSession().removeAttribute(AuthSession.REDIRECT);
            request.getSession().removeAttribute(AuthSession.PENDING_SIGNUP);
            sessionLogin.login(user.get().id(), user.get().role(), request, response);
            response.sendRedirect(frontUrl + redirect);
            return;
        }
        // 약관 동의 전에는 회원을 만들지 않는다. 로그인 상태도 아니다.
        sessionLogin.clear(request, response);
        request.getSession().setAttribute(AuthSession.PENDING_SIGNUP, new PendingSignup(provider, providerUserId));
        response.sendRedirect(frontUrl + "/signup");
    }
}
