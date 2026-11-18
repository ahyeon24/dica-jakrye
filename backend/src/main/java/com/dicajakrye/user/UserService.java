package com.dicajakrye.user;

import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.common.ImageUrls;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserQueries users;
    private final ImageUrls imageUrls;

    public UserService(UserQueries users, ImageUrls imageUrls) {
        this.users = users;
        this.imageUrls = imageUrls;
    }

    /** 탈퇴했으면 빈 값 */
    @Transactional(readOnly = true)
    public Optional<MeResponse> me(long userId) {
        return users.findActive(userId).map(u -> new MeResponse(u.id(), u.nickname(),
                imageUrls.publicUrl(u.profileImageKey()), u.role(), u.provider(), users.photoCount(u.id())));
    }

    public record ProfileResponse(String nickname, String profileImageUrl, int photoCount, OffsetDateTime joinedAt,
                                  boolean isMe) {
    }

    /** 공개 프로필. 없거나 탈퇴했으면 USER_NOT_FOUND */
    @Transactional(readOnly = true)
    public ProfileResponse profile(String nickname, Long viewerId) {
        var p = findProfile(nickname);
        return new ProfileResponse(p.nickname(), imageUrls.publicUrl(p.profileImageKey()), users.photoCount(p.id()),
                p.createdAt(), viewerId != null && viewerId == p.id());
    }

    /** 프로필 주인의 id. 없거나 탈퇴했으면 USER_NOT_FOUND */
    @Transactional(readOnly = true)
    public long profileUserId(String nickname) {
        return findProfile(nickname).id();
    }

    private UserQueries.ProfileRow findProfile(String nickname) {
        if (!Nickname.isValid(nickname)) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        return users.findProfile(nickname).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    /** 형식이 맞지 않으면 INVALID_NICKNAME. 예약어·사용 중이면 false */
    @Transactional(readOnly = true)
    public boolean isAvailable(String nickname) {
        requireValid(nickname);
        return !Nickname.isReserved(nickname) && !users.nicknameExists(nickname);
    }

    @Transactional
    public void changeNickname(long userId, String nickname) {
        requireValid(nickname);
        if (Nickname.isReserved(nickname)) {
            throw new ApiException(ErrorCode.NICKNAME_DUPLICATED);
        }
        try {
            users.updateNickname(userId, nickname);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.NICKNAME_DUPLICATED);
        }
    }

    static void requireValid(String nickname) {
        if (!Nickname.isValid(nickname)) {
            throw new ApiException(ErrorCode.INVALID_NICKNAME);
        }
    }
}
