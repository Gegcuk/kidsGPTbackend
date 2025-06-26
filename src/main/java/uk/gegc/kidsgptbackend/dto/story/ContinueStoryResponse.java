package uk.gegc.kidsgptbackend.dto.story;

import java.util.UUID;

public record ContinueStoryResponse(
        UUID storyId,
        String reply,
        String model,
        long latencyMs,
        int tokensUsed
) {
} 