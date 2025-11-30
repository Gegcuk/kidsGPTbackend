package uk.gegc.kidsgptbackend.features.user.api.dto;

import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;

import java.util.UUID;

public record ChildProfileDto(
    UUID id,
    String name,
    int age,
    String interests,
    String avatarId,
    AgeGroup ageGroup
) {} 