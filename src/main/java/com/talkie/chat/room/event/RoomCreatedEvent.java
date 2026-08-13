package com.talkie.chat.room.event;

import java.util.Set;

public record RoomCreatedEvent(
        Set<Long> memberIds,
        Long roomId
) {
}