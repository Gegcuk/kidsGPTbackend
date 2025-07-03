package uk.gegc.kidsgptbackend.dto.user;

public record KidSelfUpdateRequest(
    String avatarId  // Optional - kids can only update their avatar
) {} 