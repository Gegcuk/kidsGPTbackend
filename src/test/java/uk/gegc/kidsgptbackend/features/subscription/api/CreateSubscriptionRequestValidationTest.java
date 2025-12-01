package uk.gegc.kidsgptbackend.features.subscription.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import uk.gegc.kidsgptbackend.features.subscription.api.dto.CreateSubscriptionRequest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.CONCURRENT)
class CreateSubscriptionRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid request should pass validation")
    void validRequest_shouldPassValidation() {
        // Given
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                UUID.randomUUID(),
                "plus_monthly",
                "purchase_token_123"
        );

        // When
        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Missing planId should fail validation with correct message")
    void missingPlanId_shouldFailValidation() {
        // Given
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                null, // Missing planId
                "plus_monthly",
                "purchase_token_123"
        );

        // When
        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateSubscriptionRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("planId");
        assertThat(violation.getMessage()).isEqualTo("Plan ID is required");
    }

    @Test
    @DisplayName("Missing googleProductId should fail validation")
    void missingGoogleProductId_shouldFailValidation() {
        // Given
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                UUID.randomUUID(),
                null, // Missing googleProductId
                "purchase_token_123"
        );

        // When
        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateSubscriptionRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("googleProductId");
        assertThat(violation.getMessage()).isEqualTo("Google product ID is required");
    }

    @Test
    @DisplayName("Empty googleProductId should fail validation")
    void emptyGoogleProductId_shouldFailValidation() {
        // Given
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                UUID.randomUUID(),
                "", // Empty googleProductId
                "purchase_token_123"
        );

        // When
        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateSubscriptionRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("googleProductId");
        assertThat(violation.getMessage()).isEqualTo("Google product ID is required");
    }

    @Test
    @DisplayName("Blank googleProductId should fail validation")
    void blankGoogleProductId_shouldFailValidation() {
        // Given
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                UUID.randomUUID(),
                "   ", // Blank googleProductId
                "purchase_token_123"
        );

        // When
        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateSubscriptionRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("googleProductId");
        assertThat(violation.getMessage()).isEqualTo("Google product ID is required");
    }

    @Test
    @DisplayName("Missing purchaseToken should fail validation")
    void missingPurchaseToken_shouldFailValidation() {
        // Given
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                UUID.randomUUID(),
                "plus_monthly",
                null // Missing purchaseToken
        );

        // When
        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateSubscriptionRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purchaseToken");
        assertThat(violation.getMessage()).isEqualTo("Purchase token is required");
    }

    @Test
    @DisplayName("Empty purchaseToken should fail validation")
    void emptyPurchaseToken_shouldFailValidation() {
        // Given
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                UUID.randomUUID(),
                "plus_monthly",
                "" // Empty purchaseToken
        );

        // When
        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateSubscriptionRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purchaseToken");
        assertThat(violation.getMessage()).isEqualTo("Purchase token is required");
    }

    @Test
    @DisplayName("Blank purchaseToken should fail validation")
    void blankPurchaseToken_shouldFailValidation() {
        // Given
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                UUID.randomUUID(),
                "plus_monthly",
                "   " // Blank purchaseToken
        );

        // When
        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateSubscriptionRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purchaseToken");
        assertThat(violation.getMessage()).isEqualTo("Purchase token is required");
    }

    @Test
    @DisplayName("Multiple missing fields should fail validation with multiple violations")
    void multipleMissingFields_shouldFailValidation() {
        // Given
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                null, // Missing planId
                null, // Missing googleProductId
                null  // Missing purchaseToken
        );

        // When
        Set<ConstraintViolation<CreateSubscriptionRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(3);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("planId", "googleProductId", "purchaseToken");
    }
}
