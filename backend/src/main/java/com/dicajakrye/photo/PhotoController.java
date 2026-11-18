package com.dicajakrye.photo;

import com.dicajakrye.auth.CurrentUser;
import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.common.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService photoService;
    private final PhotoDeleteService deleteService;
    private final PhotoListService listService;

    public PhotoController(PhotoService photoService, PhotoDeleteService deleteService, PhotoListService listService) {
        this.photoService = photoService;
        this.deleteService = deleteService;
        this.listService = listService;
    }

    /** 홈의 최근 올라온 작례 (사용자 업로드, 기종이 정해진 것만) */
    @GetMapping("/recent")
    public PageResponse<PhotoSummaryResponse> recent(@RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "" + PhotoListService.DEFAULT_PAGE_SIZE) int size) {
        return listService.recent(cursor, size);
    }

    @GetMapping("/{id}")
    public PhotoDetailResponse detail(@PathVariable long id) {
        return photoService.detail(id, CurrentUser.id().orElse(null));
    }

    /** 이전·다음 작례 (상세 화면에서 옆으로 넘기기). 필터는 기종 작례 목록과 같다 */
    @GetMapping("/{id}/adjacent")
    public PhotoService.AdjacentResponse adjacent(@PathVariable long id,
                                                  @RequestParam(required = false) Boolean flash,
                                                  @RequestParam(defaultValue = "false") boolean originalOnly) {
        return photoService.adjacent(id, flash, originalOnly, CurrentUser.id().orElse(null));
    }

    /** 내 작례 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        long userId = CurrentUser.id().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        deleteService.deleteOwn(id, userId);
        return ResponseEntity.noContent().build();
    }

    /** 상세 페이지가 브라우저에서 열릴 때 프론트가 부른다 */
    @PostMapping("/{id}/views")
    public ResponseEntity<Void> view(@PathVariable long id) {
        photoService.increaseView(id);
        return ResponseEntity.noContent().build();
    }
}
