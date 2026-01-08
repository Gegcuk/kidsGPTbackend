package uk.gegc.kidsgptbackend.features.user.api.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "User account details")
public record UserDto(
        @Schema(description = "User identifier")
        UUID id,

        @Schema(description = "Username")
        String username,

        @Schema(description = "Email address")
        String email,

        @Schema(description = "Whether the account is active")
        boolean isActive,

        @Schema(description = "Assigned roles")
        Set<RoleName> roles,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last login timestamp")
        Instant lastLoginDate,

        @Schema(description = "Last update timestamp")
        Instant updatedAt
) {
}
