package uk.gegc.kidsgptbackend.features.story.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.chat.api.dto.Tone;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request to continue an existing story")
public record ContinueStoryRequest(
        @Schema(description = "Story identifier")
        @NotNull(message = "Story ID is required")
        UUID storyId,
        
        @Schema(description = "User contribution to the story")
        @NotBlank(message = "Story message is required")
        @Size(max = 1000, message = "Story message must be less than 1000 characters")
        String message,
        
        @Schema(description = "Tone for the AI continuation")
        @NotNull(message = "Tone is required")
        Tone tone,
        
        // Context history provided by client (like chat API)
        @Schema(description = "Optional previous messages to preserve context")
        List<StoryMessageDto> context
) {
}
