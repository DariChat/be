package com.talkie.chat.room.dto;

import java.time.LocalDateTime;

public record RoomSummaryResponse(
        Long roomId,
        String roomName,
        String lastMessage,
        LocalDateTime lastMessageAt,
        int memberCount,
        int unreadCount
) {
}
