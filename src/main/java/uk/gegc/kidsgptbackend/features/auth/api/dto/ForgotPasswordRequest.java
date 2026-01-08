package uk.gegc.kidsgptbackend.features.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to initiate password reset")
public record ForgotPasswordRequest(
        @Schema(description = "Account email")
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid address")
        String email
) {
}
