package uk.gegc.kidsgptbackend.dto.story;

import java.time.LocalDateTime;
import java.util.UUID;

public record StartStoryResponse(
        UUID storyId,
        String title,
        String encouragingMessage,
        String model,
        long latencyMs,
        int tokensUsed,
        LocalDateTime createdAt
) {
} 