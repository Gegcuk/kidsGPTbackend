package uk.gegc.kidsgptbackend.features.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Parent account details")
public record ParentDto(
    @Schema(description = "Parent identifier")
    UUID id,
    @Schema(description = "Parent first name")
    String firstName,
    @Schema(description = "Parent last name")
    String lastName,
    @Schema(description = "Email address")
    String email,
    @Schema(description = "Optional phone number")
    String phoneNumber
) {}
