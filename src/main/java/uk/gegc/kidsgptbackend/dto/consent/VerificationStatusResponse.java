package uk.gegc.kidsgptbackend.dto.consent;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.model.consent.VerificationMethod;
import uk.gegc.kidsgptbackend.model.consent.VerificationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Verification status response")
public record VerificationStatusResponse(
    @Schema(format = "uuid") UUID verificationId,
    @Schema(format = "uuid") UUID parentId,
    VerificationMethod verificationMethod,
    VerificationStatus verificationStatus,
    Integer attemptCount,
    @Schema(format = "date-time") OffsetDateTime expiresAt,
    @Schema(format = "date-time") OffsetDateTime verifiedAt,
    @Schema(format = "date-time") OffsetDateTime createdAt
) {} 