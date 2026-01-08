package uk.gegc.kidsgptbackend.features.consent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod;

import java.util.List;

@Schema(description = "Age thresholds and verification requirements per region")
public record ComplianceAgeThresholdResponse(
    @Schema(description = "Country code (ISO 3166)")
    String country,
    @Schema(description = "Region or state code")
    String region,
    @Schema(description = "Age considered a minor")
    Integer minorThreshold,
    @Schema(description = "Recommended retention years")
    Integer retentionYears,
    @Schema(description = "Whether teens need opt-in")
    boolean teenOptIn,
    @Schema(description = "Allowed verification methods")
    List<VerificationMethod> allowedMethods,
    @Schema(description = "Notes or references")
    String notes
) {}
