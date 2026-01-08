package uk.gegc.kidsgptbackend.features.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;

import java.util.UUID;

@Schema(description = "Kid profile details")
public record KidDto(
    @Schema(description = "Kid identifier")
    UUID id,
    @Schema(description = "Kid nickname")
    String nickname,
    @Schema(description = "Login username for the kid account")
    String username,
    @Schema(description = "Age group bucket")
    AgeGroup ageGroup,
    @Schema(description = "Favorite color if provided")
    String favoriteColor,
    @Schema(description = "Avatar asset ID")
    String avatarId,
    @Schema(description = "Interests/bio")
    String interests
) {}
