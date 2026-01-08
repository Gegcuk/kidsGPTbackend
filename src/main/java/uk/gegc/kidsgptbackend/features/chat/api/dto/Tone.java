package uk.gegc.kidsgptbackend.features.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tone of voice for AI responses")
public enum Tone {
    FRIENDLY,
    FORMAL,
    FUN
}
