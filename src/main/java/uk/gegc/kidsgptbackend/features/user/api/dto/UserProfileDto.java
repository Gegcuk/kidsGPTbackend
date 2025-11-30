package uk.gegc.kidsgptbackend.features.user.api.dto;

import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;

import java.time.Instant;
import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String username,
        String email,
        RoleName role,
        Instant createdAt
) {
}
