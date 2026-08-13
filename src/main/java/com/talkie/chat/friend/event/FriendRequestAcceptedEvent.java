package com.talkie.chat.friend.event;

import com.talkie.chat.friend.dto.FriendResponse;

public record FriendRequestAcceptedEvent(
        Long requesterId,
        FriendResponse friend
) {
}