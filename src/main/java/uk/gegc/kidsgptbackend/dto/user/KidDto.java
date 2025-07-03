package uk.gegc.kidsgptbackend.dto.user;

import uk.gegc.kidsgptbackend.model.user.AgeGroup;

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