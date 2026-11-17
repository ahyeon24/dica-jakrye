package com.dicajakrye.auth;

import java.io.Serializable;

/**
 * 세션에 저장하는 로그인 정보. 회원 id만 둔다.
 * 닉네임·권한·탈퇴 여부는 매번 DB에서 읽어서, 바뀌면 바로 반영되게 한다. (API.md 1.2)
 * 세션이 DB에 저장되므로 Serializable이어야 한다.
 */
public record LoginUser(long userId) implements Serializable {
}
