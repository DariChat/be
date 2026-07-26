package com.talkie.chat.message.controller;

import com.talkie.chat.global.redis.ChatMessage;
import com.talkie.chat.global.redis.RedisPublisher;
import com.talkie.chat.message.dto.ChatMessageRequest;
import com.talkie.chat.message.dto.MessageResponse;
import com.talkie.chat.message.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final RedisPublisher redisPublisher;
    private final ObjectMapper objectMapper;
    private final ChannelTopic channelTopic;
    private final MessageService messageService;

    @MessageMapping("/rooms/{roomId}/send")
    public void sendMessage(@DestinationVariable Long roomId, @Valid @Payload ChatMessageRequest request, Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        MessageResponse messageResponse = messageService.saveMessage(userId, roomId, request.content());

        ChatMessage chatMessage = new ChatMessage(roomId, messageResponse);

        String json;
        try {
            json = objectMapper.writeValueAsString(chatMessage);
        } catch (Exception e) {
            throw new IllegalArgumentException("메시지 직렬화 실패", e);
        }

        // TODO: publish 실패 시 재시도/보정 전략 필요 (DB 저장은 완료된 상태)
        try {
            redisPublisher.publish(channelTopic, json);
        } catch (Exception e) {
            throw new IllegalArgumentException("메시지 발행 실패", e);
        }
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/errors")
    public String handleException(IllegalArgumentException e) {
        return e.getMessage();
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public String handleValidationException(MethodArgumentNotValidException e) {
        return "입력값이 올바르지 않습니다.";
    }
}
