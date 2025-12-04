package uk.gegc.kidsgptbackend.features.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Stored chat message within a conversation")
public record ChatMessageDto(
        @Schema(description = "Message identifier")
        UUID id,
        @Schema(description = "Role of the sender (user/assistant/system)")
        String role,
        @Schema(description = "Message content")
        String content,
        @Schema(description = "Creation timestamp")
        LocalDateTime createdAt
) {
}
