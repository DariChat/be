package com.talkie.chat.auth.dto;

import com.talkie.chat.user.enums.PreferredLanguage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(max = 100) String name,
        @Email @NotBlank @Size(max = 100) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 20) String nickname,
        @NotNull PreferredLanguage preferredLanguage
) {
}
