package com.talkie.chat.message.dto;

import com.talkie.chat.message.entity.Message;
import com.talkie.chat.message.entity.MessageTranslation;
import com.talkie.chat.message.enums.PublishStatus;
import com.talkie.chat.user.enums.PreferredLanguage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record MessageResponse(
        Long id,
        String content,
        String senderNickname,
        String clientMessageId,
        PublishStatus publishStatus,
        Map<PreferredLanguage, String> translations,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message message) {
        return from(message, List.of());
    }

    public static MessageResponse from(Message message, List<MessageTranslation> translations) {
        return new MessageResponse(
                message.getId(),
                message.getContent(),
                message.getUser().getNickname(),
                message.getClientMessageId(),
                message.getPublishStatus(),
                translations.stream()
                        .collect(Collectors.toMap(MessageTranslation::getLanguage, MessageTranslation::getTranslatedContent)),
                message.getCreatedAt()
        );
    }
}
