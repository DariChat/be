package com.talkie.chat.global.exception;

public record ApiResponse<T>(
        boolean success,
        T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null);
    }
}
