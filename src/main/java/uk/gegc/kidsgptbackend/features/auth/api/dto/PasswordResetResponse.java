package uk.gegc.kidsgptbackend.features.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Confirmation that a reset email was sent")
public record PasswordResetResponse(
        @Schema(description = "User-facing message")
        String message,
        @Schema(description = "Expiration timestamp for the reset token")
        LocalDateTime expiresAt
) {
}
