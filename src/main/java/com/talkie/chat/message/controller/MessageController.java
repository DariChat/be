package com.talkie.chat.message.controller;

import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.global.exception.CommonErrorCode;
import com.talkie.chat.global.exception.ErrorResponse;
import com.talkie.chat.message.dto.MessageCursor;
import com.talkie.chat.message.dto.MessageResponse;
import com.talkie.chat.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Message", description = "채팅 메시지 조회 (실시간 송수신은 WebSocket/STOMP)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Validated
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "채팅 메시지 커서 조회", description = "cursorCreatedAt/cursorId를 함께 생략하면 최신 메시지부터 조회한다. " +
            "둘 중 하나만 주면 400. 실시간 송수신은 STOMP `/pub/rooms/{roomId}/send` 발행, `/sub/rooms/{roomId}` 구독을 사용한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "cursorCreatedAt/cursorId 중 하나만 전달됨",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "방 멤버가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(@AuthenticationPrincipal Long userId,
                                                             @PathVariable Long roomId,
                                                             @Parameter(description = "이 시각 이전 메시지부터 조회 (cursorId와 함께 사용)")
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
                                                             @Parameter(description = "동시각 메시지 타이브레이커용 커서 id")
                                                             @RequestParam(required = false) Long cursorId,
                                                             @Parameter(description = "페이지 크기 (1~100)")
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
