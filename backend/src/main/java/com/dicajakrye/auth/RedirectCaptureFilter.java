package com.dicajakrye.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인 시작(/oauth2/authorization/{provider}?redirect=/경로)에서 돌아갈 경로를 검사해 세션에 저장한다.
 * 로그인이 끝나면 OAuthLoginSuccessHandler가 꺼내 쓴다. (SECURITY.md 6장)
 */
public class RedirectCaptureFilter extends OncePerRequestFilter {

    static final String AUTHORIZATION_PREFIX = "/oauth2/authorization/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AUTHORIZATION_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.getSession().setAttribute(AuthSession.REDIRECT, SafeRedirect.sanitize(request.getParameter("redirect")));
        chain.doFilter(request, response);
    }
}
