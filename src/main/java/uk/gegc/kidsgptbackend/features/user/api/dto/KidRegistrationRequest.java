package uk.gegc.kidsgptbackend.features.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;

@Schema(description = "Request to register a kid profile under a parent account")
public record KidRegistrationRequest(
    @Schema(description = "Kid nickname")
    @NotBlank(message = "Nickname must not be blank")
    @Size(min = 2, max = 50, message = "Nickname must be between 2 and 50 characters")
    String nickname,
    
    @Schema(description = "Kid password for login")
    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    String password,
    
    @Schema(description = "Age group for safety/limits")
    @NotNull(message = "Age group must be specified")
    AgeGroup ageGroup
) {}
