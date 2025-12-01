package uk.gegc.kidsgptbackend.features.story.api.dto;

import uk.gegc.kidsgptbackend.features.story.domain.model.StoryStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StoryDto(
        UUID id,
        String title,
        StoryStatus status,
        List<StoryMessageDto> messages,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

