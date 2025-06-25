package uk.gegc.kidsgptbackend.dto.image;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageGenerationRequest(
        @NotBlank(message = "Description is required")
        @Size(max = 1000, message = "Description must be less than 1000 characters")
        String description,
        
        @Size(max = 100, message = "Style must be less than 100 characters")
        String style  // Optional style preference (e.g., "cartoon", "realistic", "colorful")
) {
} 