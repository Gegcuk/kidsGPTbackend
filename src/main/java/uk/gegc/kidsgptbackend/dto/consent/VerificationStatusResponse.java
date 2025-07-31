package uk.gegc.kidsgptbackend.dto.consent;

import uk.gegc.kidsgptbackend.model.consent.VerificationMethod;
import uk.gegc.kidsgptbackend.model.consent.VerificationStatus;

import java.time.LocalDateTime;

public record VerificationStatusResponse(
    String verificationId,
    String parentId,
    VerificationMethod verificationMethod,
    VerificationStatus verificationStatus,
    Integer attemptCount,
    LocalDateTime expiresAt,
    LocalDateTime verifiedAt,
    LocalDateTime createdAt,
    String ipAddress,
    String userAgent
) {} 