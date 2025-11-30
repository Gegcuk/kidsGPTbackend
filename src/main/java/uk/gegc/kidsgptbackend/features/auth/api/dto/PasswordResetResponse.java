package uk.gegc.kidsgptbackend.features.auth.api.dto;

import java.time.LocalDateTime;

public record PasswordResetResponse(
        String message,
        LocalDateTime expiresAt
) {
} 