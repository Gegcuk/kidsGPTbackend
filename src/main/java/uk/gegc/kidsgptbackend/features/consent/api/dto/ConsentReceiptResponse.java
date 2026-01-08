package uk.gegc.kidsgptbackend.features.consent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Receipt/details for a granted consent")
public record ConsentReceiptResponse(
    @Schema(description = "Consent ID")
    String consentId,
    @Schema(description = "User ID")
    String userId,
    @Schema(description = "Consent type")
    ConsentType consentType,
    @Schema(description = "Consent version")
    String consentVersion,
    @Schema(description = "Policy URL")
    String policyUrl,
    @Schema(description = "Content hash of consent doc")
    String contentHash,
    @Schema(description = "Jurisdiction")
    String jurisdiction,
    @Schema(description = "Region/subdivision")
    String region,
    @Schema(description = "Locale of document")
    String locale,
    @Schema(description = "Lawful basis for processing")
    LawfulBasis lawfulBasis,
    @Schema(description = "Source of consent")
    ConsentSource source,
    @Schema(description = "IP address used")
    String ipAddress,
    @Schema(description = "User agent string")
    String userAgent,
    @Schema(description = "When consent was provided")
    LocalDateTime consentTimestamp,
    @Schema(description = "Verification request ID")
    String parentVerificationId,
    @Schema(description = "Data retention expiry")
    LocalDateTime retentionExpiresAt,
    @Schema(description = "Record creation timestamp")
    LocalDateTime createdAt,
    @Schema(description = "Covered kids")
    List<String> coveredKids,
    @Schema(description = "Receipt serialized as JSON")
    String receiptJson,
    @Schema(description = "Signature blob for the record")
    byte[] recordSignature
) {}
