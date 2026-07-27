package com.talkie.chat.message.dto;

import com.talkie.chat.message.entity.Message;
import com.talkie.chat.message.enums.PublishStatus;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        String content,
        String senderNickname,
        String clientMessageId,
        PublishStatus publishStatus,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getContent(),
                message.getUser().getNickname(),
                message.getClientMessageId(),
                message.getPublishStatus(),
                message.getCreatedAt()
        );
    }
}
