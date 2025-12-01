package uk.gegc.kidsgptbackend.features.consent.api.dto;

import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod;

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