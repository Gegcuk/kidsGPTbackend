package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to cleanup expired consent records")
public record ConsentCleanupRequest(
    @Schema(description = "Reason to log for the cleanup")
    @NotBlank(message = "Audit reason is required")
    String auditReason,
    
    @Schema(description = "When true, performs a dry run without deletion")
    @NotNull(message = "Dry run flag is required")
    Boolean dryRun,
    
    @Schema(description = "Optional batch size for processing")
    Integer batchSize,
    @Schema(description = "Filter by jurisdiction")
    String jurisdiction,
    @Schema(description = "Filter by region")
    String region
) {}
