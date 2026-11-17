package com.dicajakrye.common;

/** 사용자에게 에러 코드로 알려야 하는 예외. GlobalExceptionHandler가 { code, message }로 바꾼다. */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
