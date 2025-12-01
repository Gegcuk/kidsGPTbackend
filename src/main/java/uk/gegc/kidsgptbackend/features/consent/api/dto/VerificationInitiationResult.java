package uk.gegc.kidsgptbackend.features.consent.api.dto;

public record VerificationInitiationResult(
    VerificationStatusResponse verificationStatus,
    boolean newlyCreated
) {} 