package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsentCleanupRequest(
    @NotBlank(message = "Audit reason is required")
    String auditReason,
    
    @NotNull(message = "Dry run flag is required")
    Boolean dryRun,
    
    Integer batchSize,
    String jurisdiction,
    String region
) {} 