package uk.gegc.kidsgptbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePasswordRequest(
        @NotBlank(message = "Current password must not be blank")
        String currentPassword,
        
        @NotBlank(message = "New password must not be blank")
        @Size(min = 8, max = 100, message = "New password length must be at least 8 characters")
        String newPassword
) {
} 