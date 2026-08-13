package com.talkie.chat.friend.dto;

import com.talkie.chat.friend.entity.Friendship;
import com.talkie.chat.friend.enums.FriendshipStatus;

import java.time.LocalDateTime;

public record FriendRequestResponse(
        Long friendshipId,
        Long requesterId,
        String requesterNickname,
        String requesterProfileImageUrl,
        FriendshipStatus status,
        LocalDateTime createdAt
) {
    public static FriendRequestResponse from(Friendship friendship) {
        return new FriendRequestResponse(
                friendship.getId(),
                friendship.getRequester().getId(),
                friendship.getRequester().getNickname(),
                friendship.getRequester().getProfileImageUrl(),
                friendship.getStatus(),
                friendship.getCreatedAt()
        );
    }
}