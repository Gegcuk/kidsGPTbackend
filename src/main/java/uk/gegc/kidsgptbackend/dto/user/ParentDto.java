package uk.gegc.kidsgptbackend.dto.user;

import java.util.UUID;

public record ParentDto(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String phoneNumber
) {} 