package com.talkie.chat.auth.exception;

import com.talkie.chat.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    DUPLICATE_EMAIL("AUTH_001", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    DUPLICATE_NICKNAME("AUTH_002", "이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),
    DUPLICATE_EMAIL_OR_NICKNAME("AUTH_003", "이미 사용 중인 이메일 또는 닉네임입니다.", HttpStatus.CONFLICT),
    LOGIN_FAILED("AUTH_004", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("AUTH_005", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}