package com.talkie.chat.message.exception;

import com.talkie.chat.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MessageErrorCode implements ErrorCode {
    NOT_ROOM_MEMBER("MESSAGE_001", "채팅방 멤버가 아닙니다.", HttpStatus.FORBIDDEN),
    SERIALIZATION_FAILED("MESSAGE_002", "메시지 직렬화 실패", HttpStatus.INTERNAL_SERVER_ERROR),
    PUBLISH_FAILED("MESSAGE_003", "메시지 발행 실패", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_BROADCAST_MESSAGE("MESSAGE_004", "유효하지 않은 메시지입니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    CLIENT_MESSAGE_ID_CONFLICT("MESSAGE_005", "이미 사용된 clientMessageId입니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}