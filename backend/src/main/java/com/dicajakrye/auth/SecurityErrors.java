package com.dicajakrye.auth;

import com.dicajakrye.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** 스프링 시큐리티 단계(컨트롤러 밖)에서 막힌 요청도 { code, message } 형식으로 응답한다 (API.md 1.4) */
final class SecurityErrors {

    private SecurityErrors() {
    }

    static void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 코드와 메시지는 고정된 상수라 따옴표·역슬래시가 없다
        response.getWriter().write("{\"code\":\"" + code.name() + "\",\"message\":\"" + code.message() + "\"}");
    }
}
