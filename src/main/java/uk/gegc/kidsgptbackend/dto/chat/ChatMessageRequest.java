package uk.gegc.kidsgptbackend.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatMessageRequest(
        @NotBlank
        String message,
        UUID contextId,
        @NotNull
        Tone tone
) {
}
