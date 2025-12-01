package uk.gegc.kidsgptbackend.features.consent.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ComplianceAgeThresholdResponse DTO Tests")
class ComplianceAgeThresholdResponseTest extends BaseUnitTest {

    @Test
    @DisplayName("should create response with all fields")
    void createResponse_allFields_valid() {
        // Given
        ComplianceAgeThresholdResponse response = new ComplianceAgeThresholdResponse(
                "UK",
                "England",
                13,
                7,
                true,
                List.of(VerificationMethod.EMAIL, VerificationMethod.SMS),
                "UK requires parental consent for users under 13"
        );

        // Then
        assertThat(response.country()).isEqualTo("UK");
        assertThat(response.region()).isEqualTo("England");
        assertThat(response.minorThreshold()).isEqualTo(13);
        assertThat(response.retentionYears()).isEqualTo(7);
        assertThat(response.teenOptIn()).isTrue();
        assertThat(response.allowedMethods()).containsExactly(VerificationMethod.EMAIL, VerificationMethod.SMS);
        assertThat(response.notes()).isEqualTo("UK requires parental consent for users under 13");
    }

    @Test
    @DisplayName("should create response with null optional fields")
    void createResponse_nullOptionalFields_valid() {
        // Given
        ComplianceAgeThresholdResponse response = new ComplianceAgeThresholdResponse(
                "UK",
                null, // region is optional
                13,
                7,
                false,
                null, // allowedMethods is optional
                null  // notes is optional
        );

        // Then
        assertThat(response.country()).isEqualTo("UK");
        assertThat(response.region()).isNull();
        assertThat(response.minorThreshold()).isEqualTo(13);
        assertThat(response.retentionYears()).isEqualTo(7);
        assertThat(response.teenOptIn()).isFalse();
        assertThat(response.allowedMethods()).isNull();
        assertThat(response.notes()).isNull();
    }

    @Test
    @DisplayName("should handle teenOptIn true and false")
    void createResponse_teenOptInValues_valid() {
        // Test with true
        ComplianceAgeThresholdResponse response1 = new ComplianceAgeThresholdResponse(
                "UK",
                null,
                13,
                7,
                true,
                null,
                null
        );
        assertThat(response1.teenOptIn()).isTrue();

        // Test with false
        ComplianceAgeThresholdResponse response2 = new ComplianceAgeThresholdResponse(
                "UK",
                null,
                13,
                7,
                false,
                null,
                null
        );
        assertThat(response2.teenOptIn()).isFalse();
    }

    @Test
    @DisplayName("should handle all verification methods")
    void createResponse_allVerificationMethods_valid() {
        // Given
        ComplianceAgeThresholdResponse response = new ComplianceAgeThresholdResponse(
                "UK",
                null,
                13,
                7,
                true,
                List.of(VerificationMethod.values()),
                null
        );

        // Then
        assertThat(response.allowedMethods()).hasSize(VerificationMethod.values().length);
        assertThat(response.allowedMethods()).contains(VerificationMethod.EMAIL, VerificationMethod.SMS);
    }

    @Test
    @DisplayName("should handle empty allowedMethods list")
    void createResponse_emptyAllowedMethods_valid() {
        // Given
        ComplianceAgeThresholdResponse response = new ComplianceAgeThresholdResponse(
                "UK",
                null,
                13,
                7,
                true,
                List.of(),
                null
        );

        // Then
        assertThat(response.allowedMethods()).isEmpty();
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        ComplianceAgeThresholdResponse response1 = new ComplianceAgeThresholdResponse(
                "UK",
                "England",
                13,
                7,
                true,
                List.of(VerificationMethod.EMAIL),
                "Notes"
        );

        ComplianceAgeThresholdResponse response2 = new ComplianceAgeThresholdResponse(
                "UK",
                "England",
                13,
                7,
                true,
                List.of(VerificationMethod.EMAIL),
                "Notes"
        );

        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        ComplianceAgeThresholdResponse response = new ComplianceAgeThresholdResponse(
                "UK",
                "England",
                13,
                7,
                true,
                List.of(VerificationMethod.EMAIL),
                "Notes"
        );

        // When
        String toString = response.toString();

        // Then
        assertThat(toString).contains("ComplianceAgeThresholdResponse");
        assertThat(toString).contains("UK");
        assertThat(toString).contains("13");
        assertThat(toString).contains("7");
    }
}

