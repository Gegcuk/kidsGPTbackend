package uk.gegc.kidsgptbackend.features.user.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Child profile update payload")
public record ChildProfileUpdateRequest(
    @Schema(description = "Display name")
    @NotBlank(message = "Name must not be blank")
    @Size(max = 50, message = "Name must be at most 50 characters")
    String name,

    @Schema(description = "Age in years")
    @Min(value = 3, message = "Age must be at least 3")
    @Max(value = 16, message = "Age must be at most 16")
    int age,

    @Schema(description = "Optional interests/bio")
    String interests,
    @Schema(description = "Avatar asset ID")
    String avatarId
) {}
