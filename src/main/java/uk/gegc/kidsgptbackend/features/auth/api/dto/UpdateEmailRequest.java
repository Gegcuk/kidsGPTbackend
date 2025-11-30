package uk.gegc.kidsgptbackend.features.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateEmailRequest(
        @NotBlank(message = "New email must not be blank")
        @Email(message = "New email must be a valid address")
        String newEmail
) {
} 