package uk.gegc.kidsgptbackend.controller.advice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.exception.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleBadRequest: returns 400 with validation errors")
    void handleBadRequest_returnsBadRequest() {
        ValidationException ex = new ValidationException("Field is required");

        GlobalExceptionHandler.ErrorResponse response = handler.handleBadRequest(ex);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo("Bad Request");
        assertThat(response.details()).contains("Field is required");
    }

    @Test
    @DisplayName("handleUnauthorized: returns 401")
    void handleUnauthorized_returnsUnauthorized() {
        UnauthorizedException ex = new UnauthorizedException("Invalid credentials");

        GlobalExceptionHandler.ErrorResponse response = handler.handleUnauthorized(ex);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.error()).isEqualTo("Unauthorized");
        assertThat(response.details()).contains("Invalid credentials");
    }

    @Test
    @DisplayName("handleNotFound: returns 404")
    void handleNotFound_returnsNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");

        GlobalExceptionHandler.ErrorResponse response = handler.handleNotFound(ex);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.error()).isEqualTo("Not Found");
        assertThat(response.details()).contains("User not found");
    }

    @Test
    @DisplayName("handleRateLimit: returns 429")
    void handleRateLimit_returnsTooManyRequests() {
        RateLimitException ex = new RateLimitException("Rate limit exceeded", null);

        GlobalExceptionHandler.ErrorResponse response = handler.handleRateLimit(ex);

        assertThat(response.status()).isEqualTo(429);
        assertThat(response.error()).isEqualTo("Too Many Requests");
        assertThat(response.details()).contains("Rate limit exceeded");
    }

    @Test
    @DisplayName("handleModerationUnavailable: returns 503")
    void handleModerationUnavailable_returnsServiceUnavailable() {
        ModerationServiceException ex = new ModerationServiceException("Content violates guidelines", null);

        GlobalExceptionHandler.ErrorResponse response = handler.handleModerationUnavailable(ex);

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.error()).isEqualTo("Service Unavailable");
        assertThat(response.details()).contains("Content violates guidelines");
    }

    @Test
    @DisplayName("handleUnsupportedOperation: returns 400")
    void handleUnsupportedOperation_returnsBadRequest() {
        UnsupportedOperationException ex = new UnsupportedOperationException("Operation not supported");

        GlobalExceptionHandler.ErrorResponse response = handler.handleUnsupportedOperation(ex);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo("Bad Request");
        assertThat(response.details()).contains("Operation not supported");
    }

    @Test
    @DisplayName("handleUnsupportedOperation: handles null message")
    void handleUnsupportedOperation_nullMessage_returnsDefaultMessage() {
        UnsupportedOperationException ex = new UnsupportedOperationException();

        GlobalExceptionHandler.ErrorResponse response = handler.handleUnsupportedOperation(ex);

        assertThat(response.details()).contains("Operation not supported");
    }

    @Test
    @DisplayName("handleIllegalArgument: returns 400")
    void handleIllegalArgument_returnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        GlobalExceptionHandler.ErrorResponse response = handler.handleIllegalArgument(ex);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo("Bad request");
        assertThat(response.details()).contains("Invalid argument");
    }

    @Test
    @DisplayName("handleIllegalState: returns 409")
    void handleIllegalState_returnsConflict() {
        IllegalStateException ex = new IllegalStateException("State conflict");

        GlobalExceptionHandler.ErrorResponse response = handler.handleIllegalState(ex);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.error()).isEqualTo("Conflict");
        assertThat(response.details()).contains("State conflict");
    }

    @Test
    @DisplayName("handleDataIntegrity: returns 409")
    void handleDataIntegrity_returnsConflict() {
        org.springframework.dao.DataIntegrityViolationException ex = 
            new org.springframework.dao.DataIntegrityViolationException("Database constraint violation", 
                new RuntimeException("Unique constraint failed"));

        GlobalExceptionHandler.ErrorResponse response = handler.handleDataIntegrity(ex);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.error()).isEqualTo("Conflict");
        assertThat(response.details()).contains("Database error: Unique constraint failed");
    }

    @Test
    @DisplayName("handleAccessDenied: returns 403")
    void handleAccessDenied_returnsForbidden() {
        org.springframework.security.access.AccessDeniedException ex = 
            new org.springframework.security.access.AccessDeniedException("Access denied");

        GlobalExceptionHandler.ErrorResponse response = handler.handleAccessDenied(ex);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.error()).isEqualTo("Access Denied");
        assertThat(response.details()).contains("Access denied");
    }

    @Test
    @DisplayName("handleAccessDenied: handles null message")
    void handleAccessDenied_nullMessage_returnsDefaultMessage() {
        org.springframework.security.access.AccessDeniedException ex = 
            new org.springframework.security.access.AccessDeniedException(null);

        GlobalExceptionHandler.ErrorResponse response = handler.handleAccessDenied(ex);

        assertThat(response.details()).contains("You do not have permission to access this resource");
    }

    @Test
    @DisplayName("handleAllOthers: returns 500")
    void handleAllOthers_returnsInternalServerError() {
        RuntimeException ex = new RuntimeException("Unexpected error");

        GlobalExceptionHandler.ErrorResponse response = handler.handleAllOthers(ex);

        assertThat(response.status()).isEqualTo(500);
        assertThat(response.error()).isEqualTo("Internal Server Error");
        assertThat(response.details()).contains("An unexpected error occurred");
    }

    @Test
    @DisplayName("ErrorResponse: timestamp is set correctly")
    void errorResponse_timestampIsSet() {
        ValidationException ex = new ValidationException("Test");

        GlobalExceptionHandler.ErrorResponse response = handler.handleBadRequest(ex);

        assertThat(response.timestamp()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(response.timestamp()).isAfter(LocalDateTime.now().minusSeconds(1));
    }
} 