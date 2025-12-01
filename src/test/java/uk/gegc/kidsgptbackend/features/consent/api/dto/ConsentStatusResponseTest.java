package uk.gegc.kidsgptbackend.features.consent.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentStatusResponse DTO Tests")
class ConsentStatusResponseTest extends BaseUnitTest {

    @Test
    @DisplayName("should create response with all fields")
    void createResponse_allFields_valid() {
        // Given
        UUID consentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        
        ConsentStatusResponse.ConsentStatusByType statusByType = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                now,
                "https://example.com/policy"
        );

        // When
        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(statusByType),
                false,
                consentId
        );

        // Then
        assertThat(response.latestByType()).hasSize(1);
        assertThat(response.latestByType().get(0)).isEqualTo(statusByType);
        assertThat(response.reconsentNeeded()).isFalse();
        assertThat(response.consentId()).isEqualTo(consentId);
    }

    @Test
    @DisplayName("should create response with empty list")
    void createResponse_emptyList_valid() {
        // Given
        UUID consentId = UUID.randomUUID();

        // When
        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(),
                true,
                consentId
        );

        // Then
        assertThat(response.latestByType()).isEmpty();
        assertThat(response.reconsentNeeded()).isTrue();
        assertThat(response.consentId()).isEqualTo(consentId);
    }

    @Test
    @DisplayName("should create response with null list")
    void createResponse_nullList_valid() {
        // Given
        UUID consentId = UUID.randomUUID();

        // When
        ConsentStatusResponse response = new ConsentStatusResponse(
                null,
                false,
                consentId
        );

        // Then
        assertThat(response.latestByType()).isNull();
        assertThat(response.consentId()).isEqualTo(consentId);
    }

    @Test
    @DisplayName("should create response with multiple statuses")
    void createResponse_multipleStatuses_valid() {
        // Given
        UUID consentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        
        ConsentStatusResponse.ConsentStatusByType status1 = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                now,
                "https://example.com/policy1"
        );
        
        ConsentStatusResponse.ConsentStatusByType status2 = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.DATA_PROCESSING,
                "2.0",
                ConsentStatus.WITHDRAWN,
                now.plusDays(1),
                "https://example.com/policy2"
        );

        // When
        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(status1, status2),
                false,
                consentId
        );

        // Then
        assertThat(response.latestByType()).hasSize(2);
        assertThat(response.latestByType()).contains(status1, status2);
    }

    @Test
    @DisplayName("should test nested record ConsentStatusByType")
    void nestedRecord_ConsentStatusByType_valid() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        ConsentStatusResponse.ConsentStatusByType status = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                now,
                "https://example.com/policy"
        );

        // Then
        assertThat(status.type()).isEqualTo(ConsentType.PRIVACY_POLICY);
        assertThat(status.version()).isEqualTo("1.0");
        assertThat(status.status()).isEqualTo(ConsentStatus.GRANTED);
        assertThat(status.timestamp()).isEqualTo(now);
        assertThat(status.policyUrl()).isEqualTo("https://example.com/policy");
    }

    @Test
    @DisplayName("should test nested record equality")
    void nestedRecord_equality_valid() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        ConsentStatusResponse.ConsentStatusByType status1 = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                now,
                "https://example.com/policy"
        );
        
        ConsentStatusResponse.ConsentStatusByType status2 = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                now,
                "https://example.com/policy"
        );

        // Then
        assertThat(status1).isEqualTo(status2);
        assertThat(status1.hashCode()).isEqualTo(status2.hashCode());
    }

    @Test
    @DisplayName("should test nested record toString")
    void nestedRecord_toString_valid() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        ConsentStatusResponse.ConsentStatusByType status = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                now,
                "https://example.com/policy"
        );

        // When
        String toString = status.toString();

        // Then
        assertThat(toString).contains("ConsentStatusByType");
        assertThat(toString).contains("PRIVACY_POLICY");
        assertThat(toString).contains("1.0");
        assertThat(toString).contains("GRANTED");
    }

    @Test
    @DisplayName("should handle all consent types in nested record")
    void nestedRecord_allConsentTypes_valid() {
        LocalDateTime now = LocalDateTime.now();
        
        for (ConsentType type : ConsentType.values()) {
            ConsentStatusResponse.ConsentStatusByType status = new ConsentStatusResponse.ConsentStatusByType(
                    type,
                    "1.0",
                    ConsentStatus.GRANTED,
                    now,
                    "https://example.com/policy"
            );
            
            assertThat(status.type()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("should handle all consent statuses in nested record")
    void nestedRecord_allConsentStatuses_valid() {
        LocalDateTime now = LocalDateTime.now();
        
        for (ConsentStatus status : ConsentStatus.values()) {
            ConsentStatusResponse.ConsentStatusByType statusByType = new ConsentStatusResponse.ConsentStatusByType(
                    ConsentType.PRIVACY_POLICY,
                    "1.0",
                    status,
                    now,
                    "https://example.com/policy"
            );
            
            assertThat(statusByType.status()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        UUID consentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        
        ConsentStatusResponse.ConsentStatusByType status = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                now,
                "https://example.com/policy"
        );

        ConsentStatusResponse response1 = new ConsentStatusResponse(
                List.of(status),
                false,
                consentId
        );

        ConsentStatusResponse response2 = new ConsentStatusResponse(
                List.of(status),
                false,
                consentId
        );

        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        UUID consentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        
        ConsentStatusResponse.ConsentStatusByType status = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0",
                ConsentStatus.GRANTED,
                now,
                "https://example.com/policy"
        );

        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(status),
                false,
                consentId
        );

        // When
        String toString = response.toString();

        // Then
        assertThat(toString).contains("ConsentStatusResponse");
        assertThat(toString).contains(consentId.toString());
        assertThat(toString).contains("reconsentNeeded=false");
    }
}

