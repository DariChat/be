package com.talkie.chat.friend.dto;

import com.talkie.chat.friend.enums.FriendEventType;

public record FriendEvent(
        FriendEventType type,
        FriendRequestResponse request,
        FriendResponse friend
) {
    public static FriendEvent requestReceived(FriendRequestResponse request) {
        return new FriendEvent(FriendEventType.REQUEST_RECEIVED, request, null);
    }

    public static FriendEvent requestAccepted(FriendResponse friend) {
        return new FriendEvent(FriendEventType.REQUEST_ACCEPTED, null, friend);
    }
}