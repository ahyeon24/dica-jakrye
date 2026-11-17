package com.dicajakrye.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 로그인 과정에서 세션에 잠깐 두는 값들 */
public final class AuthSession {

    /** 로그인 후 돌아갈 내부 경로 (SafeRedirect로 검사한 값) */
    static final String REDIRECT = "dica.auth.redirect";
    /** 가입 대기 정보 (PendingSignup) */
    static final String PENDING_SIGNUP = "dica.auth.pendingSignup";

    private AuthSession() {
    }

    static String redirect(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object v = session == null ? null : session.getAttribute(REDIRECT);
        return v instanceof String s ? SafeRedirect.sanitize(s) : SafeRedirect.HOME;
    }

    static PendingSignup pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object v = session == null ? null : session.getAttribute(PENDING_SIGNUP);
        return v instanceof PendingSignup p ? p : null;
    }
}
