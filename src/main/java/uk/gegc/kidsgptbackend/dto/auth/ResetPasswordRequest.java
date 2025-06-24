package uk.gegc.kidsgptbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token must not be blank")
        String token,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 8, max = 100, message = "Password length must be at least 8 characters")
        String newPassword
) {
} 