package uk.gegc.kidsgptbackend.dto.user;


import uk.gegc.kidsgptbackend.model.user.RoleName;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record UserDto(
        UUID id,

        String username,

        String email,

        boolean isActive,

        Set<RoleName> roles,

        LocalDateTime createdAt,

        LocalDateTime lastLoginDate,

        LocalDateTime updatedAt
) {
}
