package uk.gegc.kidsgptbackend.features.consent.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentHistoryResponse DTO Tests")
class ConsentHistoryResponseTest extends BaseUnitTest {

    @Test
    @DisplayName("should create response with entries")
    void createResponse_withEntries_valid() {
        // Given
        String userId = "user123";
        LocalDateTime now = LocalDateTime.now();
        
        ConsentHistoryResponse.ConsentHistoryEntry entry = new ConsentHistoryResponse.ConsentHistoryEntry(
                UUID.randomUUID().toString(),
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                "https://example.com/policy",
                "abc123",
                "UK",
                "London",
                "en-GB",
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                "192.168.1.1",
                "Mozilla/5.0",
                now,
                UUID.randomUUID().toString(),
                now.plusYears(7),
                now,
                List.of("kid1", "kid2"),
                null
        );

        // When
        ConsentHistoryResponse response = new ConsentHistoryResponse(
                userId,
                List.of(entry)
        );

        // Then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.entries()).hasSize(1);
        assertThat(response.entries().get(0)).isEqualTo(entry);
    }

    @Test
    @DisplayName("should create response with empty entries")
    void createResponse_emptyEntries_valid() {
        // Given
        String userId = "user123";

        // When
        ConsentHistoryResponse response = new ConsentHistoryResponse(
                userId,
                List.of()
        );

        // Then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.entries()).isEmpty();
    }

    @Test
    @DisplayName("should create response with null entries")
    void createResponse_nullEntries_valid() {
        // Given
        String userId = "user123";

        // When
        ConsentHistoryResponse response = new ConsentHistoryResponse(
                userId,
                null
        );

        // Then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.entries()).isNull();
    }

    @Test
    @DisplayName("should test nested record ConsentHistoryEntry")
    void nestedRecord_ConsentHistoryEntry_valid() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        String consentId = UUID.randomUUID().toString();
        String verificationId = UUID.randomUUID().toString();
        
        ConsentHistoryResponse.ConsentHistoryEntry entry = new ConsentHistoryResponse.ConsentHistoryEntry(
                consentId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                "https://example.com/policy",
                "abc123",
                "UK",
                "London",
                "en-GB",
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                "192.168.1.1",
                "Mozilla/5.0",
                now,
                verificationId,
                now.plusYears(7),
                now,
                List.of("kid1"),
                null
        );

        // Then
        assertThat(entry.consentId()).isEqualTo(consentId);
        assertThat(entry.consentType()).isEqualTo(ConsentType.PRIVACY_POLICY);
        assertThat(entry.consentVersion()).isEqualTo("1.0");
        assertThat(entry.consentStatus()).isEqualTo(ConsentStatus.GRANTED);
        assertThat(entry.policyUrl()).isEqualTo("https://example.com/policy");
        assertThat(entry.contentHash()).isEqualTo("abc123");
        assertThat(entry.jurisdiction()).isEqualTo("UK");
        assertThat(entry.region()).isEqualTo("London");
        assertThat(entry.locale()).isEqualTo("en-GB");
        assertThat(entry.lawfulBasis()).isEqualTo(LawfulBasis.CONSENT);
        assertThat(entry.source()).isEqualTo(ConsentSource.WEB);
        assertThat(entry.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(entry.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(entry.consentTimestamp()).isEqualTo(now);
        assertThat(entry.parentVerificationId()).isEqualTo(verificationId);
        assertThat(entry.retentionExpiresAt()).isEqualTo(now.plusYears(7));
        assertThat(entry.createdAt()).isEqualTo(now);
        assertThat(entry.coveredKids()).containsExactly("kid1");
        assertThat(entry.withdrawnConsentId()).isNull();
    }

    @Test
    @DisplayName("should test nested record equality")
    void nestedRecord_equality_valid() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        String consentId = UUID.randomUUID().toString();
        
        ConsentHistoryResponse.ConsentHistoryEntry entry1 = new ConsentHistoryResponse.ConsentHistoryEntry(
                consentId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                "https://example.com/policy",
                "abc123",
                "UK",
                null,
                null,
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                null,
                null,
                now,
                null,
                null,
                now,
                null,
                null
        );
        
        ConsentHistoryResponse.ConsentHistoryEntry entry2 = new ConsentHistoryResponse.ConsentHistoryEntry(
                consentId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                "https://example.com/policy",
                "abc123",
                "UK",
                null,
                null,
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                null,
                null,
                now,
                null,
                null,
                now,
                null,
                null
        );

        // Then
        assertThat(entry1).isEqualTo(entry2);
        assertThat(entry1.hashCode()).isEqualTo(entry2.hashCode());
    }

    @Test
    @DisplayName("should test PaginatedConsentHistoryResponse from method")
    void paginatedResponse_fromMethod_valid() {
        // Given
        String userId = "user123";
        LocalDateTime now = LocalDateTime.now();
        
        ConsentHistoryResponse.ConsentHistoryEntry entry = new ConsentHistoryResponse.ConsentHistoryEntry(
                UUID.randomUUID().toString(),
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                "https://example.com/policy",
                "abc123",
                "UK",
                null,
                null,
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                null,
                null,
                now,
                null,
                null,
                now,
                null,
                null
        );

        ConsentHistoryResponse response = new ConsentHistoryResponse(
                userId,
                List.of(entry)
        );

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse paginated = 
                ConsentHistoryResponse.PaginatedConsentHistoryResponse.from(response, 0, 10, 25);

        // Then
        assertThat(paginated.userId()).isEqualTo(userId);
        assertThat(paginated.entries()).hasSize(1);
        assertThat(paginated.page()).isEqualTo(0);
        assertThat(paginated.size()).isEqualTo(10);
        assertThat(paginated.total()).isEqualTo(25);
        assertThat(paginated.totalPages()).isEqualTo(3); // ceil(25/10) = 3
        assertThat(paginated.hasNext()).isTrue(); // page 0 < totalPages-1 (2)
        assertThat(paginated.hasPrevious()).isFalse(); // page 0
    }

    @Test
    @DisplayName("should test PaginatedConsentHistoryResponse hasNext and hasPrevious")
    void paginatedResponse_paginationFlags_valid() {
        // Given
        String userId = "user123";
        ConsentHistoryResponse response = new ConsentHistoryResponse(userId, List.of());

        // Test first page
        ConsentHistoryResponse.PaginatedConsentHistoryResponse firstPage = 
                ConsentHistoryResponse.PaginatedConsentHistoryResponse.from(response, 0, 10, 30);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.hasPrevious()).isFalse();

        // Test middle page
        ConsentHistoryResponse.PaginatedConsentHistoryResponse middlePage = 
                ConsentHistoryResponse.PaginatedConsentHistoryResponse.from(response, 1, 10, 30);
        assertThat(middlePage.hasNext()).isTrue();
        assertThat(middlePage.hasPrevious()).isTrue();

        // Test last page
        ConsentHistoryResponse.PaginatedConsentHistoryResponse lastPage = 
                ConsentHistoryResponse.PaginatedConsentHistoryResponse.from(response, 2, 10, 30);
        assertThat(lastPage.hasNext()).isFalse(); // page 2 is not < totalPages-1 (2)
        assertThat(lastPage.hasPrevious()).isTrue();
    }

    @Test
    @DisplayName("should test PaginatedConsentHistoryResponse totalPages calculation")
    void paginatedResponse_totalPagesCalculation_valid() {
        // Given
        String userId = "user123";
        ConsentHistoryResponse response = new ConsentHistoryResponse(userId, List.of());

        // Test exact division
        ConsentHistoryResponse.PaginatedConsentHistoryResponse exact = 
                ConsentHistoryResponse.PaginatedConsentHistoryResponse.from(response, 0, 10, 30);
        assertThat(exact.totalPages()).isEqualTo(3);

        // Test with remainder
        ConsentHistoryResponse.PaginatedConsentHistoryResponse withRemainder = 
                ConsentHistoryResponse.PaginatedConsentHistoryResponse.from(response, 0, 10, 25);
        assertThat(withRemainder.totalPages()).isEqualTo(3); // ceil(25/10) = 3

        // Test single page
        ConsentHistoryResponse.PaginatedConsentHistoryResponse singlePage = 
                ConsentHistoryResponse.PaginatedConsentHistoryResponse.from(response, 0, 10, 5);
        assertThat(singlePage.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        String userId = "user123";
        LocalDateTime now = LocalDateTime.now();
        
        ConsentHistoryResponse.ConsentHistoryEntry entry = new ConsentHistoryResponse.ConsentHistoryEntry(
                UUID.randomUUID().toString(),
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                "https://example.com/policy",
                "abc123",
                "UK",
                null,
                null,
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                null,
                null,
                now,
                null,
                null,
                now,
                null,
                null
        );

        ConsentHistoryResponse response1 = new ConsentHistoryResponse(
                userId,
                List.of(entry)
        );

        ConsentHistoryResponse response2 = new ConsentHistoryResponse(
                userId,
                List.of(entry)
        );

        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        String userId = "user123";
        ConsentHistoryResponse response = new ConsentHistoryResponse(userId, List.of());

        // When
        String toString = response.toString();

        // Then
        assertThat(toString).contains("ConsentHistoryResponse");
        assertThat(toString).contains(userId);
    }
}

