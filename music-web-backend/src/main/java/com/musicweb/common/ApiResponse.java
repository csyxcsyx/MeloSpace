package com.musicweb.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ApiResponse<T>(int code, String message, T data, OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null, now());
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null, now());
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.ofHours(8));
    }
}
