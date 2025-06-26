package uk.gegc.kidsgptbackend.dto.story;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContinueStoryRequest(
        @NotBlank(message = "Story content is required")
        @Size(max = 1000, message = "Story content must be less than 1000 characters")
        String content
) {
} 