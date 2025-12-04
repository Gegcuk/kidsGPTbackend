package uk.gegc.kidsgptbackend.features.consent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Result of a consent cleanup run")
public record ConsentCleanupResponse(
    @Schema(description = "Whether the run was a dry run")
    boolean dryRun,
    @Schema(description = "Timestamp when cleanup executed")
    LocalDateTime cleanupTimestamp,
    @Schema(description = "Total records processed")
    int recordsProcessed,
    @Schema(description = "Records deleted")
    int recordsDeleted,
    @Schema(description = "Records archived")
    int recordsArchived,
    @Schema(description = "Errors encountered, if any")
    List<String> errors,
    @Schema(description = "Human-readable summary")
    String summary
) {}
