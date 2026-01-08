package uk.gegc.kidsgptbackend.features.story.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "AI continuation response for a story")
public record ContinueStoryResponse(
        @Schema(description = "Story identifier")
        UUID storyId,
        @Schema(description = "AI-generated continuation")
        String reply,
        @Schema(description = "Model identifier used")
        String model,
        @Schema(description = "End-to-end latency in ms")
        long latencyMs,
        @Schema(description = "Tokens consumed")
        int tokensUsed
) {
}
