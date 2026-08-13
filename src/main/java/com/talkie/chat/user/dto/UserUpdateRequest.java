package com.talkie.chat.user.dto;

import com.talkie.chat.user.enums.PreferredLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserUpdateRequest(
        @NotBlank String nickname,
        String profileImageUrl,
        @NotNull PreferredLanguage preferredLanguage
) {
}
