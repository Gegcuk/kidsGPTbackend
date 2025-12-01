package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentWithdrawRequest DTO Tests")
class ConsentWithdrawRequestTest extends BaseUnitTest {

    private Validator validator;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("should create valid request with all required fields")
    void createRequest_allRequiredFields_valid() {
        // Given
        ConsentWithdrawRequest request = new ConsentWithdrawRequest(
                "user123",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "User requested withdrawal",
                "192.168.1.1",
                "Mozilla/5.0"
        );

        // When
        Set<ConstraintViolation<ConsentWithdrawRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
        assertThat(request.userId()).isEqualTo("user123");
        assertThat(request.consentType()).isEqualTo(ConsentType.PRIVACY_POLICY);
        assertThat(request.consentVersion()).isEqualTo("1.0");
        assertThat(request.reason()).isEqualTo("User requested withdrawal");
        assertThat(request.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(request.userAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("should create valid request with null optional fields")
    void createRequest_nullOptionalFields_valid() {
        // Given
        ConsentWithdrawRequest request = new ConsentWithdrawRequest(
                "user123",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                null, // reason is optional
                null, // ipAddress is optional
                null  // userAgent is optional
        );

        // When
        Set<ConstraintViolation<ConsentWithdrawRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("should fail validation when userId is blank")
    void createRequest_blankUserId_invalid() {
        // Given
        ConsentWithdrawRequest request = new ConsentWithdrawRequest(
                "   ",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentWithdrawRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("userId") &&
                v.getMessage().contains("User ID is required"));
    }

    @Test
    @DisplayName("should fail validation when userId is null")
    void createRequest_nullUserId_invalid() {
        // Given
        ConsentWithdrawRequest request = new ConsentWithdrawRequest(
                null,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentWithdrawRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("userId") &&
                v.getMessage().contains("User ID is required"));
    }

    @Test
    @DisplayName("should fail validation when consentType is null")
    void createRequest_nullConsentType_invalid() {
        // Given
        ConsentWithdrawRequest request = new ConsentWithdrawRequest(
                "user123",
                null,
                "1.0",
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentWithdrawRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("consentType") &&
                v.getMessage().contains("Consent type is required"));
    }

    @Test
    @DisplayName("should fail validation when consentVersion is blank")
    void createRequest_blankConsentVersion_invalid() {
        // Given
        ConsentWithdrawRequest request = new ConsentWithdrawRequest(
                "user123",
                ConsentType.PRIVACY_POLICY,
                "",
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentWithdrawRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("consentVersion") &&
                v.getMessage().contains("Consent version is required"));
    }

    @Test
    @DisplayName("should handle all consent types")
    void createRequest_allConsentTypes_valid() {
        for (ConsentType type : ConsentType.values()) {
            ConsentWithdrawRequest request = new ConsentWithdrawRequest(
                    "user123",
                    type,
                    "1.0",
                    null,
                    null,
                    null
            );

            Set<ConstraintViolation<ConsentWithdrawRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        ConsentWithdrawRequest request1 = new ConsentWithdrawRequest(
                "user123",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "Reason",
                "192.168.1.1",
                "Mozilla/5.0"
        );

        ConsentWithdrawRequest request2 = new ConsentWithdrawRequest(
                "user123",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "Reason",
                "192.168.1.1",
                "Mozilla/5.0"
        );

        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        ConsentWithdrawRequest request = new ConsentWithdrawRequest(
                "user123",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "Reason",
                "192.168.1.1",
                "Mozilla/5.0"
        );

        // When
        String toString = request.toString();

        // Then
        assertThat(toString).contains("ConsentWithdrawRequest");
        assertThat(toString).contains("user123");
        assertThat(toString).contains("PRIVACY_POLICY");
        assertThat(toString).contains("1.0");
    }
}

