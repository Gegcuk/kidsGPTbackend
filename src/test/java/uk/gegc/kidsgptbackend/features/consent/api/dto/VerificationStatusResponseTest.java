package uk.gegc.kidsgptbackend.features.consent.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerificationStatusResponse DTO Tests")
class VerificationStatusResponseTest extends BaseUnitTest {

    @Test
    @DisplayName("should create response with all fields")
    void createResponse_allFields_valid() {
        // Given
        UUID verificationId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusMinutes(30);
        OffsetDateTime verifiedAt = now.plusMinutes(5);

        // When
        VerificationStatusResponse response = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.VERIFIED,
                1,
                expiresAt,
                verifiedAt,
                now
        );

        // Then
        assertThat(response.verificationId()).isEqualTo(verificationId);
        assertThat(response.parentId()).isEqualTo(parentId);
        assertThat(response.verificationMethod()).isEqualTo(VerificationMethod.EMAIL);
        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(response.attemptCount()).isEqualTo(1);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.verifiedAt()).isEqualTo(verifiedAt);
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("should create response with null optional fields")
    void createResponse_nullOptionalFields_valid() {
        // Given
        UUID verificationId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // When
        VerificationStatusResponse response = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.SMS,
                VerificationStatus.PENDING,
                0,
                now.plusMinutes(30),
                null, // verifiedAt is null for pending
                now
        );

        // Then
        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(response.verifiedAt()).isNull();
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        UUID verificationId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        VerificationStatusResponse response1 = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.PENDING,
                1,
                now.plusMinutes(30),
                null,
                now
        );

        VerificationStatusResponse response2 = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.PENDING,
                1,
                now.plusMinutes(30),
                null,
                now
        );

        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        UUID verificationId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        VerificationStatusResponse response = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.PENDING,
                1,
                now.plusMinutes(30),
                null,
                now
        );

        // When
        String toString = response.toString();

        // Then
        assertThat(toString).contains("VerificationStatusResponse");
        assertThat(toString).contains(verificationId.toString());
        assertThat(toString).contains(parentId.toString());
        assertThat(toString).contains("EMAIL");
        assertThat(toString).contains("PENDING");
    }

    @Test
    @DisplayName("should handle all verification statuses")
    void createResponse_allStatuses_valid() {
        // Given
        UUID verificationId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Test all statuses
        for (VerificationStatus status : VerificationStatus.values()) {
            VerificationStatusResponse response = new VerificationStatusResponse(
                    verificationId,
                    parentId,
                    VerificationMethod.EMAIL,
                    status,
                    1,
                    now.plusMinutes(30),
                    status == VerificationStatus.VERIFIED ? now : null,
                    now
            );

            // Then
            assertThat(response.verificationStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("should handle all verification methods")
    void createResponse_allMethods_valid() {
        // Given
        UUID verificationId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Test all methods
        for (VerificationMethod method : VerificationMethod.values()) {
            VerificationStatusResponse response = new VerificationStatusResponse(
                    verificationId,
                    parentId,
                    method,
                    VerificationStatus.PENDING,
                    1,
                    now.plusMinutes(30),
                    null,
                    now
            );

            // Then
            assertThat(response.verificationMethod()).isEqualTo(method);
        }
    }
}

