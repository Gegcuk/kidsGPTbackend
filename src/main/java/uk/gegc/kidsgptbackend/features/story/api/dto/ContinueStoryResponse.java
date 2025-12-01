package uk.gegc.kidsgptbackend.features.story.api.dto;

import java.util.UUID;

public record ContinueStoryResponse(
        UUID storyId,
        String reply,
        String model,
        long latencyMs,
        int tokensUsed
) {
}

