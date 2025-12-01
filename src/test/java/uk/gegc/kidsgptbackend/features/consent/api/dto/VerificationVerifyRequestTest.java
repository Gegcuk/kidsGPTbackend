package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerificationVerifyRequest DTO Tests")
class VerificationVerifyRequestTest extends BaseUnitTest {

    private Validator validator;
    private UUID verificationId;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        verificationId = UUID.randomUUID();
    }

    @Test
    @DisplayName("should create valid request with 6-digit code")
    void createRequest_validCode_valid() {
        // Given
        VerificationVerifyRequest request = new VerificationVerifyRequest(
                verificationId,
                "123456"
        );

        // When
        Set<ConstraintViolation<VerificationVerifyRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
        assertThat(request.verificationId()).isEqualTo(verificationId);
        assertThat(request.verificationCode()).isEqualTo("123456");
    }

    @Test
    @DisplayName("should fail validation when verificationId is null")
    void createRequest_nullVerificationId_invalid() {
        // Given
        VerificationVerifyRequest request = new VerificationVerifyRequest(
                null,
                "123456"
        );

        // When
        Set<ConstraintViolation<VerificationVerifyRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("verificationId") &&
                v.getMessage().contains("Verification ID is required"));
    }

    @Test
    @DisplayName("should fail validation when verificationCode is null")
    void createRequest_nullVerificationCode_invalid() {
        // Given
        VerificationVerifyRequest request = new VerificationVerifyRequest(
                verificationId,
                null
        );

        // When
        Set<ConstraintViolation<VerificationVerifyRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("verificationCode") &&
                v.getMessage().contains("Verification code is required"));
    }

    @Test
    @DisplayName("should fail validation when verificationCode is blank")
    void createRequest_blankVerificationCode_invalid() {
        // Given
        VerificationVerifyRequest request = new VerificationVerifyRequest(
                verificationId,
                "   "
        );

        // When
        Set<ConstraintViolation<VerificationVerifyRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("verificationCode"));
    }

    @Test
    @DisplayName("should fail validation when verificationCode is not 6 digits")
    void createRequest_invalidCodeLength_invalid() {
        // Test with 5 digits
        VerificationVerifyRequest request1 = new VerificationVerifyRequest(
                verificationId,
                "12345"
        );
        Set<ConstraintViolation<VerificationVerifyRequest>> violations1 = validator.validate(request1);
        assertThat(violations1).isNotEmpty();
        assertThat(violations1).anyMatch(v -> 
                v.getPropertyPath().toString().equals("verificationCode") &&
                v.getMessage().contains("6 digits"));

        // Test with 7 digits
        VerificationVerifyRequest request2 = new VerificationVerifyRequest(
                verificationId,
                "1234567"
        );
        Set<ConstraintViolation<VerificationVerifyRequest>> violations2 = validator.validate(request2);
        assertThat(violations2).isNotEmpty();
        assertThat(violations2).anyMatch(v -> 
                v.getPropertyPath().toString().equals("verificationCode") &&
                v.getMessage().contains("6 digits"));
    }

    @Test
    @DisplayName("should fail validation when verificationCode contains non-digits")
    void createRequest_nonNumericCode_invalid() {
        // Given
        VerificationVerifyRequest request = new VerificationVerifyRequest(
                verificationId,
                "12345a"
        );

        // When
        Set<ConstraintViolation<VerificationVerifyRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("verificationCode") &&
                v.getMessage().contains("6 digits"));
    }

    @Test
    @DisplayName("should accept valid 6-digit codes")
    void createRequest_validCodes_valid() {
        // Test various valid codes
        String[] validCodes = {"000000", "123456", "999999", "012345"};

        for (String code : validCodes) {
            VerificationVerifyRequest request = new VerificationVerifyRequest(
                    verificationId,
                    code
            );

            Set<ConstraintViolation<VerificationVerifyRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        VerificationVerifyRequest request1 = new VerificationVerifyRequest(
                verificationId,
                "123456"
        );
        VerificationVerifyRequest request2 = new VerificationVerifyRequest(
                verificationId,
                "123456"
        );

        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        VerificationVerifyRequest request = new VerificationVerifyRequest(
                verificationId,
                "123456"
        );

        // When
        String toString = request.toString();

        // Then
        assertThat(toString).contains("VerificationVerifyRequest");
        assertThat(toString).contains(verificationId.toString());
        assertThat(toString).contains("123456");
    }
}

