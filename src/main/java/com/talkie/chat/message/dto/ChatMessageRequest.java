package com.talkie.chat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotBlank @Size(max = 500) String content,
        @NotBlank @Size(max = 100) String clientMessageId
) {
}
