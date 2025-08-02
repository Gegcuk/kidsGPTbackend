package uk.gegc.kidsgptbackend.dto.user;

import uk.gegc.kidsgptbackend.model.user.AgeGroup;

import java.util.UUID;

public record ChildProfileDto(
    UUID id,
    String name,
    int age,
    String interests,
    String avatarId,
    AgeGroup ageGroup
) {} 