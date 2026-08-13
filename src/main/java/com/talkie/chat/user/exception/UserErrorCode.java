package com.talkie.chat.user.exception;

import com.talkie.chat.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("USER_001", "존재하지 않는 유저입니다.", HttpStatus.NOT_FOUND),
    SEARCH_KEYWORD_REQUIRED("USER_002", "검색어를 입력해주세요.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}