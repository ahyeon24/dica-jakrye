package com.dicajakrye.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SafeRedirectTest {

    @Test
    void 내부_경로는_그대로() {
        assertThat(SafeRedirect.sanitize("/cameras/canon-ixus-30?flash=true")).isEqualTo("/cameras/canon-ixus-30?flash=true");
        assertThat(SafeRedirect.sanitize("/")).isEqualTo("/");
    }

    @Test
    void 외부로_보낼_수_있는_값은_홈으로() {
        assertThat(SafeRedirect.sanitize("https://evil.example.com")).isEqualTo("/");
        assertThat(SafeRedirect.sanitize("//evil.example.com")).isEqualTo("/");
        assertThat(SafeRedirect.sanitize("/\\evil.example.com")).isEqualTo("/");
        assertThat(SafeRedirect.sanitize("/a\\b")).isEqualTo("/");
        assertThat(SafeRedirect.sanitize("/a\r\nSet-Cookie: x=1")).isEqualTo("/");
        assertThat(SafeRedirect.sanitize("javascript:alert(1)")).isEqualTo("/");
        assertThat(SafeRedirect.sanitize("")).isEqualTo("/");
        assertThat(SafeRedirect.sanitize(null)).isEqualTo("/");
        assertThat(SafeRedirect.sanitize("/" + "a".repeat(600))).isEqualTo("/");
    }
}
