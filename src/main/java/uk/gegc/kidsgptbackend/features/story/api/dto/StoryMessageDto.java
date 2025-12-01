package uk.gegc.kidsgptbackend.features.story.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record StoryMessageDto(
        UUID id,
        String role,
        String content,
        LocalDateTime createdAt
) {
}

