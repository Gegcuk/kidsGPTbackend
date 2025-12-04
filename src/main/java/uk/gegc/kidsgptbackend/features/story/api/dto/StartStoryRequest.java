package uk.gegc.kidsgptbackend.features.story.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to start a new AI-generated story")
public record StartStoryRequest(
        @Schema(description = "Story title")
        @NotBlank(message = "Story title is required")
        @Size(max = 100, message = "Story title must be less than 100 characters")
        String title,
        
        @Schema(description = "Optional initial idea or prompt")
        @Size(max = 500, message = "Initial idea must be less than 500 characters")
        String initialIdea  // Optional - user can provide initial story idea
) {
}
