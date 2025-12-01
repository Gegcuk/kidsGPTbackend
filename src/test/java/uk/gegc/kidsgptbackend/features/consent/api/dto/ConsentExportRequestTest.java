package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentExportRequest DTO Tests")
class ConsentExportRequestTest extends BaseUnitTest {

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
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        LocalDateTime to = LocalDateTime.now();
        
        ConsentExportRequest request = new ConsentExportRequest(
                "Data export for compliance",
                from,
                to,
                List.of(ConsentType.PRIVACY_POLICY),
                List.of(ConsentStatus.GRANTED),
                "UK",
                "London",
                "user123",
                "CSV"
        );

        // When
        Set<ConstraintViolation<ConsentExportRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
        assertThat(request.auditReason()).isEqualTo("Data export for compliance");
        assertThat(request.fromDate()).isEqualTo(from);
        assertThat(request.toDate()).isEqualTo(to);
        assertThat(request.consentTypes()).containsExactly(ConsentType.PRIVACY_POLICY);
        assertThat(request.consentStatuses()).containsExactly(ConsentStatus.GRANTED);
        assertThat(request.jurisdiction()).isEqualTo("UK");
        assertThat(request.region()).isEqualTo("London");
        assertThat(request.userId()).isEqualTo("user123");
        assertThat(request.format()).isEqualTo("CSV");
    }

    @Test
    @DisplayName("should create valid request with null optional fields")
    void createRequest_nullOptionalFields_valid() {
        // Given
        ConsentExportRequest request = new ConsentExportRequest(
                "Export",
                null, // fromDate is optional
                null, // toDate is optional
                null, // consentTypes is optional
                null, // consentStatuses is optional
                null, // jurisdiction is optional
                null, // region is optional
                null, // userId is optional
                null  // format is optional
        );

        // When
        Set<ConstraintViolation<ConsentExportRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("should fail validation when auditReason is blank")
    void createRequest_blankAuditReason_invalid() {
        // Given
        ConsentExportRequest request = new ConsentExportRequest(
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentExportRequest>> violations = validator.validate(request);

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
        ConsentExportRequest request = new ConsentExportRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentExportRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("auditReason") &&
                v.getMessage().contains("Audit reason is required"));
    }

    @Test
    @DisplayName("should handle all consent types in list")
    void createRequest_allConsentTypes_valid() {
        // Given
        ConsentExportRequest request = new ConsentExportRequest(
                "Export",
                null,
                null,
                List.of(ConsentType.values()),
                null,
                null,
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentExportRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
        assertThat(request.consentTypes()).hasSize(ConsentType.values().length);
    }

    @Test
    @DisplayName("should handle all consent statuses in list")
    void createRequest_allConsentStatuses_valid() {
        // Given
        ConsentExportRequest request = new ConsentExportRequest(
                "Export",
                null,
                null,
                null,
                List.of(ConsentStatus.values()),
                null,
                null,
                null,
                null
        );

        // When
        Set<ConstraintViolation<ConsentExportRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
        assertThat(request.consentStatuses()).hasSize(ConsentStatus.values().length);
    }

    @Test
    @DisplayName("should handle different export formats")
    void createRequest_differentFormats_valid() {
        String[] formats = {"CSV", "JSON", "XML"};
        
        for (String format : formats) {
            ConsentExportRequest request = new ConsentExportRequest(
                    "Export",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    format
            );

            Set<ConstraintViolation<ConsentExportRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
            assertThat(request.format()).isEqualTo(format);
        }
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        LocalDateTime to = LocalDateTime.now();
        
        ConsentExportRequest request1 = new ConsentExportRequest(
                "Export",
                from,
                to,
                List.of(ConsentType.PRIVACY_POLICY),
                List.of(ConsentStatus.GRANTED),
                "UK",
                "London",
                "user123",
                "CSV"
        );

        ConsentExportRequest request2 = new ConsentExportRequest(
                "Export",
                from,
                to,
                List.of(ConsentType.PRIVACY_POLICY),
                List.of(ConsentStatus.GRANTED),
                "UK",
                "London",
                "user123",
                "CSV"
        );

        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        ConsentExportRequest request = new ConsentExportRequest(
                "Export",
                null,
                null,
                null,
                null,
                "UK",
                null,
                null,
                "CSV"
        );

        // When
        String toString = request.toString();

        // Then
        assertThat(toString).contains("ConsentExportRequest");
        assertThat(toString).contains("Export");
        assertThat(toString).contains("CSV");
    }
}

