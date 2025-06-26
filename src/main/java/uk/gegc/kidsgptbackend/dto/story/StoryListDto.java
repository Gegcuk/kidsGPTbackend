package uk.gegc.kidsgptbackend.dto.story;

import uk.gegc.kidsgptbackend.model.story.StoryStatus;

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