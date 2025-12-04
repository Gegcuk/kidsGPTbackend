package uk.gegc.kidsgptbackend.features.story.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.story.domain.model.StoryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Lightweight story summary for list views")
public record StoryListDto(
        @Schema(description = "Story identifier")
        UUID id,
        @Schema(description = "Story title")
        String title,
        @Schema(description = "Story generation status")
        StoryStatus status,
        @Schema(description = "Number of messages in the story")
        int messageCount,
        @Schema(description = "Creation timestamp")
        LocalDateTime createdAt,
        @Schema(description = "Update timestamp")
        LocalDateTime updatedAt
) {
}
