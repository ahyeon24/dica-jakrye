package com.dicajakrye.report;

import com.dicajakrye.auth.CurrentUser;
import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import com.dicajakrye.common.ImageUrls;
import com.dicajakrye.user.AdminCheck;
import com.dicajakrye.user.UserService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private final ReportService reportService;
    private final AdminReportService adminReportService;
    private final AdminCheck adminCheck;
    private final UserService userService;
    private final JdbcClient jdbc;
    private final ImageUrls imageUrls;

    public ReportController(ReportService reportService, AdminReportService adminReportService, AdminCheck adminCheck,
                            UserService userService, JdbcClient jdbc, ImageUrls imageUrls) {
        this.reportService = reportService;
        this.adminReportService = adminReportService;
        this.adminCheck = adminCheck;
        this.userService = userService;
        this.jdbc = jdbc;
        this.imageUrls = imageUrls;
    }

    public record ReportRequest(String reason, String detail) {
    }

    public record ReportResult(boolean hidden) {
    }

    @PostMapping("/api/photos/{id}/reports")
    public ResponseEntity<ReportResult> report(@PathVariable long id, @RequestBody ReportRequest body) {
        long userId = activeUserId();
        boolean hidden = reportService.report(id, userId, body.reason(), body.detail());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReportResult(hidden));
    }

    public record HiddenPhoto(long id, String thumbnailUrl, OffsetDateTime hiddenAt) {
    }

    public record HiddenPhotos(List<HiddenPhoto> items) {
    }

    record HiddenRow(long id, String thumbnailKey, OffsetDateTime hiddenAt) {
    }

    /** 신고로 가려진 내 작례 (올린 사람에게 알리기, PRD F8) */
    @GetMapping("/api/me/hidden-photos")
    public HiddenPhotos myHiddenPhotos() {
        long userId = activeUserId();
        List<HiddenPhoto> items = jdbc.sql("""
                        SELECT id, thumbnail_key, hidden_at FROM photo
                         WHERE uploader_id = :u AND deleted_at IS NULL AND hidden_at IS NOT NULL
                         ORDER BY hidden_at DESC
                        """)
                .param("u", userId)
                .query(HiddenRow.class)
                .list()
                .stream()
                .map(r -> new HiddenPhoto(r.id(), imageUrls.publicUrl(r.thumbnailKey()), r.hiddenAt()))
                .toList();
        return new HiddenPhotos(items);
    }

    // ---------- 관리자 ----------

    @GetMapping("/api/admin/reports")
    public List<AdminReportService.PhotoReports> pendingReports() {
        adminCheck.requireAdmin();
        return adminReportService.pending();
    }

    @PostMapping("/api/admin/reports/photos/{photoId}/delete")
    public ResponseEntity<Void> resolveByDelete(@PathVariable long photoId) {
        adminReportService.delete(photoId, adminCheck.requireAdmin());
        return ResponseEntity.noContent().build();
    }

    public record ChangeCameraRequest(String cameraSlug) {
    }

    @PostMapping("/api/admin/reports/photos/{photoId}/change-camera")
    public ResponseEntity<Void> resolveByCameraChange(@PathVariable long photoId, @RequestBody ChangeCameraRequest body) {
        adminReportService.changeCamera(photoId, body.cameraSlug(), adminCheck.requireAdmin());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/reports/photos/{photoId}/reject")
    public ResponseEntity<Void> reject(@PathVariable long photoId) {
        adminReportService.reject(photoId, adminCheck.requireAdmin());
        return ResponseEntity.noContent().build();
    }

    /** 신고와 관계없는 강제 삭제 (Commons 저작권자 요청 등) */
    @DeleteMapping("/api/admin/photos/{photoId}")
    public ResponseEntity<Void> forceDelete(@PathVariable long photoId) {
        adminReportService.forceDelete(photoId, adminCheck.requireAdmin());
        return ResponseEntity.noContent().build();
    }

    private long activeUserId() {
        long userId = CurrentUser.id().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (userService.me(userId).isEmpty()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
