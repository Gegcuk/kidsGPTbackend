package uk.gegc.kidsgptbackend.dto.user;

import uk.gegc.kidsgptbackend.model.user.RoleName;

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
