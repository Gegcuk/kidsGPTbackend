package uk.gegc.kidsgptbackend.features.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Complete password reset with token issued via email")
public record ResetPasswordRequest(
        @Schema(description = "Reset token from email link")
        @NotBlank(message = "Token must not be blank")
        String token,

        @Schema(description = "New password")
        @NotBlank(message = "Password must not be blank")
        @Size(min = 8, max = 100, message = "Password length must be at least 8 characters")
        String newPassword
) {
}
