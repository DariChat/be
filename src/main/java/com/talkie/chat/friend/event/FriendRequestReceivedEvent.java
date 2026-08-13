package com.talkie.chat.friend.event;

import com.talkie.chat.friend.dto.FriendRequestResponse;

public record FriendRequestReceivedEvent(
        Long addresseeId,
        FriendRequestResponse request
) {
}