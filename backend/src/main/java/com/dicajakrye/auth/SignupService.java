package com.dicajakrye.auth;

import com.dicajakrye.auth.TermsProperties.Terms;
import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.user.Nickname;
import com.dicajakrye.user.UserQueries;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가입: users 1행 + 약관 종류별 terms_agreement 1행씩을 한 트랜잭션으로 (ERD.md 5장) */
@Service
public class SignupService {

    public record AgreedTerms(String type, String version) {
    }

    private final UserQueries users;
    private final TermsProperties terms;

    public SignupService(UserQueries users, TermsProperties terms) {
        this.users = users;
        this.terms = terms;
    }

    /** @return 회원 id. 같은 소셜 계정으로 이미 가입돼 있으면(두 번 누름 등) 그 회원 id */
    @Transactional
    public long signup(PendingSignup pending, String nickname, List<AgreedTerms> agreed) {
        if (!Nickname.isValid(nickname)) {
            throw new ApiException(ErrorCode.INVALID_NICKNAME);
        }
        checkTerms(agreed);

        var existing = users.findActiveByProvider(pending.provider(), pending.providerUserId());
        if (existing.isPresent()) {
            return existing.get().id();
        }
        if (Nickname.isReserved(nickname) || users.nicknameExists(nickname)) {
            throw new ApiException(ErrorCode.NICKNAME_DUPLICATED);
        }
        long userId;
        try {
            userId = users.insert(pending.provider(), pending.providerUserId(), nickname);
        } catch (DuplicateKeyException e) {
            // 동시에 같은 닉네임으로 가입한 경우
            throw new ApiException(ErrorCode.NICKNAME_DUPLICATED);
        }
        for (Terms t : terms.current()) {
            users.insertTermsAgreement(userId, t.type(), t.version());
        }
        return userId;
    }

    private void checkTerms(List<AgreedTerms> agreed) {
        Map<String, String> byType = agreed == null ? Map.of() : agreed.stream()
                .filter(a -> a != null && a.type() != null && a.version() != null)
                .collect(Collectors.toMap(AgreedTerms::type, AgreedTerms::version, (a, b) -> a));
        for (Terms t : terms.current()) {
            String version = byType.get(t.type());
            if (version == null) {
                throw new ApiException(ErrorCode.TERMS_NOT_AGREED);
            }
            if (!version.equals(t.version())) {
                throw new ApiException(ErrorCode.TERMS_VERSION_MISMATCH);
            }
        }
    }
}
