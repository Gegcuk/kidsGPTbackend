package uk.gegc.kidsgptbackend.features.story.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Single message within a story conversation")
public record StoryMessageDto(
        @Schema(description = "Message identifier")
        UUID id,
        @Schema(description = "Role of sender (user/assistant)")
        String role,
        @Schema(description = "Content of the message")
        String content,
        @Schema(description = "Creation timestamp")
        LocalDateTime createdAt
) {
}
