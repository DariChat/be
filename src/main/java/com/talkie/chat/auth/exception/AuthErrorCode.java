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
    INVALID_TOKEN("AUTH_005", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EMAIL_NOT_VERIFIED("AUTH_006", "이메일 인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    VERIFICATION_CODE_NOT_FOUND("AUTH_007", "인증코드가 만료되었거나 존재하지 않습니다. 재발송해주세요.", HttpStatus.BAD_REQUEST),
    VERIFICATION_CODE_MISMATCH("AUTH_008", "인증코드가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_VERIFIED("AUTH_009", "이미 인증된 이메일입니다.", HttpStatus.CONFLICT),
    VERIFICATION_EMAIL_NOT_FOUND("AUTH_010", "가입되지 않은 이메일입니다.", HttpStatus.NOT_FOUND),
    VERIFICATION_RESEND_TOO_SOON("AUTH_011", "잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_SEND_FAILED("AUTH_012", "인증 메일 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}