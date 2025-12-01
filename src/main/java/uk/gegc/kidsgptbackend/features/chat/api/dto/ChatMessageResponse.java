package uk.gegc.kidsgptbackend.features.chat.api.dto;

import java.util.UUID;

public record ChatMessageResponse(
        String reply,
        String model,
        long latencyMs,
        int tokensUsed,
        UUID contextId,
        UUID responseMessageId,
        UUID repliedMessageId
) {
}

