package uk.gegc.kidsgptbackend.features.consent.api.dto;

import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis;

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
        List<String> coveredKids,
        String withdrawnConsentId
    ) {}
    
    /**
     * Paginated response wrapper that includes paging metadata
     */
    public record PaginatedConsentHistoryResponse(
        String userId,
        List<ConsentHistoryEntry> entries,
        int page,
        int size,
        long total,
        int totalPages,
        boolean hasNext,
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