package uk.gegc.kidsgptbackend.dto.consent;

public record VerificationInitiationResult(
    VerificationStatusResponse verificationStatus,
    boolean newlyCreated
) {} 