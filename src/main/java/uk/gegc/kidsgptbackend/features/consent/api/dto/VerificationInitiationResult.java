package uk.gegc.kidsgptbackend.features.consent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of initiating verification, including whether it was newly created")
public record VerificationInitiationResult(
    @Schema(description = "Verification status payload")
    VerificationStatusResponse verificationStatus,
    @Schema(description = "True if a new verification was created, false if an existing pending one was reused")
    boolean newlyCreated
) {} 
