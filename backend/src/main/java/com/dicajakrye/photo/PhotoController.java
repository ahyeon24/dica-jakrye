package com.dicajakrye.photo;

import com.dicajakrye.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
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

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
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

    /** 상세 페이지가 브라우저에서 열릴 때 프론트가 부른다 */
    @PostMapping("/{id}/views")
    public ResponseEntity<Void> view(@PathVariable long id) {
        photoService.increaseView(id);
        return ResponseEntity.noContent().build();
    }
}
