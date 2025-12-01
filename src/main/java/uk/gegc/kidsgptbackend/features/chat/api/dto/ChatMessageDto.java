package uk.gegc.kidsgptbackend.features.chat.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageDto(
        UUID id,
        String role,
        String content,
        LocalDateTime createdAt
) {
}

