package uk.gegc.kidsgptbackend.dto.user;

import java.util.UUID;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;

public record ChildProfileDto(
    UUID id,
    String name,
    int age,
    String interests,
    String avatarId,
    AgeGroup ageGroup
) {} 