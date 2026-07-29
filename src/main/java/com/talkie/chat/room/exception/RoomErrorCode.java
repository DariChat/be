package com.talkie.chat.room.exception;

import com.talkie.chat.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RoomErrorCode implements ErrorCode {
    USER_NOT_FOUND("ROOM_001", "없는 유저입니다.", HttpStatus.NOT_FOUND),
    ROOM_NAME_REQUIRED("ROOM_002", "그룹 채팅은 방 이름이 필수입니다.", HttpStatus.BAD_REQUEST),
    INVITEE_NOT_FOUND("ROOM_003", "존재하지 않는 초대 대상이 있습니다.", HttpStatus.NOT_FOUND),
    NOT_ROOM_MEMBER("ROOM_004", "해당 방의 회원이 아닙니다.", HttpStatus.FORBIDDEN),
    ROOM_NOT_FOUND("ROOM_005", "방을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    MEMBER_IDS_REQUIRED("ROOM_006", "초대할 대상이 최소 1명 필요합니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}