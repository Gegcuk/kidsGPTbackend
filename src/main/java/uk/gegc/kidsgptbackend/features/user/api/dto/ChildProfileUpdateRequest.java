package uk.gegc.kidsgptbackend.features.user.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChildProfileUpdateRequest(
    @NotBlank(message = "Name must not be blank")
    @Size(max = 50, message = "Name must be at most 50 characters")
    String name,

    @Min(value = 3, message = "Age must be at least 3")
    @Max(value = 16, message = "Age must be at most 16")
    int age,

    String interests,
    String avatarId
) {} 