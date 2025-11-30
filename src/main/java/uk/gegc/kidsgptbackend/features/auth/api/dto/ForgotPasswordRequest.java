package uk.gegc.kidsgptbackend.features.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid address")
        String email
) {
} 