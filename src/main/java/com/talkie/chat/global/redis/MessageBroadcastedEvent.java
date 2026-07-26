package com.talkie.chat.global.redis;

public record MessageBroadcastedEvent(
        Long roomId,
        Long messageId
) {
}