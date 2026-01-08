package uk.gegc.kidsgptbackend.features.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credentials to authenticate a user")
public record AuthLoginRequest(
        @Schema(description = "Username or email")
        @NotBlank(message = "Username or email must not be blank")
        String usernameOrEmail,
        @Schema(description = "User password")
        @NotBlank(message = "Password must not be blank")
        String password
) {
}
