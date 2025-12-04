package uk.gegc.kidsgptbackend.features.story.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.story.domain.model.StoryStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Full story with messages")
public record StoryDto(
        @Schema(description = "Story identifier")
        UUID id,
        @Schema(description = "Story title")
        String title,
        @Schema(description = "Story generation status")
        StoryStatus status,
        @Schema(description = "Messages forming the story")
        List<StoryMessageDto> messages,
        @Schema(description = "Creation timestamp")
        LocalDateTime createdAt,
        @Schema(description = "Update timestamp")
        LocalDateTime updatedAt
) {
}
