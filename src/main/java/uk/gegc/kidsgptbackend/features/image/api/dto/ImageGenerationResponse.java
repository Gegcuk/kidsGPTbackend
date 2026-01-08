package uk.gegc.kidsgptbackend.features.image.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of an image generation request")
public record ImageGenerationResponse(
        @Schema(description = "URL of the generated image")
        String imageUrl,
        @Schema(description = "Prompt after safety rewriting")
        String revisedPrompt,  // The actual prompt used by DALL-E after safety filtering
        @Schema(description = "Model identifier used to generate the image")
        String model,
        @Schema(description = "End-to-end latency in milliseconds")
        long latencyMs,
        @Schema(description = "Age group context used for safety")
        String ageGroup
) {
}
