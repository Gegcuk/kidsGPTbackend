package uk.gegc.kidsgptbackend.dto.consent;

import uk.gegc.kidsgptbackend.model.consent.ConsentSource;
import uk.gegc.kidsgptbackend.model.consent.ConsentStatus;
import uk.gegc.kidsgptbackend.model.consent.ConsentType;
import uk.gegc.kidsgptbackend.model.consent.LawfulBasis;

import java.time.LocalDateTime;
import java.util.List;

public record ConsentHistoryResponse(
    String userId,
    List<ConsentHistoryEntry> entries
) {
    public record ConsentHistoryEntry(
        String consentId,
        ConsentType consentType,
        String consentVersion,
        ConsentStatus consentStatus,
        String policyUrl,
        String contentHash,
        String jurisdiction,
        String region,
        String locale,
        LawfulBasis lawfulBasis,
        ConsentSource source,
        String ipAddress,
        String userAgent,
        LocalDateTime consentTimestamp,
        String parentVerificationId,
        LocalDateTime retentionExpiresAt,
        LocalDateTime createdAt,
        List<String> coveredKids
    ) {}
} 