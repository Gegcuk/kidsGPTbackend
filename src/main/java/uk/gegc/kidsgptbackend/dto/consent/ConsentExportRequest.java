package uk.gegc.kidsgptbackend.dto.consent;

import uk.gegc.kidsgptbackend.model.consent.ConsentType;
import uk.gegc.kidsgptbackend.model.consent.ConsentStatus;

import jakarta.validation.constraints.NotBlank;
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