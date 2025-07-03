package uk.gegc.kidsgptbackend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;

public record KidRegistrationRequest(
    @NotBlank(message = "Nickname must not be blank")
    @Size(min = 2, max = 50, message = "Nickname must be between 2 and 50 characters")
    String nickname,
    
    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    String password,
    
    @NotNull(message = "Age group must be specified")
    AgeGroup ageGroup
) {} 