package uk.gegc.kidsgptbackend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionClassesTest {

    @Test
    @DisplayName("ValidationException: constructor with message")
    void validationException_constructorWithMessage() {
        String message = "Validation failed";
        ValidationException ex = new ValidationException(message);

        assertThat(ex.getMessage()).isEqualTo(message);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ResourceNotFoundException: constructor with message")
    void resourceNotFoundException_constructorWithMessage() {
        String message = "Resource not found";
        ResourceNotFoundException ex = new ResourceNotFoundException(message);

        assertThat(ex.getMessage()).isEqualTo(message);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("RateLimitException: constructor with message and cause")
    void rateLimitException_constructorWithMessageAndCause() {
        String message = "Rate limit exceeded";
        Throwable cause = new RuntimeException("Too many requests");
        RateLimitException ex = new RateLimitException(message, cause);

        assertThat(ex.getMessage()).isEqualTo(message);
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("RateLimitException: constructor with null cause")
    void rateLimitException_constructorWithNullCause() {
        String message = "Rate limit exceeded";
        RateLimitException ex = new RateLimitException(message, null);

        assertThat(ex.getMessage()).isEqualTo(message);
        assertThat(ex.getCause()).isNull();
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("UnauthorizedException: constructor with message")
    void unauthorizedException_constructorWithMessage() {
        String message = "Unauthorized access";
        UnauthorizedException ex = new UnauthorizedException(message);

        assertThat(ex.getMessage()).isEqualTo(message);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ModerationServiceException: constructor with message and cause")
    void moderationServiceException_constructorWithMessageAndCause() {
        String message = "Content violates guidelines";
        Throwable cause = new RuntimeException("Moderation service error");
        ModerationServiceException ex = new ModerationServiceException(message, cause);

        assertThat(ex.getMessage()).isEqualTo(message);
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ModerationServiceException: constructor with null cause")
    void moderationServiceException_constructorWithNullCause() {
        String message = "Content violates guidelines";
        ModerationServiceException ex = new ModerationServiceException(message, null);

        assertThat(ex.getMessage()).isEqualTo(message);
        assertThat(ex.getCause()).isNull();
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ApiError: constructor with message")
    void apiError_constructorWithMessage() {
        String message = "API error occurred";
        ApiError ex = new ApiError(message);

        assertThat(ex.getMessage()).isEqualTo(message);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
} 