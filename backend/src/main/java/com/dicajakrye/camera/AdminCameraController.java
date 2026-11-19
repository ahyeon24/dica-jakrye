package com.dicajakrye.camera;

import com.dicajakrye.camera.UnclassifiedService.Group;
import com.dicajakrye.camera.UnclassifiedService.Linked;
import com.dicajakrye.camera.UnclassifiedService.NewCamera;
import com.dicajakrye.user.AdminCheck;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 관리자: 미분류 작례 연결 (API.md 8장). 권한은 요청마다 DB의 users.role로 확인한다 */
@RestController
public class AdminCameraController {

    private final UnclassifiedService unclassifiedService;
    private final AdminCheck adminCheck;

    public AdminCameraController(UnclassifiedService unclassifiedService, AdminCheck adminCheck) {
        this.unclassifiedService = unclassifiedService;
        this.adminCheck = adminCheck;
    }

    @GetMapping("/api/admin/unclassified")
    public List<Group> unclassified() {
        adminCheck.requireAdmin();
        return unclassifiedService.list();
    }

    /** cameraSlug(기존 기종)와 newCamera(새 기종) 중 하나만 보낸다 */
    public record LinkRequest(String exifMake, String exifModel, String cameraSlug, NewCamera newCamera) {
    }

    @PostMapping("/api/admin/exif-models")
    public Linked link(@RequestBody LinkRequest body) {
        adminCheck.requireAdmin();
        return unclassifiedService.link(body.exifMake(), body.exifModel(), body.cameraSlug(), body.newCamera());
    }
}
