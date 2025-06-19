package uk.gegc.kidsgptbackend.dto.user;

import uk.gegc.kidsgptbackend.model.user.RoleName;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String username,
        RoleName role,
        LocalDateTime createdAt
) {
}
