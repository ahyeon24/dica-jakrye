package com.dicajakrye.user;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserQueries {

    private final JdbcClient jdbc;

    public UserQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 탈퇴하지 않은 회원 한 명 */
    public record UserRow(long id, String provider, String nickname, String profileImageKey, String role) {
    }

    public Optional<UserRow> findActive(long id) {
        return jdbc.sql("""
                        SELECT id, provider, nickname, profile_image_key, role
                          FROM users WHERE id = :id AND deleted_at IS NULL
                        """)
                .param("id", id)
                .query(UserRow.class)
                .optional();
    }

    public Optional<UserRow> findActiveByProvider(String provider, String providerUserId) {
        return jdbc.sql("""
                        SELECT id, provider, nickname, profile_image_key, role
                          FROM users
                         WHERE provider = :provider AND provider_user_id = :pid AND deleted_at IS NULL
                        """)
                .param("provider", provider)
                .param("pid", providerUserId)
                .query(UserRow.class)
                .optional();
    }

    public record ProfileRow(long id, String nickname, String profileImageKey, OffsetDateTime createdAt) {
    }

    /** 공개 프로필. 탈퇴한 회원은 없는 것으로 본다 */
    public Optional<ProfileRow> findProfile(String nickname) {
        return jdbc.sql("""
                        SELECT id, nickname, profile_image_key, created_at
                          FROM users WHERE nickname = :n AND deleted_at IS NULL
                        """)
                .param("n", nickname)
                .query(ProfileRow.class)
                .optional();
    }

    /** 탈퇴한 회원의 닉네임("탈퇴한 사용자#id")까지 포함해서 겹치는지 본다 */
    public boolean nicknameExists(String nickname) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM users WHERE nickname = :n)")
                .param("n", nickname)
                .query(Boolean.class)
                .single();
    }

    public int photoCount(long userId) {
        return jdbc.sql("SELECT count(*) FROM photo WHERE uploader_id = :id AND deleted_at IS NULL AND hidden_at IS NULL")
                .param("id", userId)
                .query(Integer.class)
                .single();
    }

    public long insert(String provider, String providerUserId, String nickname) {
        return jdbc.sql("""
                        INSERT INTO users (provider, provider_user_id, nickname)
                        VALUES (:provider, :pid, :nickname)
                        RETURNING id
                        """)
                .param("provider", provider)
                .param("pid", providerUserId)
                .param("nickname", nickname)
                .query(Long.class)
                .single();
    }

    public void insertTermsAgreement(long userId, String termsType, String version) {
        jdbc.sql("INSERT INTO terms_agreement (user_id, terms_type, version) VALUES (:u, :t, :v)")
                .param("u", userId)
                .param("t", termsType)
                .param("v", version)
                .update();
    }

    public void updateNickname(long userId, String nickname) {
        jdbc.sql("UPDATE users SET nickname = :n, updated_at = now() WHERE id = :id AND deleted_at IS NULL")
                .param("n", nickname)
                .param("id", userId)
                .update();
    }
}
