package uk.gegc.kidsgptbackend.features.consent.api.dto;

import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConsentStatusResponse(
    List<ConsentStatusByType> latestByType,
    boolean reconsentNeeded,
    UUID consentId
) {
    public record ConsentStatusByType(
        ConsentType type,
        String version,
        ConsentStatus status,
        LocalDateTime timestamp,
        String policyUrl
    ) {}
} 