package uk.gegc.kidsgptbackend.dto.consent;

import uk.gegc.kidsgptbackend.model.consent.ConsentType;
import uk.gegc.kidsgptbackend.model.consent.LawfulBasis;
import uk.gegc.kidsgptbackend.model.consent.ConsentSource;

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