package com.dicajakrye.upload;

import com.dicajakrye.auth.CurrentUser;
import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.photo.PhotoDetailResponse;
import com.dicajakrye.user.UserService;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UploadController {

    private final UploadService uploadService;
    private final CameraMatcher cameraMatcher;
    private final UserService userService;

    public UploadController(UploadService uploadService, CameraMatcher cameraMatcher, UserService userService) {
        this.uploadService = uploadService;
        this.cameraMatcher = cameraMatcher;
        this.userService = userService;
    }

    /** 작례 올리기. multipart: file(JPEG, 20MB), agreedToUploadPolicy(true) */
    @PostMapping("/api/photos")
    public ResponseEntity<PhotoDetailResponse> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(defaultValue = "false") boolean agreedToUploadPolicy)
            throws IOException {
        long userId = activeUserId();
        if (file.getSize() > UploadService.MAX_FILE_BYTES) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }
        // 사용자가 보낸 파일명·Content-Type은 쓰지 않는다
        PhotoDetailResponse created = uploadService.upload(userId, file.getBytes(), agreedToUploadPolicy);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    public record CameraRef(String slug, String name, String nameKo, String brandName) {
    }

    public record ExifMatch(boolean matched, CameraRef camera) {
    }

    /** 업로드 전 미리보기: 브라우저가 읽은 EXIF로 기종을 찾아 본다. 최종 판단은 업로드 때 서버가 다시 한다 */
    @GetMapping("/api/cameras/exif-match")
    public ExifMatch exifMatch(@RequestParam String make, @RequestParam String model) {
        activeUserId();
        if (make.length() > 100 || model.length() > 100) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return cameraMatcher.find(make, model)
                .map(m -> new ExifMatch(true, new CameraRef(m.slug(), m.name(), m.nameKo(), m.brandName())))
                .orElse(new ExifMatch(false, null));
    }

    /** 로그인했고 탈퇴하지 않은 회원 */
    private long activeUserId() {
        long userId = CurrentUser.id().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (userService.me(userId).isEmpty()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
