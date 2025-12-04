package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Filter parameters to export consent records")
public record ConsentExportRequest(
    @Schema(description = "Reason to log for the export")
    @NotBlank(message = "Audit reason is required")
    String auditReason,
    
    @Schema(description = "Start date for filtering")
    LocalDateTime fromDate,
    @Schema(description = "End date for filtering")
    LocalDateTime toDate,
    @Schema(description = "Consent types to include")
    List<ConsentType> consentTypes,
    @Schema(description = "Consent statuses to include")
    List<ConsentStatus> consentStatuses,
    @Schema(description = "Filter by jurisdiction")
    String jurisdiction,
    @Schema(description = "Filter by region")
    String region,
    @Schema(description = "Filter by user ID")
    String userId,
    @Schema(description = "Desired format: CSV/JSON/XML")
    String format // CSV, JSON, XML
) {}
