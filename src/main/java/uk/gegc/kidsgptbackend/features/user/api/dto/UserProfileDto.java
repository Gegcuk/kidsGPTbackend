package uk.gegc.kidsgptbackend.features.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Minimal profile information for the current user")
public record UserProfileDto(
        @Schema(description = "User identifier")
        UUID id,
        @Schema(description = "Username")
        String username,
        @Schema(description = "Email")
        String email,
        @Schema(description = "Primary role")
        RoleName role,
        @Schema(description = "Creation timestamp")
        Instant createdAt
) {
}
