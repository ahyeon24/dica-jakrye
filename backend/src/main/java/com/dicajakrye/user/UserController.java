package com.dicajakrye.user;

import com.dicajakrye.auth.CurrentUser;
import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.common.PageResponse;
import com.dicajakrye.photo.PhotoListService;
import com.dicajakrye.photo.PhotoSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;
    private final PhotoListService photoListService;

    public UserController(UserService userService, PhotoListService photoListService) {
        this.userService = userService;
        this.photoListService = photoListService;
    }

    /** 내 정보. 프론트는 401이면 로그인 안 한 것으로 본다 */
    @GetMapping("/api/me")
    public MeResponse me(HttpServletRequest request) {
        return currentMe(request);
    }

    public record NicknameRequest(String nickname) {
    }

    @PatchMapping("/api/me")
    public MeResponse changeNickname(@RequestBody NicknameRequest body, HttpServletRequest request) {
        MeResponse me = currentMe(request);
        userService.changeNickname(me.id(), body.nickname());
        return currentMe(request);
    }

    public record Availability(boolean available) {
    }

    @GetMapping("/api/users/nickname-availability")
    public Availability nicknameAvailability(@RequestParam String nickname) {
        return new Availability(userService.isAvailable(nickname));
    }

    /** 공개 프로필 */
    @GetMapping("/api/users/{nickname}")
    public UserService.ProfileResponse profile(@PathVariable String nickname) {
        return userService.profile(nickname, CurrentUser.id().orElse(null));
    }

    /** 프로필 피드: 그 사람이 올린 작례 (미분류 포함) */
    @GetMapping("/api/users/{nickname}/photos")
    public PageResponse<PhotoSummaryResponse> profilePhotos(@PathVariable String nickname,
                                                            @RequestParam(required = false) String cursor,
                                                            @RequestParam(defaultValue = "" + PhotoListService.DEFAULT_PAGE_SIZE) int size) {
        return photoListService.byUploader(userService.profileUserId(nickname), cursor, size);
    }

    /** 세션은 있는데 그사이 탈퇴(또는 강제 탈퇴)됐으면 세션을 끝내고 401 */
    private MeResponse currentMe(HttpServletRequest request) {
        long userId = CurrentUser.id().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return userService.me(userId).orElseThrow(() -> {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            return new ApiException(ErrorCode.UNAUTHORIZED);
        });
    }
}
