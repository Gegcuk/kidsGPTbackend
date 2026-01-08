package uk.gegc.kidsgptbackend.features.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to update the parent email")
public record UpdateEmailRequest(
        @Schema(description = "New email address")
        @NotBlank(message = "New email must not be blank")
        @Email(message = "New email must be a valid address")
        String newEmail
) {
}
