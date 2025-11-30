package uk.gegc.kidsgptbackend.features.auth.api.dto;

public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        long accessExpiresInMs,
        long refreshExpiresInMs
) {
}
