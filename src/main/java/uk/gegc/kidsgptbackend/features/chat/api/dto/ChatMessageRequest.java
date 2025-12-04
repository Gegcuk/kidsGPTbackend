package uk.gegc.kidsgptbackend.features.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Chat message request sent to the AI model")
public record ChatMessageRequest(
        @Schema(description = "User's prompt text")
        @NotBlank
        String message,

        @Schema(description = "Existing conversation/context identifier")
        UUID contextId,

        @Schema(description = "Desired response tone")
        @NotNull
        Tone tone,

        @Schema(description = "Optional previous messages to maintain context")
        List<ChatMessageDto> context
) {
}
