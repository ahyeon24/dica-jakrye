package com.dicajakrye.auth;

import java.io.Serializable;

/**
 * 소셜 로그인은 했지만 아직 약관에 동의하지 않은 사람. 회원은 만들지 않고 세션에만 둔다. (API.md 2장)
 */
public record PendingSignup(String provider, String providerUserId) implements Serializable {
}
