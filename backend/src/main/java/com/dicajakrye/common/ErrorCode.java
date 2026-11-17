package com.dicajakrye.common;

import org.springframework.http.HttpStatus;

/** API 에러 코드. 목록과 의미는 docs/API.md 9장과 맞춘다. */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요."),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "브랜드를 찾을 수 없어요."),
    CAMERA_NOT_FOUND(HttpStatus.NOT_FOUND, "기종을 찾을 수 없어요."),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "작례를 찾을 수 없어요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
