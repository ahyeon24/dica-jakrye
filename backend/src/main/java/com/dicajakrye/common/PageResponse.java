package com.dicajakrye.common;

import java.util.List;

/** 커서 페이지네이션 응답. nextCursor가 null이면 마지막 페이지. */
public record PageResponse<T>(List<T> items, String nextCursor) {
}
