package com.dicajakrye.auth;

import com.dicajakrye.auth.SignupService.AgreedTerms;
import com.dicajakrye.auth.TermsProperties.Terms;
import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.user.MeResponse;
import com.dicajakrye.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final SignupService signupService;
    private final UserService userService;
    private final TermsProperties terms;
    private final SessionLogin sessionLogin;

    public AuthController(SignupService signupService, UserService userService, TermsProperties terms,
                          SessionLogin sessionLogin) {
        this.signupService = signupService;
        this.userService = userService;
        this.terms = terms;
        this.sessionLogin = sessionLogin;
    }

    /** XSRF-TOKEN 쿠키를 받는다. 프론트가 첫 쓰기 요청 전에 쿠키가 없으면 부른다 */
    @GetMapping("/api/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // 토큰을 읽어야 쿠키가 만들어진다
        return ResponseEntity.noContent().build();
    }

    public record SignupInfo(String provider, List<Terms> terms, String redirect) {
    }

    /** 가입 화면에 보여줄 정보. 제공자 정보(닉네임 등)는 받지 않으므로 미리 채울 값은 없다 */
    @GetMapping("/api/auth/signup")
    public SignupInfo signupInfo(HttpServletRequest request) {
        PendingSignup pending = requirePending(request);
        return new SignupInfo(pending.provider(), terms.current(), AuthSession.redirect(request));
    }

    public record SignupRequest(String nickname, List<AgreedTerms> agreedTerms) {
    }

    @PostMapping("/api/auth/signup")
    public ResponseEntity<MeResponse> signup(@RequestBody SignupRequest body, HttpServletRequest request,
                                             HttpServletResponse response) {
        PendingSignup pending = requirePending(request);
        long userId = signupService.signup(pending, body.nickname(), body.agreedTerms());

        // 로그인 상태가 바뀌므로 세션 id를 새로 발급한다 (세션 고정 공격 방지)
        request.changeSessionId();
        request.getSession().removeAttribute(AuthSession.PENDING_SIGNUP);
        MeResponse me = userService.me(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        sessionLogin.login(userId, me.role(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(me);
    }

    private static PendingSignup requirePending(HttpServletRequest request) {
        PendingSignup pending = AuthSession.pending(request);
        if (pending == null) {
            throw new ApiException(ErrorCode.SIGNUP_SESSION_NOT_FOUND);
        }
        return pending;
    }
}
