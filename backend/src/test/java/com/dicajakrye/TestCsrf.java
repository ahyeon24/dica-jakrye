package com.dicajakrye;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 실제 브라우저처럼 XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더를 같은 값으로 보낸다.
 * spring-security-test의 csrf()는 앱의 CSRF 저장소를 세션 방식으로 바꿔 버려서(테스트 사이에 남음) 쓰지 않는다.
 */
public final class TestCsrf {

    private static final String TOKEN = "test-csrf-token";

    private TestCsrf() {
    }

    public static RequestPostProcessor csrf() {
        return request -> {
            List<Cookie> cookies = new ArrayList<>();
            if (request.getCookies() != null) {
                cookies.addAll(Arrays.asList(request.getCookies()));
            }
            cookies.add(new Cookie("XSRF-TOKEN", TOKEN));
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", TOKEN);
            return request;
        };
    }
}
