package uk.gegc.kidsgptbackend.features.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;

@Schema(description = "Parent-initiated updates to a kid profile")
public record ParentUpdateKidRequest(
    @Schema(description = "Kid nickname")
    @NotBlank(message = "Nickname must not be blank")
    @Size(min = 2, max = 50, message = "Nickname must be between 2 and 50 characters")
    String nickname,
    
    @Schema(description = "Optional new kid password")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    String password,  // Optional - only update if provided
    
    @Schema(description = "Age group for safety/limits")
    @NotNull(message = "Age group must be specified")
    AgeGroup ageGroup
) {}
