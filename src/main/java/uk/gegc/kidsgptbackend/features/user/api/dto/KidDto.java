package uk.gegc.kidsgptbackend.features.user.api.dto;

import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;

import java.util.UUID;

public record KidDto(
    UUID id,
    String nickname,
    String username,
    AgeGroup ageGroup,
    String favoriteColor,
    String avatarId,
    String interests
) {} 