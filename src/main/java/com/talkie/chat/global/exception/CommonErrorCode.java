package com.talkie.chat.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT("COMMON_001", "입력값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("COMMON_002", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST_BODY("COMMON_003", "요청 본문을 읽을 수 없습니다.", HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER("COMMON_004", "필수 파라미터가 누락되었습니다.", HttpStatus.BAD_REQUEST),
    TYPE_MISMATCH("COMMON_005", "파라미터 타입이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED("COMMON_006", "지원하지 않는 HTTP 메서드입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_FOUND("COMMON_007", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    UNSUPPORTED_MEDIA_TYPE("COMMON_008", "지원하지 않는 Content-Type입니다.", HttpStatus.UNSUPPORTED_MEDIA_TYPE);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}