package uk.gegc.kidsgptbackend.dto.story;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ContinueStoryRequest(
        @NotBlank(message = "Story message is required")
        @Size(max = 1000, message = "Story message must be less than 1000 characters")
        String message,
        
        // Context history provided by client (like chat API)
        List<StoryMessageDto> context
) {
} 