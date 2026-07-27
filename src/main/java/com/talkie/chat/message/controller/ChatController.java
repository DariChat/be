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
        // saveMessage는 clientMessageId로 멱등하게 저장한다. 발행 실패 후 클라이언트가
        // 같은 clientMessageId로 재요청하면 새로 저장하지 않고 기존 메시지를 그대로 사용한다.
        MessageResponse messageResponse = messageService.saveMessage(userId, roomId, request.content(), request.clientMessageId());

        if (messageResponse.publishStatus() == PublishStatus.PUBLISHED
                || messageResponse.publishStatus() == PublishStatus.PUBLISHING) {
            // 이미 발행에 성공했거나(PUBLISHED) 다른 요청이 지금 발행 중인(PUBLISHING)
            // 메시지의 재요청 - 중복 브로드캐스트를 막기 위해 재발행하지 않는다.
            return;
        }

        // PENDING/FAILED 상태일 때만 PUBLISHING으로 원자적 전이를 시도한다. 같은
        // clientMessageId로 요청이 동시에 겹쳐도 단 하나만 true를 받아 실제로
        // Redis에 발행하고, 나머지는 false를 받아 여기서 조용히 종료한다.
        if (!messageService.tryStartPublishing(messageResponse.id())) {
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
            // DB 저장은 이미 커밋된 상태 - 메시지를 취소하는 대신 발행 실패로 표시해
            // 상태 불일치(저장은 됐는데 아무도 못 받은 메시지)를 드러내고, 재시도는
            // 클라이언트가 동일한 clientMessageId로 재요청하도록 위임한다.
            messageService.markPublishFailed(messageResponse.id());
            throw new BusinessException(MessageErrorCode.PUBLISH_FAILED, e);
        }

        // publish는 이미 성공했으므로 여기서부터는 절대 재발행하면 안 된다.
        // markPublished는 내부에서 재시도 후 실패하면 로그만 남기고 예외를 던지지 않는다 -
        // 여기서 예외가 나가면 클라이언트가 같은 clientMessageId로 재요청했을 때
        // publishStatus가 여전히 PENDING이라 중복 브로드캐스트로 이어지기 때문이다.
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
