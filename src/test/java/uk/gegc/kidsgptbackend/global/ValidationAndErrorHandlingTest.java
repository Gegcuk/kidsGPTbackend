package uk.gegc.kidsgptbackend.global;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import uk.gegc.kidsgptbackend.shared.exception.advice.GlobalExceptionHandler;
import uk.gegc.kidsgptbackend.shared.exception.ResourceNotFoundException;
import uk.gegc.kidsgptbackend.shared.exception.ValidationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests to ensure proper validation and error handling:
 * - Every public method returns guarded defaults or throws domain exceptions as intended
 * - Errors include context but no secrets
 * - Validation failures are handled gracefully
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Validation and Error Handling Tests")
class ValidationAndErrorHandlingTest {

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
    @DisplayName("GlobalExceptionHandler should return guarded error responses with context but no secrets")
    void globalExceptionHandler_shouldReturnGuardedErrorResponses() {
        // Given
        ResourceNotFoundException exception = new ResourceNotFoundException("User not found with ID: 123");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleResourceNotFound(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getTitle()).isEqualTo("Resource Not Found");
        assertThat(response.getDetail()).contains("User not found with ID: 123");
        
        // Verify no sensitive information is exposed
        assertThat(response.getDetail()).doesNotContain("password");
        assertThat(response.getDetail()).doesNotContain("token");
        assertThat(response.getDetail()).doesNotContain("secret");
        assertThat(response.getDetail()).doesNotContain("key");
    }

    @Test
    @DisplayName("GlobalExceptionHandler should handle validation exceptions with appropriate context")
    void globalExceptionHandler_shouldHandleValidationExceptions() {
        // Given
        ValidationException exception = new ValidationException("Invalid email format");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleValidation(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Validation Failed");
        assertThat(response.getDetail()).contains("Invalid email format");
    }

    @Test
    @DisplayName("GlobalExceptionHandler should handle null exception messages gracefully")
    void globalExceptionHandler_shouldHandleNullExceptionMessages() {
        // Given
        RuntimeException exception = new RuntimeException();

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleRuntimeException(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getDetail()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("Error responses should include timestamp for debugging context")
    void errorResponses_shouldIncludeTimestamp() {
        // Given
        ResourceNotFoundException exception = new ResourceNotFoundException("Test error");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleResourceNotFound(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getProperties().get("timestamp")).isNotNull();
        assertThat(response.getProperties().get("timestamp")).isEqualTo(Instant.parse("2024-01-01T12:00:00Z"));
    }

    @Test
    @DisplayName("RuntimeException should not expose internal implementation details")
    void runtimeException_shouldNotExposeInternalDetails() {
        // Given
        RuntimeException exception = new RuntimeException("Internal database connection failed");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleRuntimeException(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        // RuntimeException returns generic message for security
        assertThat(response.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(response.getDetail()).doesNotContain("database");
        assertThat(response.getDetail()).doesNotContain("connection");
        assertThat(response.getDetail()).doesNotContain("Internal");
    }

    @Test
    @DisplayName("Validation should provide meaningful error messages")
    void validation_shouldProvideMeaningfulErrorMessages() {
        // Given
        ValidationException exception = new ValidationException("Email address is required");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleValidation(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getDetail()).contains("Email address is required");
        assertThat(response.getTitle()).isEqualTo("Validation Failed");
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Error handling should be consistent across different exception types")
    void errorHandling_shouldBeConsistentAcrossExceptionTypes() {
        // Given
        ResourceNotFoundException notFoundException = new ResourceNotFoundException("Resource not found");
        ValidationException validationException = new ValidationException("Validation failed");
        RuntimeException runtimeException = new RuntimeException("Unexpected error");

        // When
        ResponseEntity<ProblemDetail> notFoundResponseEntity = globalExceptionHandler.handleResourceNotFound(notFoundException, httpServletRequest);
        ResponseEntity<ProblemDetail> validationResponseEntity = globalExceptionHandler.handleValidation(validationException, httpServletRequest);
        ResponseEntity<ProblemDetail> runtimeResponseEntity = globalExceptionHandler.handleRuntimeException(runtimeException, httpServletRequest);

        ProblemDetail notFoundResponse = notFoundResponseEntity.getBody();
        ProblemDetail validationResponse = validationResponseEntity.getBody();
        ProblemDetail runtimeResponse = runtimeResponseEntity.getBody();

        // Then
        // All responses should have consistent structure
        assertThat(notFoundResponse).isNotNull();
        assertThat(validationResponse).isNotNull();
        assertThat(runtimeResponse).isNotNull();
        
        assertThat(notFoundResponse.getProperties().get("timestamp")).isNotNull();
        assertThat(validationResponse.getProperties().get("timestamp")).isNotNull();
        assertThat(runtimeResponse.getProperties().get("timestamp")).isNotNull();
        
        assertThat(notFoundResponse.getDetail()).isNotNull();
        assertThat(validationResponse.getDetail()).isNotNull();
        assertThat(runtimeResponse.getDetail()).isNotNull();
        
        // Status codes should be appropriate
        assertThat(notFoundResponse.getStatus()).isEqualTo(404);
        assertThat(validationResponse.getStatus()).isEqualTo(400);
        assertThat(runtimeResponse.getStatus()).isEqualTo(500);  // RuntimeException is 500
    }

    @Test
    @DisplayName("Error messages should be user-friendly and actionable")
    void errorMessages_shouldBeUserFriendlyAndActionable() {
        // Given
        ValidationException exception = new ValidationException("Please provide a valid email address");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleValidation(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getDetail()).contains("Please provide a valid email address");
        assertThat(response.getTitle()).isEqualTo("Validation Failed");
        
        // Error should be actionable (tells user what to do)
        assertThat(response.getDetail()).contains("provide");
        assertThat(response.getDetail()).contains("valid");
    }

    @Test
    @DisplayName("DataIntegrityViolationException should be sanitized for user-friendly messages")
    void dataIntegrityViolation_shouldBeSanitizedForUserFriendlyMessages() {
        // Given
        org.springframework.dao.DataIntegrityViolationException exception = 
            new org.springframework.dao.DataIntegrityViolationException("Duplicate entry 'test@example.com' for key 'users.UK_email'");

        // When
        ResponseEntity<ProblemDetail> responseEntity = globalExceptionHandler.handleDataIntegrity(exception, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getTitle()).isEqualTo("Data Conflict");
        // Should provide user-friendly message about email conflict
        assertThat(response.getDetail()).containsAnyOf(
            "email address",
            "duplicate entry",
            "already exists"
        );
        // Should not expose technical database details
        assertThat(response.getDetail()).doesNotContain("UK_email");
        assertThat(response.getDetail()).doesNotContain("users.");
    }
}
