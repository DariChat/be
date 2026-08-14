package com.talkie.chat.user.dto;

import com.talkie.chat.user.enums.PreferredLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank String nickname,
        String profileImageUrl,
        @NotNull PreferredLanguage preferredLanguage,
        @Size(max = 200) String bio
) {
}
