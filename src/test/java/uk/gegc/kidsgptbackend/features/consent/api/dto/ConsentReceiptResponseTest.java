package uk.gegc.kidsgptbackend.features.consent.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentReceiptResponse DTO Tests")
class ConsentReceiptResponseTest extends BaseUnitTest {

    @Test
    @DisplayName("should create response with all fields")
    void createResponse_allFields_valid() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        LocalDateTime retentionExpires = timestamp.plusYears(7);
        LocalDateTime createdAt = timestamp;
        byte[] signature = new byte[]{1, 2, 3, 4};
        
        ConsentReceiptResponse response = new ConsentReceiptResponse(
                "consent123",
                "user123",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                "UK",
                "London",
                "en-GB",
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                "192.168.1.1",
                "Mozilla/5.0",
                timestamp,
                UUID.randomUUID().toString(),
                retentionExpires,
                createdAt,
                List.of("kid1", "kid2"),
                "{\"receipt\":\"data\"}",
                signature
        );

        // Then
        assertThat(response.consentId()).isEqualTo("consent123");
        assertThat(response.userId()).isEqualTo("user123");
        assertThat(response.consentType()).isEqualTo(ConsentType.PRIVACY_POLICY);
        assertThat(response.consentVersion()).isEqualTo("1.0");
        assertThat(response.policyUrl()).isEqualTo("https://example.com/policy");
        assertThat(response.contentHash()).isEqualTo("abc123");
        assertThat(response.jurisdiction()).isEqualTo("UK");
        assertThat(response.region()).isEqualTo("London");
        assertThat(response.locale()).isEqualTo("en-GB");
        assertThat(response.lawfulBasis()).isEqualTo(LawfulBasis.CONSENT);
        assertThat(response.source()).isEqualTo(ConsentSource.WEB);
        assertThat(response.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(response.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(response.consentTimestamp()).isEqualTo(timestamp);
        assertThat(response.retentionExpiresAt()).isEqualTo(retentionExpires);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.coveredKids()).containsExactly("kid1", "kid2");
        assertThat(response.receiptJson()).isEqualTo("{\"receipt\":\"data\"}");
        assertThat(response.recordSignature()).isEqualTo(signature);
    }

    @Test
    @DisplayName("should create response with null optional fields")
    void createResponse_nullOptionalFields_valid() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        
        ConsentReceiptResponse response = new ConsentReceiptResponse(
                "consent123",
                "user123",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                null, // jurisdiction is optional
                null, // region is optional
                null, // locale is optional
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                null, // ipAddress is optional
                null, // userAgent is optional
                timestamp,
                null, // parentVerificationId is optional
                null, // retentionExpiresAt is optional
                null, // createdAt is optional
                null, // coveredKids is optional
                null, // receiptJson is optional
                null  // recordSignature is optional
        );

        // Then
        assertThat(response.consentId()).isEqualTo("consent123");
        assertThat(response.userId()).isEqualTo("user123");
        assertThat(response.consentType()).isEqualTo(ConsentType.PRIVACY_POLICY);
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        byte[] signature = new byte[]{1, 2, 3};
        
        ConsentReceiptResponse response1 = new ConsentReceiptResponse(
                "consent123",
                "user123",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                "UK",
                null,
                null,
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                null,
                null,
                timestamp,
                null,
                null,
                null,
                null,
                null,
                signature
        );

        ConsentReceiptResponse response2 = new ConsentReceiptResponse(
                "consent123",
                "user123",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                "UK",
                null,
                null,
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                null,
                null,
                timestamp,
                null,
                null,
                null,
                null,
                null,
                signature
        );

        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        
        ConsentReceiptResponse response = new ConsentReceiptResponse(
                "consent123",
                "user123",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                "UK",
                null,
                null,
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                null,
                null,
                timestamp,
                null,
                null,
                null,
                null,
                null,
                null
        );

        // When
        String toString = response.toString();

        // Then
        assertThat(toString).contains("ConsentReceiptResponse");
        assertThat(toString).contains("consent123");
        assertThat(toString).contains("user123");
    }
}

