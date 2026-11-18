package com.dicajakrye.report;

import com.dicajakrye.common.ApiException;
import com.dicajakrye.common.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 접수 (PRD F8, API.md 5장)
 * - 초상권·도용 신고는 같은 트랜잭션에서 작례를 가린다 (임시조치)
 * - 한 사람 하루 10건, 같은 작례 1회
 */
@Service
public class ReportService {

    static final int MAX_PER_DAY = 10;
    static final int MAX_DETAIL = 500;

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    public ReportService(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    record Target(long id, Long uploaderId) {
    }

    /** @return 작례를 가렸으면 true */
    @Transactional
    public boolean report(long photoId, long reporterId, String reasonValue, String detailValue) {
        ReportReason reason = parseReason(reasonValue);
        String detail = detailValue == null ? null : detailValue.strip();
        if (detail != null && detail.isEmpty()) {
            detail = null;
        }
        if ((detail != null && detail.length() > MAX_DETAIL) || (reason == ReportReason.OTHER && detail == null)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }

        // 이미 가려진 작례는 다른 사람에게 보이지 않으므로 신고할 수도 없다
        Target target = jdbc.sql("""
                        SELECT id, uploader_id FROM photo
                         WHERE id = :id AND deleted_at IS NULL AND hidden_at IS NULL
                           FOR UPDATE
                        """)
                .param("id", photoId)
                .query(Target.class)
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.PHOTO_NOT_FOUND));
        if (target.uploaderId() != null && target.uploaderId() == reporterId) {
            throw new ApiException(ErrorCode.CANNOT_REPORT_OWN_PHOTO);
        }
        int today = jdbc.sql("SELECT count(*) FROM report WHERE reporter_id = :r AND created_at > now() - interval '1 day'")
                .param("r", reporterId)
                .query(Integer.class)
                .single();
        if (today >= MAX_PER_DAY) {
            throw new ApiException(ErrorCode.REPORT_RATE_LIMITED);
        }

        long reportId;
        try {
            reportId = jdbc.sql("""
                            INSERT INTO report (photo_id, reporter_id, reason, detail)
                            VALUES (:p, :r, :reason, :detail) RETURNING id
                            """)
                    .param("p", photoId)
                    .param("r", reporterId)
                    .param("reason", reason.name())
                    .param("detail", detail)
                    .query(Long.class)
                    .single();
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.ALREADY_REPORTED);
        }

        boolean hidden = reason.hidesImmediately();
        if (hidden) {
            jdbc.sql("UPDATE photo SET hidden_at = now(), updated_at = now() WHERE id = :id AND hidden_at IS NULL")
                    .param("id", photoId)
                    .update();
        }
        events.publishEvent(new ReportCreatedEvent(reportId, photoId, reason, hidden));
        return hidden;
    }

    private static ReportReason parseReason(String value) {
        try {
            return ReportReason.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }
}
