package uk.gegc.kidsgptbackend.features.user.api.dto;


import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserDto(
        UUID id,

        String username,

        String email,

        boolean isActive,

        Set<RoleName> roles,

        Instant createdAt,

        Instant lastLoginDate,

        Instant updatedAt
) {
}
