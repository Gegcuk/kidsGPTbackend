package uk.gegc.kidsgptbackend.features.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to update the parent password")
public record UpdatePasswordRequest(
        @Schema(description = "Current password")
        @NotBlank(message = "Current password must not be blank")
        String currentPassword,
        
        @Schema(description = "New password")
        @NotBlank(message = "New password must not be blank")
        @Size(min = 8, max = 100, message = "New password length must be at least 8 characters")
        String newPassword
) {
}
