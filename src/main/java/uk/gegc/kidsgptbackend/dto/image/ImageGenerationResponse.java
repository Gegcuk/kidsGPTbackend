package uk.gegc.kidsgptbackend.dto.image;

public record ImageGenerationResponse(
        String imageUrl,
        String revisedPrompt,  // The actual prompt used by DALL-E after safety filtering
        String model,
        long latencyMs,
        String ageGroup
) {
} 