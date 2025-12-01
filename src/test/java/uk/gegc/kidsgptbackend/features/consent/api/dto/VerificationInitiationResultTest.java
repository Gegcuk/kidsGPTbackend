package uk.gegc.kidsgptbackend.features.consent.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerificationInitiationResult DTO Tests")
class VerificationInitiationResultTest extends BaseUnitTest {

    @Test
    @DisplayName("should create result with newlyCreated true")
    void createResult_newlyCreatedTrue_valid() {
        // Given
        UUID verificationId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        VerificationStatusResponse statusResponse = new VerificationStatusResponse(
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
        VerificationInitiationResult result = new VerificationInitiationResult(
                statusResponse,
                true
        );

        // Then
        assertThat(result.verificationStatus()).isEqualTo(statusResponse);
        assertThat(result.newlyCreated()).isTrue();
    }

    @Test
    @DisplayName("should create result with newlyCreated false")
    void createResult_newlyCreatedFalse_valid() {
        // Given
        UUID verificationId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        VerificationStatusResponse statusResponse = new VerificationStatusResponse(
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
        VerificationInitiationResult result = new VerificationInitiationResult(
                statusResponse,
                false
        );

        // Then
        assertThat(result.verificationStatus()).isEqualTo(statusResponse);
        assertThat(result.newlyCreated()).isFalse();
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        UUID verificationId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        VerificationStatusResponse statusResponse = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.PENDING,
                1,
                now.plusMinutes(30),
                null,
                now
        );

        VerificationInitiationResult result1 = new VerificationInitiationResult(
                statusResponse,
                true
        );

        VerificationInitiationResult result2 = new VerificationInitiationResult(
                statusResponse,
                true
        );

        // Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        UUID verificationId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        VerificationStatusResponse statusResponse = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.PENDING,
                1,
                now.plusMinutes(30),
                null,
                now
        );

        VerificationInitiationResult result = new VerificationInitiationResult(
                statusResponse,
                true
        );

        // When
        String toString = result.toString();

        // Then
        assertThat(toString).contains("VerificationInitiationResult");
        assertThat(toString).contains("newlyCreated=true");
    }
}

