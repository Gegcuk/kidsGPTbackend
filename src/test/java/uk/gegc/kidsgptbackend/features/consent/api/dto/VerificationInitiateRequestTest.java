package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerificationInitiateRequest DTO Tests")
class VerificationInitiateRequestTest extends BaseUnitTest {

    private Validator validator;
    private UUID parentId;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        parentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("should create valid request with EMAIL method")
    void createRequest_emailMethod_valid() {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "parent@example.com"
        );

        // When
        Set<ConstraintViolation<VerificationInitiateRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
        assertThat(request.parentId()).isEqualTo(parentId);
        assertThat(request.verificationMethod()).isEqualTo(VerificationMethod.EMAIL);
        assertThat(request.contactInfo()).isEqualTo("parent@example.com");
    }

    @Test
    @DisplayName("should create valid request with SMS method")
    void createRequest_smsMethod_valid() {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "+1234567890"
        );

        // When
        Set<ConstraintViolation<VerificationInitiateRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
        assertThat(request.verificationMethod()).isEqualTo(VerificationMethod.SMS);
        assertThat(request.contactInfo()).isEqualTo("+1234567890");
    }

    @Test
    @DisplayName("should fail validation when parentId is null")
    void createRequest_nullParentId_invalid() {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                null,
                VerificationMethod.EMAIL,
                "parent@example.com"
        );

        // When
        Set<ConstraintViolation<VerificationInitiateRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("parentId") &&
                v.getMessage().contains("Parent ID is required"));
    }

    @Test
    @DisplayName("should fail validation when verificationMethod is null")
    void createRequest_nullVerificationMethod_invalid() {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                null,
                "parent@example.com"
        );

        // When
        Set<ConstraintViolation<VerificationInitiateRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("verificationMethod") &&
                v.getMessage().contains("Verification method is required"));
    }

    @Test
    @DisplayName("should fail validation when contactInfo is null")
    void createRequest_nullContactInfo_invalid() {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                null
        );

        // When
        Set<ConstraintViolation<VerificationInitiateRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("contactInfo") &&
                v.getMessage().contains("Contact information is required"));
    }

    @Test
    @DisplayName("should fail validation when contactInfo is blank")
    void createRequest_blankContactInfo_invalid() {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "   "
        );

        // When
        Set<ConstraintViolation<VerificationInitiateRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("contactInfo") &&
                v.getMessage().contains("Contact information is required"));
    }

    @Test
    @DisplayName("should fail validation when contactInfo is empty")
    void createRequest_emptyContactInfo_invalid() {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                ""
        );

        // When
        Set<ConstraintViolation<VerificationInitiateRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("contactInfo") &&
                v.getMessage().contains("Contact information is required"));
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        VerificationInitiateRequest request1 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "parent@example.com"
        );
        VerificationInitiateRequest request2 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "parent@example.com"
        );

        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "parent@example.com"
        );

        // When
        String toString = request.toString();

        // Then
        assertThat(toString).contains("VerificationInitiateRequest");
        assertThat(toString).contains(parentId.toString());
        assertThat(toString).contains("EMAIL");
        assertThat(toString).contains("parent@example.com");
    }
}

