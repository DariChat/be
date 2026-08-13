package com.talkie.chat.message.event;

public record MessageBroadcastedEvent(
        Long roomId,
        Long messageId,
        Long senderId
) {
}
