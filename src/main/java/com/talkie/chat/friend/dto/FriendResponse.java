package com.talkie.chat.friend.dto;

import com.talkie.chat.user.entity.User;

public record FriendResponse(
        Long friendshipId,
        Long userId,
        String nickname,
        String profileImageUrl
) {
    public static FriendResponse from(User friend) {
        return new FriendResponse(
                null,
                friend.getId(),
                friend.getNickname(),
                friend.getProfileImageUrl()
        );
    }

    public static FriendResponse of(Long friendshipId, User friend) {
        return new FriendResponse(
                friendshipId,
                friend.getId(),
                friend.getNickname(),
                friend.getProfileImageUrl()
        );
    }
}