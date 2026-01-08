package uk.gegc.kidsgptbackend.features.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;

import java.util.UUID;

@Schema(description = "Kid profile data visible to parent and child")
public record ChildProfileDto(
    @Schema(description = "Kid identifier")
    UUID id,
    @Schema(description = "Kid name")
    String name,
    @Schema(description = "Kid age")
    int age,
    @Schema(description = "Interests/bio")
    String interests,
    @Schema(description = "Avatar asset ID")
    String avatarId,
    @Schema(description = "Age group bucket")
    AgeGroup ageGroup
) {}
