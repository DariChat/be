package com.talkie.chat.friend.dto;

import com.talkie.chat.user.entity.User;

public record FriendResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {
    public static FriendResponse from(User friend) {
        return new FriendResponse(
                friend.getId(),
                friend.getNickname(),
                friend.getProfileImageUrl()
        );
    }
}