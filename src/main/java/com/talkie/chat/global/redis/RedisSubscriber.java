package com.talkie.chat.global.redis;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        try {
            ChatMessage chatMessage = objectMapper.readValue(message.getBody(), ChatMessage.class);
            Long roomId = chatMessage.roomId();
            messagingTemplate.convertAndSend("/sub/rooms/" + roomId, chatMessage.message());
            eventPublisher.publishEvent(new MessageBroadcastedEvent(roomId, chatMessage.message().id()));
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 메시지입니다.", e);
        }
    }
}
