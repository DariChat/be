package com.talkie.chat.user.dto;

import com.talkie.chat.user.entity.User;
import com.talkie.chat.user.enums.PreferredLanguage;

public record UserRecommendationResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        String bio,
        PreferredLanguage preferredLanguage
) {
    public static UserRecommendationResponse from(User user) {
        return new UserRecommendationResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getBio(),
                user.getPreferredLanguage()
        );
    }
}