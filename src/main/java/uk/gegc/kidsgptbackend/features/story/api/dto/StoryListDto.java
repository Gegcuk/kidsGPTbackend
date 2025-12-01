package uk.gegc.kidsgptbackend.features.story.api.dto;

import uk.gegc.kidsgptbackend.features.story.domain.model.StoryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record StoryListDto(
        UUID id,
        String title,
        StoryStatus status,
        int messageCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

