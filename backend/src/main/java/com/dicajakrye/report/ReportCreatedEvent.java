package com.dicajakrye.report;

/** 신고가 접수됐다. 트랜잭션이 끝난 뒤 관리자에게 알림을 보낸다 */
public record ReportCreatedEvent(long reportId, long photoId, ReportReason reason, boolean hidden) {
}
