package uk.gegc.kidsgptbackend.features.consent.api.dto;

import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis;

import java.time.LocalDateTime;
import java.util.List;

public record ConsentReceiptResponse(
    String consentId,
    String userId,
    ConsentType consentType,
    String consentVersion,
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
    List<String> coveredKids,
    String receiptJson,
    byte[] recordSignature
) {} 