package com.talkie.chat.room.dto;

import com.talkie.chat.room.enums.RoomEventType;

public record RoomEvent(
        RoomEventType type,
        RoomSummaryResponse room
) {
    public static RoomEvent created(RoomSummaryResponse room) {
        return new RoomEvent(RoomEventType.ROOM_CREATED, room);
    }

    public static RoomEvent updated(RoomSummaryResponse room) {
        return new RoomEvent(RoomEventType.ROOM_UPDATED, room);
    }
}