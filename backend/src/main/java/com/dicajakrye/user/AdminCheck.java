package com.dicajakrye.user;

import com.dicajakrye.auth.CurrentUser;
import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 관리자 확인. 세션에 저장된 권한이 아니라 요청마다 DB의 users.role을 본다.
 * 권한을 회수하면 바로 반영되게 하기 위해서다. (PRD F8, SECURITY.md 13-1)
 */
@Component
public class AdminCheck {

    private final UserQueries users;

    public AdminCheck(UserQueries users) {
        this.users = users;
    }

    public boolean isAdmin(Long userId) {
        return userId != null && users.findActive(userId).map(u -> "ADMIN".equals(u.role())).orElse(false);
    }

    /** 로그인 안 했으면 401, 관리자가 아니면 403. 관리자 id를 돌려준다 */
    public long requireAdmin() {
        long userId = CurrentUser.id().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (!isAdmin(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return userId;
    }
}
