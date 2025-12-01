package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.constraints.NotBlank;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;

import java.time.LocalDateTime;
import java.util.List;

public record ConsentExportRequest(
    @NotBlank(message = "Audit reason is required")
    String auditReason,
    
    LocalDateTime fromDate,
    LocalDateTime toDate,
    List<ConsentType> consentTypes,
    List<ConsentStatus> consentStatuses,
    String jurisdiction,
    String region,
    String userId,
    String format // CSV, JSON, XML
) {} 