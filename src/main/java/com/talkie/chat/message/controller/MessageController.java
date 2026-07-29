package com.talkie.chat.message.controller;

import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.global.exception.CommonErrorCode;
import com.talkie.chat.message.dto.MessageCursor;
import com.talkie.chat.message.dto.MessageResponse;
import com.talkie.chat.message.service.MessageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Validated
public class MessageController {

    private final MessageService messageService;

    /**
     * 커서는 이전 응답의 마지막 메시지 (createdAt, id)를 그대로 넘긴다. created_at 단독
     * 커서는 동일 시각에 걸친 메시지를 다음 페이지에서 누락시킬 수 있어, id를 타이브레이커로
     * 함께 받는다. 첫 페이지는 두 파라미터 모두 없이 요청한다.
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(@AuthenticationPrincipal Long userId,
                                                             @PathVariable Long roomId,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
                                                             @RequestParam(required = false) Long cursorId,
                                                             @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        MessageCursor cursor = toCursor(cursorCreatedAt, cursorId);
        List<MessageResponse> messageResponses = messageService.findMessagesByRoomId(userId, roomId, cursor, size);
        return ResponseEntity.ok(messageResponses);
    }

    private MessageCursor toCursor(LocalDateTime cursorCreatedAt, Long cursorId) {
        if (cursorCreatedAt == null && cursorId == null) {
            return null;
        }
        if (cursorCreatedAt == null || cursorId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new MessageCursor(cursorCreatedAt, cursorId);
    }
}
