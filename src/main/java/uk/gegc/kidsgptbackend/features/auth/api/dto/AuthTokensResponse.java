package uk.gegc.kidsgptbackend.features.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT token pair issued after authentication")
public record AuthTokensResponse(
        @Schema(description = "Bearer access token")
        String accessToken,
        @Schema(description = "Refresh token")
        String refreshToken,
        @Schema(description = "Access token expiry in milliseconds")
        long accessExpiresInMs,
        @Schema(description = "Refresh token expiry in milliseconds")
        long refreshExpiresInMs
) {
}
