package com.dicajakrye.auth;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 지금 요청의 로그인 회원 id. 탈퇴 여부는 확인하지 않으므로, 필요하면 DB에서 다시 확인한다 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<Long> id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser u) {
            return Optional.of(u.userId());
        }
        return Optional.empty();
    }
}
