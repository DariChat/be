package com.talkie.chat.message.controller;

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
     * 커서는 이전 응답의 마지막 메시지 createdAt을 그대로 넘긴다. id 대신 created_at으로
     * 정렬/커서링하는 이유는 (room_id, created_at) 복합 인덱스를 filesort 없이 타기
     * 위함이다. 첫 페이지는 cursor 없이 요청한다.
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(@AuthenticationPrincipal Long userId,
                                                             @PathVariable Long roomId,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursor,
                                                             @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        List<MessageResponse> messageResponses = messageService.findMessagesByRoomId(userId, roomId, cursor, size);
        return ResponseEntity.ok(messageResponses);
    }
}
