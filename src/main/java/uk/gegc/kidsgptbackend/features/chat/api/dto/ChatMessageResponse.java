package uk.gegc.kidsgptbackend.features.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "AI response payload for a chat message")
public record ChatMessageResponse(
        @Schema(description = "AI-generated reply text")
        String reply,
        @Schema(description = "Model identifier used for the response")
        String model,
        @Schema(description = "End-to-end latency in milliseconds")
        long latencyMs,
        @Schema(description = "Total tokens consumed for the call")
        int tokensUsed,
        @Schema(description = "Conversation/context identifier")
        UUID contextId,
        @Schema(description = "Stored message ID of the AI reply")
        UUID responseMessageId,
        @Schema(description = "Message ID that was replied to (if any)")
        UUID repliedMessageId
) {
}
