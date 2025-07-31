package uk.gegc.kidsgptbackend.dto.consent;

import uk.gegc.kidsgptbackend.model.consent.VerificationMethod;

import java.util.List;

public record ComplianceAgeThresholdResponse(
    String country,
    String region,
    Integer minorThreshold,
    Integer retentionYears,
    boolean teenOptIn,
    List<VerificationMethod> allowedMethods,
    String notes
) {} 