package com.talkie.chat.room.event;

import java.util.Set;

public record RoomUpdatedEvent(
        Set<Long> memberIds,
        Long roomId
) {
}