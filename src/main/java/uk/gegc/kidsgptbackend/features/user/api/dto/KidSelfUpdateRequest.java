package uk.gegc.kidsgptbackend.features.user.api.dto;

public record KidSelfUpdateRequest(
    String avatarId  // Optional - kids can only update their avatar
) {} 