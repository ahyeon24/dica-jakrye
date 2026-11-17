package com.dicajakrye.user;

/** 내 정보 (GET /api/me, API.md 2장) */
public record MeResponse(long id, String nickname, String profileImageUrl, String role, String provider,
                         int photoCount) {
}
