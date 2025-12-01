package uk.gegc.kidsgptbackend.features.consent.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ConsentCleanupResponse(
    boolean dryRun,
    LocalDateTime cleanupTimestamp,
    int recordsProcessed,
    int recordsDeleted,
    int recordsArchived,
    List<String> errors,
    String summary
) {} 