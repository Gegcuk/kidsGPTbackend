package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentCleanupRequest DTO Tests")
class ConsentCleanupRequestTest extends BaseUnitTest {

    private Validator validator;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("should create valid request with all fields")
    void createRequest_allFields_valid() {
        // Given
        ConsentCleanupRequest request = new ConsentCleanupRequest(
                "Data retention policy cleanup",
                true,
                100,
                "UK",
                "London"
        );

        // When
        Set<ConstraintViolation<ConsentCleanupRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
        assertThat(request.auditReason()).isEqualTo("Data retention policy cleanup");
        assertThat(request.dryRun()).isTrue();
        assertThat(request.batchSize()).isEqualTo(100);
        assertThat(request.jurisdiction()).isEqualTo("UK");
        assertThat(request.region()).isEqualTo("London");
    }

    @Test
    @DisplayName("should create valid request with null optional fields")
    void createRequest_nullOptionalFields_valid() {
        // Given
        ConsentCleanupRequest request = new ConsentCleanupRequest(
                "Cleanup",
                false,
                null, // batchSize is optional
                null, // jurisdiction is optional
                null  // region is optional
        );

        // When
        Set<ConstraintViolation<ConsentCleanupRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("should fail validation when auditReason is blank")
    void createRequest_blankAuditReason_invalid() {
        // Given
        ConsentCleanupRequest request = new ConsentCleanupRequest(
                "   ",
                true,
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentCleanupRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("auditReason") &&
                v.getMessage().contains("Audit reason is required"));
    }

    @Test
    @DisplayName("should fail validation when auditReason is null")
    void createRequest_nullAuditReason_invalid() {
        // Given
        ConsentCleanupRequest request = new ConsentCleanupRequest(
                null,
                true,
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentCleanupRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("auditReason") &&
                v.getMessage().contains("Audit reason is required"));
    }

    @Test
    @DisplayName("should fail validation when dryRun is null")
    void createRequest_nullDryRun_invalid() {
        // Given
        ConsentCleanupRequest request = new ConsentCleanupRequest(
                "Cleanup",
                null,
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentCleanupRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("dryRun") &&
                v.getMessage().contains("Dry run flag is required"));
    }

    @Test
    @DisplayName("should handle dryRun true and false")
    void createRequest_dryRunValues_valid() {
        // Test with true
        ConsentCleanupRequest request1 = new ConsentCleanupRequest(
                "Cleanup",
                true,
                null,
                null,
                null
        );
        Set<ConstraintViolation<ConsentCleanupRequest>> violations1 = validator.validate(request1);
        assertThat(violations1).isEmpty();
        assertThat(request1.dryRun()).isTrue();

        // Test with false
        ConsentCleanupRequest request2 = new ConsentCleanupRequest(
                "Cleanup",
                false,
                null,
                null,
                null
        );
        Set<ConstraintViolation<ConsentCleanupRequest>> violations2 = validator.validate(request2);
        assertThat(violations2).isEmpty();
        assertThat(request2.dryRun()).isFalse();
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        ConsentCleanupRequest request1 = new ConsentCleanupRequest(
                "Cleanup",
                true,
                100,
                "UK",
                "London"
        );

        ConsentCleanupRequest request2 = new ConsentCleanupRequest(
                "Cleanup",
                true,
                100,
                "UK",
                "London"
        );

        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        ConsentCleanupRequest request = new ConsentCleanupRequest(
                "Cleanup",
                true,
                100,
                "UK",
                "London"
        );

        // When
        String toString = request.toString();

        // Then
        assertThat(toString).contains("ConsentCleanupRequest");
        assertThat(toString).contains("Cleanup");
        assertThat(toString).contains("dryRun=true");
    }
}

