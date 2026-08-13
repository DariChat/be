package com.talkie.chat.friend.exception;

import com.talkie.chat.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FriendErrorCode implements ErrorCode {
    USER_NOT_FOUND("FRIEND_001", "존재하지 않는 유저입니다.", HttpStatus.NOT_FOUND),
    SELF_REQUEST_NOT_ALLOWED("FRIEND_002", "본인에게는 친구 요청을 보낼 수 없습니다.", HttpStatus.BAD_REQUEST),
    FRIENDSHIP_ALREADY_EXISTS("FRIEND_003", "이미 친구이거나 요청을 보낸 상대입니다.", HttpStatus.CONFLICT),
    FRIENDSHIP_NOT_FOUND("FRIEND_004", "존재하지 않는 친구 요청입니다.", HttpStatus.NOT_FOUND),
    NOT_FRIENDSHIP_PARTICIPANT("FRIEND_005", "본인과 관련된 친구 요청이 아닙니다.", HttpStatus.FORBIDDEN),
    NOT_REQUEST_ADDRESSEE("FRIEND_006", "요청을 받은 사람만 수락할 수 있습니다.", HttpStatus.FORBIDDEN),
    FRIENDSHIP_NOT_PENDING("FRIEND_007", "이미 처리된 친구 요청입니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}