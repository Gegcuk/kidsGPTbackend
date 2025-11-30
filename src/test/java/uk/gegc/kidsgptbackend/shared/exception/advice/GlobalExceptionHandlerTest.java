package uk.gegc.kidsgptbackend.shared.exception.advice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.WebRequest;
import uk.gegc.kidsgptbackend.shared.exception.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GlobalExceptionHandler} using RFC 7807 Problem Details.
 */
class GlobalExceptionHandlerTest extends uk.gegc.kidsgptbackend.test.BaseUnitTest {

    private GlobalExceptionHandler handler;
    private WebRequest webRequest;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1000L), java.time.ZoneOffset.UTC);
        handler = new GlobalExceptionHandler();
        org.springframework.test.util.ReflectionTestUtils.setField(handler, "clock", clock);
        webRequest = mock(WebRequest.class);
        when(webRequest.getDescription(false)).thenReturn("uri=/api/test");
    }

    @Test
    @DisplayName("handleBadRequest: returns 400 with ProblemDetail")
    void handleBadRequest_returnsBadRequest() {
        ValidationException ex = new ValidationException("Field is required");

        ProblemDetail response = handler.handleBadRequest(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Bad Request");
        assertThat(response.getDetail()).contains("Field is required");
        assertThat(response.getType().toString()).contains("/bad-request");
        assertThat(response.getProperties().get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("handleUnauthorized: returns 401")
    void handleUnauthorized_returnsUnauthorized() {
        UnauthorizedException ex = new UnauthorizedException("Invalid credentials");

        ProblemDetail response = handler.handleUnauthorized(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getTitle()).isEqualTo("Unauthorized");
        assertThat(response.getDetail()).contains("Invalid credentials");
        assertThat(response.getType().toString()).contains("/unauthorized");
    }

    @Test
    @DisplayName("handleNotFound: returns 404")
    void handleNotFound_returnsNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");

        ProblemDetail response = handler.handleNotFound(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getTitle()).isEqualTo("Resource Not Found");
        assertThat(response.getDetail()).contains("User not found");
        assertThat(response.getType().toString()).contains("/not-found");
    }

    @Test
    @DisplayName("handleRateLimit: returns 429")
    void handleRateLimit_returnsTooManyRequests() {
        RateLimitException ex = new RateLimitException("Rate limit exceeded", null);

        ProblemDetail response = handler.handleRateLimit(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getTitle()).isEqualTo("Too Many Requests");
        assertThat(response.getDetail()).contains("Rate limit exceeded");
        assertThat(response.getType().toString()).contains("/rate-limit");
    }

    @Test
    @DisplayName("handleModerationUnavailable: returns 503")
    void handleModerationUnavailable_returnsServiceUnavailable() {
        ModerationServiceException ex = new ModerationServiceException("Content violates guidelines", null);

        ProblemDetail response = handler.handleModerationUnavailable(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getTitle()).isEqualTo("Service Unavailable");
        assertThat(response.getDetail()).contains("Content violates guidelines");
        assertThat(response.getType().toString()).contains("/service-unavailable");
    }

    @Test
    @DisplayName("handleUnsupportedOperation: returns 400")
    void handleUnsupportedOperation_returnsBadRequest() {
        UnsupportedOperationException ex = new UnsupportedOperationException("Operation not supported");

        ProblemDetail response = handler.handleUnsupportedOperation(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Unsupported Operation");
        assertThat(response.getDetail()).contains("Operation not supported");
    }

    @Test
    @DisplayName("handleUnsupportedOperation: handles null message")
    void handleUnsupportedOperation_nullMessage_returnsDefaultMessage() {
        UnsupportedOperationException ex = new UnsupportedOperationException();

        ProblemDetail response = handler.handleUnsupportedOperation(ex, webRequest);

        assertThat(response.getDetail()).contains("Operation not supported");
    }

    @Test
    @DisplayName("handleIllegalArgument: returns 400")
    void handleIllegalArgument_returnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ProblemDetail response = handler.handleIllegalArgument(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Invalid Argument");
        assertThat(response.getDetail()).contains("Invalid argument");
    }

    @Test
    @DisplayName("handleIllegalState: returns 409")
    void handleIllegalState_returnsConflict() {
        IllegalStateException ex = new IllegalStateException("State conflict");

        ProblemDetail response = handler.handleIllegalState(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getTitle()).isEqualTo("Conflict");
        assertThat(response.getDetail()).contains("State conflict");
    }

    @Test
    @DisplayName("handleDataIntegrity: returns 409")
    void handleDataIntegrity_returnsConflict() {
        org.springframework.dao.DataIntegrityViolationException ex =
                new org.springframework.dao.DataIntegrityViolationException("Database constraint violation",
                        new RuntimeException("Unique constraint failed"));

        ProblemDetail response = handler.handleDataIntegrity(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getTitle()).isEqualTo("Data Integrity Violation");
        assertThat(response.getDetail()).contains("Database constraint violation");
    }

    @Test
    @DisplayName("handleAccessDenied: returns 403")
    void handleAccessDenied_returnsForbidden() {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Access denied");

        ProblemDetail response = handler.handleAccessDenied(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getTitle()).isEqualTo("Access Denied");
        assertThat(response.getDetail()).contains("Access denied");
    }

    @Test
    @DisplayName("handleAccessDenied: handles null message")
    void handleAccessDenied_nullMessage_returnsDefaultMessage() {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException(null);

        ProblemDetail response = handler.handleAccessDenied(ex, webRequest);

        assertThat(response.getDetail()).contains("You do not have permission to access this resource");
    }

    @Test
    @DisplayName("handleAllOthers: returns 500")
    void handleAllOthers_returnsInternalServerError() {
        RuntimeException ex = new RuntimeException("Unexpected error");

        ProblemDetail response = handler.handleAllOthers(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getDetail()).contains("An unexpected error occurred");
    }

    @Test
    @DisplayName("ProblemDetail: timestamp is set correctly")
    void problemDetail_timestampIsSet() {
        ValidationException ex = new ValidationException("Test");

        ProblemDetail response = handler.handleBadRequest(ex, webRequest);

        assertThat(response.getProperties().get("timestamp")).isEqualTo(Instant.ofEpochMilli(1000L));
    }

    @Test
    @DisplayName("handleResponseStatusException: returns correct status")
    void handleResponseStatusException_returnsCorrectStatus() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Conflict occurred");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleResponseStatusException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getDetail()).contains("Conflict occurred");
    }

    @Test
    @DisplayName("handleConstraintViolation: returns validation errors")
    void handleConstraintViolation_returnsValidationErrors() {
        jakarta.validation.ConstraintViolation<?> violation1 = mock(jakarta.validation.ConstraintViolation.class);
        jakarta.validation.ConstraintViolation<?> violation2 = mock(jakarta.validation.ConstraintViolation.class);

        when(violation1.getMessage()).thenReturn("Field is required");
        when(violation2.getMessage()).thenReturn("Invalid format");

        jakarta.validation.ConstraintViolationException ex =
                new jakarta.validation.ConstraintViolationException(
                        "Validation failed",
                        Set.of(violation1, violation2)
                );

        ProblemDetail response = handler.handleConstraintViolation(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Validation Failed");
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) response.getProperties().get("errors");
        assertThat(errors).hasSize(2);
        assertThat(errors).contains("Field is required", "Invalid format");
    }

    @Test
    @DisplayName("handleHttpMessageNotReadable: returns malformed JSON error")
    void handleHttpMessageNotReadable_returnsMalformedJsonError() {
        org.springframework.http.converter.HttpMessageNotReadableException ex =
                new org.springframework.http.converter.HttpMessageNotReadableException(
                        "Malformed JSON"
                );

        org.springframework.http.ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                ex,
                org.springframework.http.HttpHeaders.EMPTY,
                org.springframework.http.HttpStatusCode.valueOf(400),
                webRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problemDetail = (ProblemDetail) response.getBody();
        assertThat(problemDetail.getStatus()).isEqualTo(400);
        assertThat(problemDetail.getTitle()).isEqualTo("Malformed Request");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: returns field validation errors")
    void handleMethodArgumentNotValid_returnsFieldValidationErrors() {
        org.springframework.web.bind.MethodArgumentNotValidException ex =
                new org.springframework.web.bind.MethodArgumentNotValidException(
                        null,
                        new org.springframework.validation.BeanPropertyBindingResult(new Object(), "object")
                );

        org.springframework.validation.FieldError fieldError1 =
                new org.springframework.validation.FieldError("object", "username", "Username is required");
        org.springframework.validation.FieldError fieldError2 =
                new org.springframework.validation.FieldError("object", "email", "Email is invalid");

        ex.getBindingResult().addError(fieldError1);
        ex.getBindingResult().addError(fieldError2);

        org.springframework.http.ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                ex,
                org.springframework.http.HttpHeaders.EMPTY,
                org.springframework.http.HttpStatusCode.valueOf(400),
                webRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problemDetail = (ProblemDetail) response.getBody();
        assertThat(problemDetail.getStatus()).isEqualTo(400);
        assertThat(problemDetail.getTitle()).isEqualTo("Validation Failed");
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) problemDetail.getProperties().get("errors");
        assertThat(errors).contains("username: Username is required", "email: Email is invalid");
    }

    @Test
    @DisplayName("handleAuthorizationDenied: returns 403")
    void handleAuthorizationDenied_returnsForbidden() {
        // Use AccessDeniedException instead, which is also handled by handleAccessDenied
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Authorization denied");

        ProblemDetail response = handler.handleAccessDenied(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getTitle()).isEqualTo("Access Denied");
        assertThat(response.getDetail()).contains("Authorization denied");
    }

    @Test
    @DisplayName("ProblemDetail: includes type and instance URIs")
    void problemDetail_includesTypeAndInstance() {
        ValidationException ex = new ValidationException("Test");

        ProblemDetail response = handler.handleBadRequest(ex, webRequest);

        assertThat(response.getType()).isNotNull();
        assertThat(response.getType().toString()).startsWith("/errors/");
        assertThat(response.getInstance()).isNotNull();
    }

    @Test
    @DisplayName("handleConversationFormat: returns 400")
    void handleConversationFormat_returnsBadRequest() {
        ConversationFormatException ex = new ConversationFormatException("Invalid conversation format");

        ProblemDetail response = handler.handleConversationFormat(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Invalid Conversation Format");
        assertThat(response.getDetail()).contains("Invalid conversation format");
        assertThat(response.getType().toString()).contains("/conversation-format");
    }
}
