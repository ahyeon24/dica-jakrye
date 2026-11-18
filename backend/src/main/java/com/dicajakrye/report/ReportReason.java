package com.dicajakrye.report;

/** 신고 사유 (PRD F8) */
public enum ReportReason {
    INAPPROPRIATE("부적절한 사진"),
    STOLEN("내가 찍은 사진 도용"),
    PORTRAIT_RIGHTS("초상권 침해"),
    WRONG_CAMERA("해당 기종 사진 아님"),
    OTHER("기타");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 접수 즉시 작례를 가리는 사유 (임시조치, 정보통신망법 제44조의2) */
    public boolean hidesImmediately() {
        return this == STOLEN || this == PORTRAIT_RIGHTS;
    }
}
