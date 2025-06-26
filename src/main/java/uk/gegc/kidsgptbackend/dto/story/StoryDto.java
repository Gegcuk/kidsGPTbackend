package uk.gegc.kidsgptbackend.dto.story;

import uk.gegc.kidsgptbackend.model.story.StoryStatus;

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