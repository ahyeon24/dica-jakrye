package com.dicajakrye.camera;

import com.dicajakrye.camera.CameraResponses.CameraDetail;
import com.dicajakrye.camera.CameraResponses.SearchResult;
import com.dicajakrye.common.PageResponse;
import com.dicajakrye.photo.PhotoSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cameras")
public class CameraController {

    private final CameraService cameraService;

    public CameraController(CameraService cameraService) {
        this.cameraService = cameraService;
    }

    /** 기종 검색. 예) /api/cameras/search?q=익서스 */
    @GetMapping("/search")
    public SearchResult search(@RequestParam String q,
                               @RequestParam(defaultValue = "20") int limit) {
        return cameraService.search(q, limit);
    }

    @GetMapping("/{slug}")
    public CameraDetail detail(@PathVariable String slug) {
        return cameraService.detail(slug);
    }

    /** 기종 작례 목록. flash: 생략=전체, true/false. originalOnly: true면 원본 크기만 */
    @GetMapping("/{slug}/photos")
    public PageResponse<PhotoSummaryResponse> photos(@PathVariable String slug,
                                                     @RequestParam(required = false) Boolean flash,
                                                     @RequestParam(defaultValue = "false") boolean originalOnly,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "" + CameraService.DEFAULT_PAGE_SIZE) int size) {
        return cameraService.photos(slug, flash, originalOnly, cursor, size);
    }
}
