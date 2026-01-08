package uk.gegc.kidsgptbackend.features.image.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Prompt for generating an image")
public record ImageGenerationRequest(
        @Schema(description = "Text description of the desired image")
        @NotBlank(message = "Description is required")
        @Size(max = 1000, message = "Description must be less than 1000 characters")
        String description,
        
        @Schema(description = "Optional style hint, e.g., cartoon/realistic")
        @Size(max = 100, message = "Style must be less than 100 characters")
        String style  // Optional style preference (e.g., "cartoon", "realistic", "colorful")
) {
}
