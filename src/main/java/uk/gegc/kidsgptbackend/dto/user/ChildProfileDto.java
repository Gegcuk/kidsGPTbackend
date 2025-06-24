package uk.gegc.kidsgptbackend.dto.user;

import java.util.UUID;

public record ChildProfileDto(
    UUID id,
    String name,
    int age,
    String interests,
    String avatarId
) {} 