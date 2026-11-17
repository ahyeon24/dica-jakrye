package com.dicajakrye.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 에러를 { code, message } 형식으로 내려준다. (docs/API.md 1.4)
 * 스택 트레이스, SQL 오류, 내부 클래스 이름은 응답에 넣지 않는다. (docs/SECURITY.md 11장)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(String code, String message) {
        static ErrorResponse of(ErrorCode c) {
            return new ErrorResponse(c.name(), c.message());
        }
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.errorCode());
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
    })
    ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return respond(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(new ErrorResponse("NOT_FOUND", "찾을 수 없는 주소예요."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        return respond(ErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ErrorResponse> respond(ErrorCode c) {
        return ResponseEntity.status(c.status()).body(ErrorResponse.of(c));
    }
}
