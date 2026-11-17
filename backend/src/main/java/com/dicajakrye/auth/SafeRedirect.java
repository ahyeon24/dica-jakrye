package com.dicajakrye.auth;

/**
 * 로그인 후 돌아갈 주소 검사. 우리 사이트 내부 경로만 허용한다. (SECURITY.md 6장, Open Redirect 방지)
 */
public final class SafeRedirect {

    public static final String HOME = "/";
    private static final int MAX_LENGTH = 500;

    private SafeRedirect() {
    }

    /** 안전한 내부 경로면 그대로, 아니면 "/" */
    public static String sanitize(String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_LENGTH) {
            return HOME;
        }
        if (!path.startsWith("/") || path.startsWith("//") || path.startsWith("/\\")) {
            return HOME;
        }
        // 제어 문자(줄바꿈 등)와 역슬래시가 있으면 브라우저마다 해석이 달라질 수 있어 막는다
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c < 0x20 || c == 0x7f || c == '\\') {
                return HOME;
            }
        }
        return path;
    }
}
