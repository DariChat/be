package com.talkie.chat.message.dto;

import java.time.LocalDateTime;

public record MessageCursor(
        LocalDateTime createdAt,
        Long id
) {
}
