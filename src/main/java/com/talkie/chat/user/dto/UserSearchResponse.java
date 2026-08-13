package com.talkie.chat.user.dto;

import com.talkie.chat.user.entity.User;

public record UserSearchResponse(
        Long id,
        String nickname,
        String profileImageUrl
) {
    public static UserSearchResponse from(User user) {
        return new UserSearchResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}