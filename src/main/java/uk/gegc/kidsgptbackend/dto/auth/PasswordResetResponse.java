package uk.gegc.kidsgptbackend.dto.auth;

import java.time.LocalDateTime;

public record PasswordResetResponse(
        String message,
        LocalDateTime expiresAt
) {
} 