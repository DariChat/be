package com.talkie.chat.message.controller;

import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.global.exception.CommonErrorCode;
import com.talkie.chat.global.exception.ErrorResponse;
import com.talkie.chat.global.redis.ChatMessage;
import com.talkie.chat.global.redis.RedisPublisher;
import com.talkie.chat.message.dto.ChatMessageRequest;
import com.talkie.chat.message.dto.MessageResponse;
import com.talkie.chat.message.enums.PublishStatus;
import com.talkie.chat.message.exception.MessageErrorCode;
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
        MessageResponse messageResponse = messageService.saveMessage(userId, roomId, request.content(), request.clientMessageId());

        if (messageResponse.publishStatus() == PublishStatus.PUBLISHED) {
            return;
        }

        ChatMessage chatMessage = new ChatMessage(roomId, messageResponse);

        String json;
        try {
            json = objectMapper.writeValueAsString(chatMessage);
        } catch (Exception e) {
            messageService.markPublishFailed(messageResponse.id());
            throw new BusinessException(MessageErrorCode.SERIALIZATION_FAILED, e);
        }

        try {
            redisPublisher.publish(channelTopic, json);
        } catch (Exception e) {
            messageService.markPublishFailed(messageResponse.id());
            throw new BusinessException(MessageErrorCode.PUBLISH_FAILED, e);
        }

        messageService.markPublished(messageResponse.id());
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleException(BusinessException e) {
        return ErrorResponse.from(e.getErrorCode());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(CommonErrorCode.INVALID_INPUT.getMessage());
        return ErrorResponse.of(CommonErrorCode.INVALID_INPUT, message);
    }
}
