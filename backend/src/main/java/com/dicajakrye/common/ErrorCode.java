package com.dicajakrye.common;

import org.springframework.http.HttpStatus;

/** API 에러 코드. 목록과 의미는 docs/API.md 9장과 맞춘다. */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임은 2~20자의 한글·영문·숫자·밑줄만 쓸 수 있어요."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해 주세요."),
    TERMS_VERSION_MISMATCH(HttpStatus.BAD_REQUEST, "약관이 바뀌었어요. 화면을 새로고침해 주세요."),
    UPLOAD_POLICY_NOT_AGREED(HttpStatus.BAD_REQUEST, "직접 찍은 사진인지 확인하고 체크해 주세요."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "JPEG 사진만 올릴 수 있어요. 파일이 깨졌을 수도 있어요."),
    EXIF_NOT_FOUND(HttpStatus.BAD_REQUEST, "촬영 정보가 없는 파일이에요. 카메라에서 옮긴 원본 파일을 올려 주세요."),
    IMAGE_DIMENSIONS_TOO_LARGE(HttpStatus.BAD_REQUEST, "사진 해상도가 너무 커요. (최대 4,000만 화소)"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    SIGNUP_SESSION_NOT_FOUND(HttpStatus.UNAUTHORIZED, "가입 시간이 지났어요. 다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요."),
    NOT_PHOTO_OWNER(HttpStatus.FORBIDDEN, "내가 올린 작례만 지울 수 있어요."),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "요청을 확인하지 못했어요. 새로고침 후 다시 시도해 주세요."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 쓰고 있는 닉네임이에요."),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "브랜드를 찾을 수 없어요."),
    CAMERA_NOT_FOUND(HttpStatus.NOT_FOUND, "기종을 찾을 수 없어요."),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "작례를 찾을 수 없어요."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "20MB보다 큰 파일은 올릴 수 없어요."),
    UPLOAD_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "한 시간에 올릴 수 있는 사진 수를 넘었어요. 잠시 후 다시 올려 주세요."),
    UPLOAD_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "지금 올리는 사람이 많아요. 잠시 후 다시 시도해 주세요."),
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
