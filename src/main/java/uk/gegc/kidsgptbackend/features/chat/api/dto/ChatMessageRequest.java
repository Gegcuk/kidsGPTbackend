package uk.gegc.kidsgptbackend.features.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ChatMessageRequest(
        @NotBlank
        String message,
        UUID contextId,
        @NotNull
        Tone tone,
        List<ChatMessageDto> context
) {
}

