package com.talkie.chat.global.exception;

public record ErrorResponse(
        boolean success,
        ErrorDetail error
) {
    public record ErrorDetail(
            String code,
            String message
    ) {
    }

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(false, new ErrorDetail(errorCode.getCode(), errorCode.getMessage()));
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(false, new ErrorDetail(errorCode.getCode(), message));
    }
}
