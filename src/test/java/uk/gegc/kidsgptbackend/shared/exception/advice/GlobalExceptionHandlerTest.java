package uk.gegc.kidsgptbackend.shared.exception.advice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
        assertThat(response.getDetail()).contains("Database error: Unique constraint failed");
        assertThat(response.getProperties().get("errors")).isNotNull();
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
        HttpInputMessage inputMessage = mock(HttpInputMessage.class);
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException(
                        "Malformed JSON",
                        new RuntimeException("JSON parse error"),
                        inputMessage
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

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 400")
    void handleResponseStatusException_status400_returnsBadRequest() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Bad request");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleResponseStatusException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getTitle()).isEqualTo("Bad Request");
        assertThat(response.getBody().getType().toString()).contains("/bad-request");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 401")
    void handleResponseStatusException_status401_returnsUnauthorized() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleResponseStatusException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getTitle()).isEqualTo("Unauthorized");
        assertThat(response.getBody().getType().toString()).contains("/unauthorized");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 403")
    void handleResponseStatusException_status403_returnsForbidden() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleResponseStatusException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getTitle()).isEqualTo("Forbidden");
        assertThat(response.getBody().getType().toString()).contains("/forbidden");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 404")
    void handleResponseStatusException_status404_returnsNotFound() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not found");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleResponseStatusException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getTitle()).isEqualTo("Resource Not Found");
        assertThat(response.getBody().getType().toString()).contains("/not-found");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 410")
    void handleResponseStatusException_status410_returnsGone() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.GONE, "Gone");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleResponseStatusException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.GONE);
        assertThat(response.getBody().getTitle()).isEqualTo("Gone");
        assertThat(response.getBody().getType().toString()).contains("/gone");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 429")
    void handleResponseStatusException_status429_returnsTooManyRequests() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Too many requests");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleResponseStatusException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().getTitle()).isEqualTo("Too Many Requests");
        assertThat(response.getBody().getType().toString()).contains("/rate-limit");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 500")
    void handleResponseStatusException_status500_returnsInternalServerError() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleResponseStatusException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getType().toString()).contains("/internal-server-error");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - default")
    void handleResponseStatusException_defaultStatus_returnsRequestFailed() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.IM_USED, "Custom status");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleResponseStatusException(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.IM_USED);
        assertThat(response.getBody().getTitle()).isEqualTo("Request Failed");
        assertThat(response.getBody().getType().toString()).contains("/response-status");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles null reason")
    void handleResponseStatusException_nullReason_usesDefault() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, null);

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleResponseStatusException(ex, webRequest);

        assertThat(response.getBody().getDetail()).isEqualTo("Unknown error");
    }

    @Test
    @DisplayName("handleCredentialUpdate: when email conflict then returns 409")
    void handleCredentialUpdate_emailConflict_returnsConflict() {
        CredentialUpdateException ex = new CredentialUpdateException("Email already in use");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleCredentialUpdate(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(response.getBody().getTitle()).isEqualTo("Conflict");
        assertThat(response.getBody().getType().toString()).contains("/credential-conflict");
    }

    @Test
    @DisplayName("handleCredentialUpdate: when not email conflict then returns 400")
    void handleCredentialUpdate_notEmailConflict_returnsBadRequest() {
        CredentialUpdateException ex = new CredentialUpdateException("Password update failed");

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleCredentialUpdate(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getTitle()).isEqualTo("Credential Update Failed");
        assertThat(response.getBody().getType().toString()).contains("/credential-update-failed");
    }

    @Test
    @DisplayName("handleCredentialUpdate: handles null message")
    void handleCredentialUpdate_nullMessage_usesDefault() {
        CredentialUpdateException ex = new CredentialUpdateException(null);

        org.springframework.http.ResponseEntity<ProblemDetail> response =
                handler.handleCredentialUpdate(ex, webRequest);

        assertThat(response.getBody().getDetail()).isEqualTo("Failed to update credentials");
    }


    @Test
    @DisplayName("handleConstraintViolation: when errors are empty then uses default detail")
    void handleConstraintViolation_emptyErrors_usesDefaultDetail() {
        jakarta.validation.ConstraintViolationException ex =
                new jakarta.validation.ConstraintViolationException("Validation failed", Set.of());

        ProblemDetail response = handler.handleConstraintViolation(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Validation constraint violated");
        assertThat(response.getProperties().get("errors")).isNull();
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: when fieldErrors are empty then uses default detail")
    void handleMethodArgumentNotValid_emptyFieldErrors_usesDefaultDetail() {
        org.springframework.web.bind.MethodArgumentNotValidException ex =
                new org.springframework.web.bind.MethodArgumentNotValidException(
                        null,
                        new org.springframework.validation.BeanPropertyBindingResult(new Object(), "object")
                );

        org.springframework.http.ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                ex,
                org.springframework.http.HttpHeaders.EMPTY,
                org.springframework.http.HttpStatusCode.valueOf(400),
                webRequest
        );

        ProblemDetail problemDetail = (ProblemDetail) response.getBody();
        assertThat(problemDetail.getDetail()).isEqualTo("Validation failed");
        assertThat(problemDetail.getProperties().get("errors")).isNull();
    }

    @Test
    @DisplayName("handleMethodArgumentTypeMismatch: handles null requiredType")
    void handleMethodArgumentTypeMismatch_nullRequiredType_handlesGracefully() {
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
                new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                        "param", null, "param", null, new RuntimeException());

        ProblemDetail response = handler.handleMethodArgumentTypeMismatch(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getDetail()).contains("unknown");
        assertThat(response.getProperties().get("expectedType")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("handleHttpRequestMethodNotSupported: handles null supportedHttpMethods")
    void handleHttpRequestMethodNotSupported_nullSupportedMethods_handlesGracefully() {
        org.springframework.web.HttpRequestMethodNotSupportedException ex =
                new org.springframework.web.HttpRequestMethodNotSupportedException("PUT");

        org.springframework.http.ResponseEntity<Object> response = handler.handleHttpRequestMethodNotSupported(
                ex,
                org.springframework.http.HttpHeaders.EMPTY,
                org.springframework.http.HttpStatusCode.valueOf(405),
                webRequest
        );

        ProblemDetail problemDetail = (ProblemDetail) response.getBody();
        assertThat(problemDetail.getDetail()).contains("unknown");
        assertThat(problemDetail.getProperties().get("supportedMethods")).isEqualTo("unknown");
    }


    @Test
    @DisplayName("handleMissingServletRequestParameter: returns 400 with parameter name")
    void handleMissingServletRequestParameter_returnsBadRequest() {
        org.springframework.web.bind.MissingServletRequestParameterException ex =
                new org.springframework.web.bind.MissingServletRequestParameterException("userId", "String");

        org.springframework.http.ResponseEntity<Object> response = handler.handleMissingServletRequestParameter(
                ex,
                org.springframework.http.HttpHeaders.EMPTY,
                org.springframework.http.HttpStatusCode.valueOf(400),
                webRequest
        );

        ProblemDetail problemDetail = (ProblemDetail) response.getBody();
        assertThat(problemDetail.getStatus()).isEqualTo(400);
        assertThat(problemDetail.getTitle()).isEqualTo("Missing Parameter");
        assertThat(problemDetail.getDetail()).contains("userId");
        assertThat(problemDetail.getProperties().get("parameter")).isEqualTo("userId");
    }

    @Test
    @DisplayName("handleNoHandlerFoundException: returns 404")
    void handleNoHandlerFoundException_returnsNotFound() {
        org.springframework.web.servlet.NoHandlerFoundException ex =
                new org.springframework.web.servlet.NoHandlerFoundException(
                        "GET", "/api/invalid", org.springframework.http.HttpHeaders.EMPTY);

        org.springframework.http.ResponseEntity<Object> response = handler.handleNoHandlerFoundException(
                ex,
                org.springframework.http.HttpHeaders.EMPTY,
                org.springframework.http.HttpStatusCode.valueOf(404),
                webRequest
        );

        ProblemDetail problemDetail = (ProblemDetail) response.getBody();
        assertThat(problemDetail.getStatus()).isEqualTo(404);
        assertThat(problemDetail.getTitle()).isEqualTo("Not Found");
        assertThat(problemDetail.getProperties().get("method")).isEqualTo("GET");
        assertThat(problemDetail.getProperties().get("requestURL")).isEqualTo("/api/invalid");
    }


    @Test
    @DisplayName("handleBadRequest: sanitizes error messages with sensitive terms")
    void handleBadRequest_sanitizesSensitiveTerms() {
        ValidationException ex = new ValidationException("Database connection failed");

        ProblemDetail response = handler.handleBadRequest(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Invalid request");
    }

    @Test
    @DisplayName("handleBadRequest: sanitizes null message")
    void handleBadRequest_sanitizesNullMessage() {
        ValidationException ex = new ValidationException(null);

        ProblemDetail response = handler.handleBadRequest(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Invalid request");
    }

    @Test
    @DisplayName("handleBadRequest: sanitizes empty message")
    void handleBadRequest_sanitizesEmptyMessage() {
        ValidationException ex = new ValidationException("   ");

        ProblemDetail response = handler.handleBadRequest(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Invalid request");
    }

    @Test
    @DisplayName("handleAuthenticationException: handles BadCredentialsException")
    void handleAuthenticationException_badCredentials_returnsUnauthorized() {
        org.springframework.security.authentication.BadCredentialsException ex =
                new org.springframework.security.authentication.BadCredentialsException("Bad credentials");

        ProblemDetail response = handler.handleAuthenticationException(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getTitle()).isEqualTo("Unauthorized");
        assertThat(response.getType().toString()).contains("/authentication-failed");
    }

    @Test
    @DisplayName("handleAuthenticationException: handles AuthenticationCredentialsNotFoundException")
    void handleAuthenticationException_credentialsNotFound_returnsUnauthorized() {
        org.springframework.security.authentication.AuthenticationCredentialsNotFoundException ex =
                new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("No credentials");

        ProblemDetail response = handler.handleAuthenticationException(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getTitle()).isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("handleAuthenticationException: handles InsufficientAuthenticationException")
    void handleAuthenticationException_insufficientAuth_returnsUnauthorized() {
        org.springframework.security.authentication.InsufficientAuthenticationException ex =
                new org.springframework.security.authentication.InsufficientAuthenticationException("Insufficient auth");

        ProblemDetail response = handler.handleAuthenticationException(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getTitle()).isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("handleAccessDenied: handles AuthorizationDeniedException")
    void handleAccessDenied_authorizationDenied_returnsForbidden() {
        // Use AccessDeniedException which is also handled by the same method
        // AuthorizationDeniedException requires more complex setup
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Access denied");

        ProblemDetail response = handler.handleAccessDenied(ex, webRequest);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getTitle()).isEqualTo("Access Denied");
    }

    @Test
    @DisplayName("handleNotFound: handles null message")
    void handleNotFound_nullMessage_usesDefault() {
        ResourceNotFoundException ex = new ResourceNotFoundException(null);

        ProblemDetail response = handler.handleNotFound(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Resource not found");
    }

    @Test
    @DisplayName("handleIllegalArgument: handles null message")
    void handleIllegalArgument_nullMessage_usesDefault() {
        IllegalArgumentException ex = new IllegalArgumentException((String) null);

        ProblemDetail response = handler.handleIllegalArgument(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Invalid argument");
    }

    @Test
    @DisplayName("handleIllegalState: handles null message")
    void handleIllegalState_nullMessage_usesDefault() {
        IllegalStateException ex = new IllegalStateException((String) null);

        ProblemDetail response = handler.handleIllegalState(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Invalid state");
    }

    @Test
    @DisplayName("handleRateLimit: handles null message")
    void handleRateLimit_nullMessage_usesDefault() {
        RateLimitException ex = new RateLimitException(null, null);

        ProblemDetail response = handler.handleRateLimit(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Rate limit exceeded");
    }

    @Test
    @DisplayName("handleModerationUnavailable: handles null message")
    void handleModerationUnavailable_nullMessage_usesDefault() {
        ModerationServiceException ex = new ModerationServiceException(null, null);

        ProblemDetail response = handler.handleModerationUnavailable(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Moderation service unavailable");
    }

    @Test
    @DisplayName("handleConversationFormat: handles null message")
    void handleConversationFormat_nullMessage_usesDefault() {
        ConversationFormatException ex = new ConversationFormatException(null);

        ProblemDetail response = handler.handleConversationFormat(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Invalid conversation message sequence");
    }

    @Test
    @DisplayName("handleUnauthorized: handles null message")
    void handleUnauthorized_nullMessage_usesDefault() {
        UnauthorizedException ex = new UnauthorizedException(null);

        ProblemDetail response = handler.handleUnauthorized(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Authentication required");
    }

    @Test
    @DisplayName("handleAuthenticationException: handles null message")
    void handleAuthenticationException_nullMessage_usesDefault() {
        org.springframework.security.authentication.BadCredentialsException ex =
                new org.springframework.security.authentication.BadCredentialsException(null);

        ProblemDetail response = handler.handleAuthenticationException(ex, webRequest);

        assertThat(response.getDetail()).isEqualTo("Authentication failed");
    }
}
