package uk.gegc.kidsgptbackend.features.consent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Consent history for a user")
public record ConsentHistoryResponse(
    @Schema(description = "User ID whose consent history is returned")
    String userId,
    @Schema(description = "Consent history entries")
    List<ConsentHistoryEntry> entries
) {
    @Schema(description = "Single consent history record")
    public record ConsentHistoryEntry(
        @Schema(description = "Consent record ID")
        String consentId,
        @Schema(description = "Type of consent")
        ConsentType consentType,
        @Schema(description = "Consent document version")
        String consentVersion,
        @Schema(description = "Status of consent")
        ConsentStatus consentStatus,
        @Schema(description = "Policy URL")
        String policyUrl,
        @Schema(description = "Hash of consent content")
        String contentHash,
        @Schema(description = "Jurisdiction (e.g., US/EU)")
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
        @Schema(description = "User agent")
        String userAgent,
        @Schema(description = "Timestamp consent was provided")
        LocalDateTime consentTimestamp,
        @Schema(description = "Verification ID if present")
        String parentVerificationId,
        @Schema(description = "Data retention expiry")
        LocalDateTime retentionExpiresAt,
        @Schema(description = "Record creation timestamp")
        LocalDateTime createdAt,
        @Schema(description = "Kids covered by this consent")
        List<String> coveredKids,
        @Schema(description = "Linked withdrawn consent ID, if applicable")
        String withdrawnConsentId
    ) {}
    
    /**
     * Paginated response wrapper that includes paging metadata
     */
    @Schema(description = "Paginated consent history response")
    public record PaginatedConsentHistoryResponse(
        @Schema(description = "User ID whose consent history is returned")
        String userId,
        @Schema(description = "Consent history entries")
        List<ConsentHistoryEntry> entries,
        @Schema(description = "Current page number (0-based)")
        int page,
        @Schema(description = "Page size")
        int size,
        @Schema(description = "Total number of records")
        long total,
        @Schema(description = "Total number of pages")
        int totalPages,
        @Schema(description = "Whether there is a next page")
        boolean hasNext,
        @Schema(description = "Whether there is a previous page")
        boolean hasPrevious
    ) {
        public static PaginatedConsentHistoryResponse from(ConsentHistoryResponse response, int page, int size, long total) {
            int totalPages = (int) Math.ceil((double) total / size);
            return new PaginatedConsentHistoryResponse(
                response.userId(),
                response.entries(),
                page,
                size,
                total,
                totalPages,
                page < totalPages - 1,
                page > 0
            );
        }
    }
} 
