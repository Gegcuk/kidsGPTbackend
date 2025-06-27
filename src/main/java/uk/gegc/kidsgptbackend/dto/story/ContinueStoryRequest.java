package uk.gegc.kidsgptbackend.dto.story;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uk.gegc.kidsgptbackend.dto.chat.Tone;

import java.util.List;
import java.util.UUID;

public record ContinueStoryRequest(
        @NotNull(message = "Story ID is required")
        UUID storyId,
        
        @NotBlank(message = "Story message is required")
        @Size(max = 1000, message = "Story message must be less than 1000 characters")
        String message,
        
        @NotNull(message = "Tone is required")
        Tone tone,
        
        // Context history provided by client (like chat API)
        List<StoryMessageDto> context
) {
} 