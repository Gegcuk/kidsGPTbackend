package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentGrantRequest DTO Tests")
class ConsentGrantRequestTest extends BaseUnitTest {

    private Validator validator;
    private UUID userId;
    private UUID verificationId;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        userId = UUID.randomUUID();
        verificationId = UUID.randomUUID();
    }

    @Test
    @DisplayName("should create valid request with all required fields")
    void createRequest_allRequiredFields_valid() {
        // Given
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                verificationId,
                "UK",
                "London",
                "en-GB",
                ConsentSource.WEB,
                List.of(UUID.randomUUID()),
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );

        // When
        Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("should create valid request with null optional fields")
    void createRequest_nullOptionalFields_valid() {
        // Given
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                null, // verificationId is optional
                "UK",
                null, // region is optional
                null, // locale is optional
                ConsentSource.WEB,
                null, // kids is optional
                null, // ipAddress is optional
                null, // userAgent is optional
                LawfulBasis.CONSENT
        );

        // When
        Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("should fail validation when userId is null")
    void createRequest_nullUserId_invalid() {
        // Given
        ConsentGrantRequest request = new ConsentGrantRequest(
                null,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                verificationId,
                "UK",
                null,
                null,
                ConsentSource.WEB,
                null,
                null,
                null,
                LawfulBasis.CONSENT
        );

        // When
        Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);

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
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                null,
                "1.0",
                "https://example.com/policy",
                "abc123",
                verificationId,
                "UK",
                null,
                null,
                ConsentSource.WEB,
                null,
                null,
                null,
                LawfulBasis.CONSENT
        );

        // When
        Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);

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
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "   ",
                "https://example.com/policy",
                "abc123",
                verificationId,
                "UK",
                null,
                null,
                ConsentSource.WEB,
                null,
                null,
                null,
                LawfulBasis.CONSENT
        );

        // When
        Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("consentVersion") &&
                v.getMessage().contains("Consent version is required"));
    }

    @Test
    @DisplayName("should fail validation when policyUrl is blank")
    void createRequest_blankPolicyUrl_invalid() {
        // Given
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "",
                "abc123",
                verificationId,
                "UK",
                null,
                null,
                ConsentSource.WEB,
                null,
                null,
                null,
                LawfulBasis.CONSENT
        );

        // When
        Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("policyUrl") &&
                v.getMessage().contains("Policy URL is required"));
    }

    @Test
    @DisplayName("should fail validation when contentHash is blank")
    void createRequest_blankContentHash_invalid() {
        // Given
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                null,
                verificationId,
                "UK",
                null,
                null,
                ConsentSource.WEB,
                null,
                null,
                null,
                LawfulBasis.CONSENT
        );

        // When
        Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("contentHash") &&
                v.getMessage().contains("Content hash is required"));
    }

    @Test
    @DisplayName("should fail validation when jurisdiction is blank")
    void createRequest_blankJurisdiction_invalid() {
        // Given
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                verificationId,
                "   ",
                null,
                null,
                ConsentSource.WEB,
                null,
                null,
                null,
                LawfulBasis.CONSENT
        );

        // When
        Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("jurisdiction") &&
                v.getMessage().contains("Jurisdiction is required"));
    }

    @Test
    @DisplayName("should fail validation when source is null")
    void createRequest_nullSource_invalid() {
        // Given
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                verificationId,
                "UK",
                null,
                null,
                null,
                null,
                null,
                null,
                LawfulBasis.CONSENT
        );

        // When
        Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("source") &&
                v.getMessage().contains("Source is required"));
    }

    @Test
    @DisplayName("should fail validation when lawfulBasis is null")
    void createRequest_nullLawfulBasis_invalid() {
        // Given
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                verificationId,
                "UK",
                null,
                null,
                ConsentSource.WEB,
                null,
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("lawfulBasis") &&
                v.getMessage().contains("Lawful basis is required"));
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        UUID kidId = UUID.randomUUID();
        ConsentGrantRequest request1 = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                verificationId,
                "UK",
                "London",
                "en-GB",
                ConsentSource.WEB,
                List.of(kidId),
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );

        ConsentGrantRequest request2 = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                verificationId,
                "UK",
                "London",
                "en-GB",
                ConsentSource.WEB,
                List.of(kidId),
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );

        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                verificationId,
                "UK",
                "London",
                "en-GB",
                ConsentSource.WEB,
                List.of(UUID.randomUUID()),
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );

        // When
        String toString = request.toString();

        // Then
        assertThat(toString).contains("ConsentGrantRequest");
        assertThat(toString).contains(userId.toString());
        assertThat(toString).contains("PRIVACY_POLICY");
        assertThat(toString).contains("1.0");
    }

    @Test
    @DisplayName("should handle all consent types")
    void createRequest_allConsentTypes_valid() {
        for (ConsentType type : ConsentType.values()) {
            ConsentGrantRequest request = new ConsentGrantRequest(
                    userId,
                    type,
                    "1.0",
                    "https://example.com/policy",
                    "abc123",
                    verificationId,
                    "UK",
                    null,
                    null,
                    ConsentSource.WEB,
                    null,
                    null,
                    null,
                    LawfulBasis.CONSENT
            );

            Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    @Test
    @DisplayName("should handle all consent sources")
    void createRequest_allConsentSources_valid() {
        for (ConsentSource source : ConsentSource.values()) {
            ConsentGrantRequest request = new ConsentGrantRequest(
                    userId,
                    ConsentType.PRIVACY_POLICY,
                    "1.0",
                    "https://example.com/policy",
                    "abc123",
                    verificationId,
                    "UK",
                    null,
                    null,
                    source,
                    null,
                    null,
                    null,
                    LawfulBasis.CONSENT
            );

            Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    @Test
    @DisplayName("should handle all lawful bases")
    void createRequest_allLawfulBases_valid() {
        for (LawfulBasis basis : LawfulBasis.values()) {
            ConsentGrantRequest request = new ConsentGrantRequest(
                    userId,
                    ConsentType.PRIVACY_POLICY,
                    "1.0",
                    "https://example.com/policy",
                    "abc123",
                    verificationId,
                    "UK",
                    null,
                    null,
                    ConsentSource.WEB,
                    null,
                    null,
                    null,
                    basis
            );

            Set<ConstraintViolation<ConsentGrantRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }
}

