package uk.gegc.kidsgptbackend.dto.auth;

public record AuthTokensResponse(
        String accessToken,
        String refreshToken
) {}
