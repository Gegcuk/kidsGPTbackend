package uk.gegc.kidsgptbackend.dto.consent;

import uk.gegc.kidsgptbackend.model.consent.ConsentType;
import uk.gegc.kidsgptbackend.model.consent.ConsentStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ConsentStatusResponse(
    List<ConsentStatusByType> latestByType,
    boolean reconsentNeeded
) {
    public record ConsentStatusByType(
        ConsentType type,
        String version,
        ConsentStatus status,
        LocalDateTime timestamp,
        String policyUrl
    ) {}
} 