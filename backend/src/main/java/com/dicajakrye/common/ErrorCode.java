package com.dicajakrye.common;

import org.springframework.http.HttpStatus;

/** API 에러 코드. 목록과 의미는 docs/API.md 9장과 맞춘다. */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임은 2~20자의 한글·영문·숫자·밑줄만 쓸 수 있어요."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해 주세요."),
    TERMS_VERSION_MISMATCH(HttpStatus.BAD_REQUEST, "약관이 바뀌었어요. 화면을 새로고침해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    SIGNUP_SESSION_NOT_FOUND(HttpStatus.UNAUTHORIZED, "가입 시간이 지났어요. 다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요."),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "요청을 확인하지 못했어요. 새로고침 후 다시 시도해 주세요."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 쓰고 있는 닉네임이에요."),
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
