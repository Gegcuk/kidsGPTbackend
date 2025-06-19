package uk.gegc.kidsgptbackend.dto.chat;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ChatMessageRequest(
        @NotBlank
        String message,
        UUID contextId,
        @NotBlank
        String tone
) {
}
