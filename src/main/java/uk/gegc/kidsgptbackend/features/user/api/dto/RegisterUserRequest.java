package uk.gegc.kidsgptbackend.features.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Parent registration payload")
public record RegisterUserRequest(
        @Schema(description = "Desired username")
        @NotBlank(message = "Username must not be blank")
        @Size(min = 4, max = 20, message = "Username must be between 4 and 20 characters")
        String username,

        @Schema(description = "Parent email")
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid address")
        String email,

        @Schema(description = "Account password")
        @NotBlank(message = "Password must not be blank")
        @Size(min = 8, max = 100, message = "Password length must be at least 8 characters")
        String password
) {
}
