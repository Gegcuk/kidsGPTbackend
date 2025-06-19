package uk.gegc.kidsgptbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank(message = "Username or email must not be blank")
        String usernameOrEmail,
        @NotBlank(message = "Password must not be blank")
        String password
) {}
