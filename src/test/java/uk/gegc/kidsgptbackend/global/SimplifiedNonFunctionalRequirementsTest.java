package uk.gegc.kidsgptbackend.global;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import uk.gegc.kidsgptbackend.shared.exception.advice.GlobalExceptionHandler;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simplified tests for core non-functional requirements that don't require complex webhook processing.
 * These tests focus on the essential behaviors without the complexity of full integration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Simplified Non-Functional Requirements Tests")
class SimplifiedNonFunctionalRequirementsTest {

    private GlobalExceptionHandler globalExceptionHandler;
    private HttpServletRequest httpServletRequest;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // Use constructor injection with a fixed clock
        fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);
        globalExceptionHandler = new GlobalExceptionHandler(fixedClock);
        
        httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    @DisplayName("RuntimeException should return generic error message")
    void runtimeException_shouldReturnGenericErrorMessage() {
        // Given
        RuntimeException exception = new RuntimeException("Internal database connection failed");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleRuntimeException(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        // RuntimeException returns generic error message for security
        assertThat(response).isNotNull();
        assertThat(response.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(response.getDetail()).doesNotContain("database");
        assertThat(response.getDetail()).doesNotContain("connection");
        assertThat(response.getDetail()).doesNotContain("Internal");
    }

    @Test
    @DisplayName("Time handling should use UTC/Instant consistently")
    void timeHandling_shouldUseUtcInstantConsistently() {
        // Given
        Instant utcTime = Instant.parse("2024-06-15T12:00:00Z");
        Clock fixedClock = Clock.fixed(utcTime, ZoneOffset.UTC);

        // When
        Instant currentTime = Instant.now(fixedClock);

        // Then
        assertThat(currentTime).isEqualTo(utcTime);
        assertThat(currentTime.toString()).endsWith("Z"); // UTC format
    }

    @Test
    @DisplayName("Null timestamp handling should be graceful")
    void nullTimestampHandling_shouldBeGraceful() {
        // Given
        UserSubscription subscription = new UserSubscription();

        // When - Leave timestamps as null (simulating webhook data without these fields)
        // No explicit setting, fields remain null
        
        // Then - Should handle null timestamps gracefully without throwing exceptions
        assertThat(subscription.getCurrentPeriodStart()).isNull();
        assertThat(subscription.getCurrentPeriodEnd()).isNull();
        assertThat(subscription.getNextBillingDate()).isNull();
        
        // Also test that we can explicitly set null values
        subscription.setCurrentPeriodStart(null);
        subscription.setCurrentPeriodEnd(null);
        subscription.setNextBillingDate(null);
        
        assertThat(subscription.getCurrentPeriodStart()).isNull();
        assertThat(subscription.getCurrentPeriodEnd()).isNull();
        assertThat(subscription.getNextBillingDate()).isNull();
    }

    @Test
    @DisplayName("Error responses should include proper RFC 9457 structure")
    void errorResponses_shouldIncludeProperStructure() {
        // Given
        RuntimeException exception = new RuntimeException("Test error");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleRuntimeException(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getDetail()).isNotNull();
        assertThat(response.getProperties().get("timestamp")).isNotNull();
        assertThat(response.getProperties().get("timestamp")).isEqualTo(Instant.parse("2024-01-01T12:00:00Z"));
    }

    @Test
    @DisplayName("UTC time consistency should be maintained")
    void utcTimeConsistency_shouldBeMaintained() {
        // Given
        Instant utcStart = Instant.parse("2024-06-15T12:00:00Z");
        Instant utcEnd = Instant.parse("2024-07-15T12:00:00Z");
        
        UserSubscription subscription = new UserSubscription();

        // When
        subscription.setCurrentPeriodStart(utcStart);
        subscription.setCurrentPeriodEnd(utcEnd);
        subscription.setNextBillingDate(utcEnd);

        // Then
        assertThat(subscription.getCurrentPeriodStart()).isEqualTo(utcStart);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(utcEnd);
        assertThat(subscription.getNextBillingDate()).isEqualTo(utcEnd);
        
        // Verify UTC format
        assertThat(subscription.getCurrentPeriodStart().toString()).endsWith("Z");
        assertThat(subscription.getCurrentPeriodEnd().toString()).endsWith("Z");
        assertThat(subscription.getNextBillingDate().toString()).endsWith("Z");
    }

    @Test
    @DisplayName("RuntimeException should always return generic message regardless of content")
    void runtimeException_shouldAlwaysReturnGenericMessage() {
        // Test various sensitive messages - all should get generic response
        String[] sensitiveMessages = {
            "Database connection failed",
            "SQL query error",
            "JDBC timeout",
            "Hibernate exception",
            "Internal server error",
            "Stack trace available",
            "Connection pool exhausted"
        };

        for (String message : sensitiveMessages) {
            // Given
            RuntimeException exception = new RuntimeException(message);

            // When
            ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleRuntimeException(exception, httpServletRequest);
            ProblemDetail response = responseEntity.getBody();

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getDetail()).isEqualTo("An unexpected error occurred");
            assertThat(response.getDetail()).doesNotContain(message.toLowerCase());
        }
    }

    @Test
    @DisplayName("ValidationException with sensitive terms should be sanitized")
    void validationException_withSensitiveTerms_shouldBeSanitized() {
        // Given
        uk.gegc.kidsgptbackend.shared.exception.ValidationException exception = 
            new uk.gegc.kidsgptbackend.shared.exception.ValidationException("Database connection failed");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleValidation(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getDetail()).contains("Invalid request");
        assertThat(response.getDetail()).doesNotContain("Database");
        assertThat(response.getDetail()).doesNotContain("connection");
    }
    
    @Test
    @DisplayName("ValidationException with user-friendly message should pass through")
    void validationException_withUserFriendlyMessage_shouldPassThrough() {
        // Given
        uk.gegc.kidsgptbackend.shared.exception.ValidationException exception = 
            new uk.gegc.kidsgptbackend.shared.exception.ValidationException("Please provide a valid email address");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleValidation(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getDetail()).contains("Please provide a valid email address");
    }
}
