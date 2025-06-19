package uk.gegc.kidsgptbackend.dto.chat;

import java.util.UUID;

public record ChatMessageResponse(
        String reply,
        String model,
        long latencyMs,
        int tokensUsed,
        UUID contextId
) {
}
