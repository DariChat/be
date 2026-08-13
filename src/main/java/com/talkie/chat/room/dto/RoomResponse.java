package com.talkie.chat.room.dto;

import com.talkie.chat.room.entity.Room;
import com.talkie.chat.room.enums.RoomType;

public record RoomResponse(
        Long roomId,
        String roomName,
        RoomType roomType,
        int memberCount,
        boolean alreadyExists
) {
    public static RoomResponse of(Room room, String roomName, int memberCount, boolean alreadyExists) {
        return new RoomResponse(
                room.getId(),
                roomName,
                room.getRoomType(),
                memberCount,
                alreadyExists
        );
    }
}
