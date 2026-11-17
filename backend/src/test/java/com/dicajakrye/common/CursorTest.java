package com.dicajakrye.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CursorTest {

    @Test
    void 인코딩한_커서를_다시_읽으면_같은_값이다() {
        Cursor c = Cursor.of(Instant.parse("2026-11-17T04:05:06.123456789Z"), 42);
        Cursor back = Cursor.decode(c.encode());
        assertThat(back.id()).isEqualTo(42);
        assertThat(back.createdAt()).isEqualTo(Instant.parse("2026-11-17T04:05:06.123456Z")); // 마이크로초까지
    }

    @Test
    void 비어_있으면_null() {
        assertThat(Cursor.decode(null)).isNull();
        assertThat(Cursor.decode("")).isNull();
    }

    @Test
    void 잘못된_커서는_INVALID_REQUEST() {
        assertThatThrownBy(() -> Cursor.decode("이상한값!!"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> Cursor.decode("YWJj")) // "abc"
                .isInstanceOf(ApiException.class);
    }
}
