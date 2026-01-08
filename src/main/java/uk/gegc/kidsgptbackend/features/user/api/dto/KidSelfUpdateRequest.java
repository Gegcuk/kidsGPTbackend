package uk.gegc.kidsgptbackend.features.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kid-initiated profile updates (restricted to avatar)")
public record KidSelfUpdateRequest(
    @Schema(description = "Selected avatar asset ID")
    String avatarId  // Optional - kids can only update their avatar
) {}
