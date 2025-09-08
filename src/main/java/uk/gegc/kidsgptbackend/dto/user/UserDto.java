package uk.gegc.kidsgptbackend.dto.user;


import uk.gegc.kidsgptbackend.model.user.RoleName;

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
