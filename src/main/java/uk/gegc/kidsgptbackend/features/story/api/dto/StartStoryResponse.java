package uk.gegc.kidsgptbackend.features.story.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Response after starting a story")
public record StartStoryResponse(
        @Schema(description = "New story identifier")
        UUID storyId,
        @Schema(description = "Story title")
        String title,
        @Schema(description = "Friendly message for the user")
        String encouragingMessage,
        @Schema(description = "Model identifier used")
        String model,
        @Schema(description = "End-to-end latency in ms")
        long latencyMs,
        @Schema(description = "Tokens consumed")
        int tokensUsed,
        @Schema(description = "Creation timestamp")
        LocalDateTime createdAt
) {
}
