-- 신고로 가리기 (임시조치, 정보통신망법 제44조의2). PRD F8, ERD 3.6·3.9
-- 초상권·도용 신고가 들어오면 채우고, 관리자가 기각하면 비운다. 채워져 있으면 공개 목록·상세·개수에서 빠진다.
ALTER TABLE photo ADD COLUMN hidden_at timestamptz;

-- 한 사람의 하루 신고 수 제한 (10건)
CREATE INDEX idx_report_reporter ON report (reporter_id, created_at);
