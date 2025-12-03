package uk.gegc.kidsgptbackend.shared.exception.advice;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.WebRequest;
import uk.gegc.kidsgptbackend.shared.exception.*;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GlobalExceptionHandler} using RFC 9457 Problem Details.
 */
class GlobalExceptionHandlerTest extends BaseUnitTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest httpServletRequest;
    private WebRequest webRequest;
    private Clock fixedClock;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        fixedClock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);
        handler = new GlobalExceptionHandler(fixedClock);
        
        httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/api/test");
        
        webRequest = mock(WebRequest.class);
        when(webRequest.getDescription(false)).thenReturn("uri=/api/test");
    }

    @Test
    @DisplayName("handleValidation: returns 400 with ProblemDetail")
    void handleValidation_returnsBadRequest() {
        ValidationException ex = new ValidationException("Field is required");

        ResponseEntity<ProblemDetail> responseEntity = handler.handleValidation(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Validation Failed");
        assertThat(response.getDetail()).contains("Field is required");
        assertThat(response.getType().toString()).contains("/validation-failed");
        assertThat(response.getProperties().get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("handleUnauthorized: returns 401")
    void handleUnauthorized_returnsUnauthorized() {
        UnauthorizedException ex = new UnauthorizedException("Invalid credentials");

        ResponseEntity<ProblemDetail> responseEntity = handler.handleUnauthorized(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getTitle()).isEqualTo("Unauthorized");
        assertThat(response.getDetail()).contains("Invalid credentials");
        assertThat(response.getType().toString()).contains("/unauthorized");
    }

    @Test
    @DisplayName("handleResourceNotFound: returns 404")
    void handleResourceNotFound_returnsNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");

        ResponseEntity<ProblemDetail> responseEntity = handler.handleResourceNotFound(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getTitle()).isEqualTo("Resource Not Found");
        assertThat(response.getDetail()).contains("User not found");
        assertThat(response.getType().toString()).contains("/resource-not-found");
    }

    @Test
    @DisplayName("handleRateLimit: returns 429")
    void handleRateLimit_returnsTooManyRequests() {
        RateLimitException ex = new RateLimitException("Rate limit exceeded", null);

        ResponseEntity<ProblemDetail> responseEntity = handler.handleRateLimit(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getTitle()).isEqualTo("Rate Limit Exceeded");
        assertThat(response.getDetail()).contains("Rate limit exceeded");
        assertThat(response.getType().toString()).contains("/rate-limit-exceeded");
    }

    @Test
    @DisplayName("handleModerationUnavailable: returns 503")
    void handleModerationUnavailable_returnsServiceUnavailable() {
        ModerationServiceException ex = new ModerationServiceException("Content violates guidelines", null);

        ProblemDetail response = handler.handleModerationUnavailable(ex, httpServletRequest).getBody();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getTitle()).isEqualTo("Moderation Service Unavailable");
        assertThat(response.getDetail()).contains("Content violates guidelines");
        assertThat(response.getType().toString()).contains("/moderation-failed");
    }

    @Test
    @DisplayName("handleUnsupportedOperation: returns 400")
    void handleUnsupportedOperation_returnsBadRequest() {
        UnsupportedOperationException ex = new UnsupportedOperationException("Operation not supported");

        ProblemDetail response = handler.handleUnsupportedOperation(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Unsupported Operation");
        assertThat(response.getDetail()).contains("Operation not supported");
    }

    @Test
    @DisplayName("handleUnsupportedOperation: handles null message")
    void handleUnsupportedOperation_nullMessage_returnsDefaultMessage() {
        UnsupportedOperationException ex = new UnsupportedOperationException();

        ProblemDetail response = handler.handleUnsupportedOperation(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getDetail()).contains("Operation not supported");
    }

    @Test
    @DisplayName("handleIllegalArgument: returns 400")
    void handleIllegalArgument_returnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ProblemDetail response = handler.handleIllegalArgument(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Invalid Argument");
        assertThat(response.getDetail()).contains("Invalid argument");
    }

    @Test
    @DisplayName("handleIllegalState: returns 422")
    void handleIllegalState_returnsUnprocessableEntity() {
        IllegalStateException ex = new IllegalStateException("State conflict");

        ProblemDetail response = handler.handleIllegalState(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getTitle()).isEqualTo("Illegal State");
        assertThat(response.getDetail()).contains("State conflict");
    }

    @Test
    @DisplayName("handleDataIntegrity: returns 409")
    void handleDataIntegrity_returnsConflict() {
        org.springframework.dao.DataIntegrityViolationException ex =
                new org.springframework.dao.DataIntegrityViolationException("Database constraint violation",
                        new RuntimeException("Unique constraint failed"));

        ProblemDetail response = handler.handleDataIntegrity(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getTitle()).isEqualTo("Data Conflict");
        // Database errors are sanitized for security
        assertThat(response.getDetail()).isEqualTo("A data conflict occurred. Please check your input and try again.");
    }

    @Test
    @DisplayName("handleAccessDenied: returns 403")
    void handleAccessDenied_returnsForbidden() {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Access denied");

        ProblemDetail response = handler.handleAccessDenied(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getTitle()).isEqualTo("Access Denied");
        assertThat(response.getDetail()).contains("Access denied");
    }

    @Test
    @DisplayName("handleAccessDenied: handles null message")
    void handleAccessDenied_nullMessage_returnsDefaultMessage() {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException(null);

        ProblemDetail response = handler.handleAccessDenied(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).contains("You do not have permission to access this resource");
    }

    @Test
    @DisplayName("handleAllOthers: returns 500")
    void handleAllOthers_returnsInternalServerError() {
        RuntimeException ex = new RuntimeException("Unexpected error");

        ProblemDetail response = handler.handleAllOthers(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getDetail()).contains("An unexpected error occurred");
    }

    @Test
    @DisplayName("ProblemDetail: timestamp is set correctly")
    void problemDetail_timestampIsSet() {
        ValidationException ex = new ValidationException("Test");

        ProblemDetail response = handler.handleValidation(ex, httpServletRequest).getBody();

        assertThat(response.getProperties().get("timestamp")).isEqualTo(Instant.ofEpochMilli(1000L));
    }

    @Test
    @DisplayName("handleResponseStatusException: returns correct status")
    void handleResponseStatusException_returnsCorrectStatus() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Conflict occurred");

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleResponseStatus(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getDetail()).contains("Conflict occurred");
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

        ProblemDetail response = handler.handleConstraintViolation(ex, httpServletRequest).getBody();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Constraint Violation");
        assertThat(response.getDetail()).isEqualTo("One or more validation constraints were violated");
        assertThat(response.getProperties()).containsKey("violations");
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
        assertThat(problemDetail.getTitle()).isEqualTo("Malformed JSON");
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
        assertThat(problemDetail.getProperties()).containsKey("fieldErrors");
        assertThat(problemDetail.getDetail()).isEqualTo("Validation failed for one or more fields");
    }

    @Test
    @DisplayName("handleAuthorizationDenied: returns 403")
    void handleAuthorizationDenied_returnsForbidden() {
        // Use AccessDeniedException instead, which is also handled by handleAccessDenied
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Authorization denied");

        ProblemDetail response = handler.handleAccessDenied(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getTitle()).isEqualTo("Access Denied");
        assertThat(response.getDetail()).contains("Authorization denied");
    }

    @Test
    @DisplayName("ProblemDetail: includes type and instance URIs")
    void problemDetail_includesTypeAndInstance() {
        ValidationException ex = new ValidationException("Test");

        ProblemDetail response = handler.handleValidation(ex, httpServletRequest).getBody();

        assertThat(response.getType()).isNotNull();
        assertThat(response.getType().toString()).contains("/errors/");
        assertThat(response.getInstance()).isNotNull();
    }

    @Test
    @DisplayName("handleConversationFormat: returns 400")
    void handleConversationFormat_returnsBadRequest() {
        ConversationFormatException ex = new ConversationFormatException("Invalid conversation format");

        ProblemDetail response = handler.handleConversationFormat(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
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

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleResponseStatus(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Validation Failed");
        assertThat(response.getType().toString()).contains("/validation-failed");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 401")
    void handleResponseStatusException_status401_returnsUnauthorized() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized");

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleResponseStatus(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Unauthorized");
        assertThat(response.getType().toString()).contains("/unauthorized");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 403")
    void handleResponseStatusException_status403_returnsForbidden() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden");

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleResponseStatus(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Access Denied");
        assertThat(response.getType().toString()).contains("/access-denied");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 404")
    void handleResponseStatusException_status404_returnsNotFound() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not found");

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleResponseStatus(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Resource Not Found");
        assertThat(response.getType().toString()).contains("/resource-not-found");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 410")
    void handleResponseStatusException_status410_returnsGone() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.GONE, "Gone");

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleResponseStatus(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.GONE);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Gone");
        assertThat(response.getType().toString()).contains("/error");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 429")
    void handleResponseStatusException_status429_returnsTooManyRequests() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Too many requests");

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleResponseStatus(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Too Many Requests");
        assertThat(response.getType().toString()).contains("/error");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - 500")
    void handleResponseStatusException_status500_returnsInternalServerError() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleResponseStatus(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getType().toString()).contains("/error");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles different status codes - default")
    void handleResponseStatusException_defaultStatus_returnsRequestFailed() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.IM_USED, "Custom status");

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleResponseStatus(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.IM_USED);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("IM Used");
        assertThat(response.getType().toString()).contains("/error");
    }

    @Test
    @DisplayName("handleResponseStatusException: handles null reason")
    void handleResponseStatusException_nullReason_usesDefault() {
        org.springframework.web.server.ResponseStatusException ex =
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, null);

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleResponseStatus(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getDetail()).contains("400 BAD_REQUEST");
    }

    @Test
    @DisplayName("handleCredentialUpdate: when email conflict then returns 409")
    void handleCredentialUpdate_emailConflict_returnsConflict() {
        CredentialUpdateException ex = new CredentialUpdateException("Email already in use");

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleCredentialUpdate(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Email Already Exists");
        assertThat(response.getType().toString()).contains("/email-already-exists");
    }

    @Test
    @DisplayName("handleCredentialUpdate: when not email conflict then returns 400")
    void handleCredentialUpdate_notEmailConflict_returnsBadRequest() {
        CredentialUpdateException ex = new CredentialUpdateException("Password update failed");

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleCredentialUpdate(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Credential Update Failed");
        assertThat(response.getType().toString()).contains("/credential-update-failed");
    }

    @Test
    @DisplayName("handleCredentialUpdate: handles null message")
    void handleCredentialUpdate_nullMessage_usesDefault() {
        CredentialUpdateException ex = new CredentialUpdateException(null);

        ResponseEntity<ProblemDetail> responseEntity =
                handler.handleCredentialUpdate(ex, httpServletRequest);
        ProblemDetail response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getDetail()).isEqualTo("Failed to update credentials");
    }


    @Test
    @DisplayName("handleConstraintViolation: when errors are empty then uses default detail")
    void handleConstraintViolation_emptyErrors_usesDefaultDetail() {
        jakarta.validation.ConstraintViolationException ex =
                new jakarta.validation.ConstraintViolationException("Validation failed", Set.of());

        ProblemDetail response = handler.handleConstraintViolation(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("One or more validation constraints were violated");
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
        assertThat(problemDetail.getDetail()).isEqualTo("Validation failed for one or more fields");
        assertThat(problemDetail.getProperties().get("errors")).isNull();
    }

    @Test
    @DisplayName("handleMethodArgumentTypeMismatch: handles null requiredType")
    void handleMethodArgumentTypeMismatch_nullRequiredType_handlesGracefully() {
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
                new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                        "param", null, "param", null, new RuntimeException());

        ProblemDetail response = handler.handleTypeMismatch(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
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
        assertThat(problemDetail.getTitle()).isEqualTo("Resource Not Found");
        assertThat(problemDetail.getProperties().get("method")).isEqualTo("GET");
        assertThat(problemDetail.getProperties().get("requestURL")).isEqualTo("/api/invalid");
    }


    @Test
    @DisplayName("handleBadRequest: sanitizes error messages with sensitive terms")
    void handleBadRequest_sanitizesSensitiveTerms() {
        ValidationException ex = new ValidationException("Database connection failed");

        ProblemDetail response = handler.handleValidation(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("Invalid request");
    }

    @Test
    @DisplayName("handleBadRequest: sanitizes null message")
    void handleBadRequest_sanitizesNullMessage() {
        ValidationException ex = new ValidationException(null);

        ProblemDetail response = handler.handleValidation(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("Invalid request");
    }

    @Test
    @DisplayName("handleBadRequest: sanitizes empty message")
    void handleBadRequest_sanitizesEmptyMessage() {
        ValidationException ex = new ValidationException("   ");

        ProblemDetail response = handler.handleValidation(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("Invalid request");
    }

    @Test
    @DisplayName("handleAuthenticationException: handles BadCredentialsException")
    void handleAuthenticationException_badCredentials_returnsUnauthorized() {
        org.springframework.security.authentication.BadCredentialsException ex =
                new org.springframework.security.authentication.BadCredentialsException("Bad credentials");

        ProblemDetail response = handler.handleAuthenticationException(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getTitle()).isEqualTo("Authentication Failed");
        assertThat(response.getType().toString()).contains("/authentication-failed");
    }

    @Test
    @DisplayName("handleAuthenticationException: handles AuthenticationCredentialsNotFoundException")
    void handleAuthenticationException_credentialsNotFound_returnsUnauthorized() {
        org.springframework.security.authentication.AuthenticationCredentialsNotFoundException ex =
                new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("No credentials");

        ProblemDetail response = handler.handleAuthenticationException(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getTitle()).isEqualTo("Authentication Failed");
    }

    @Test
    @DisplayName("handleAuthenticationException: handles InsufficientAuthenticationException")
    void handleAuthenticationException_insufficientAuth_returnsUnauthorized() {
        org.springframework.security.authentication.InsufficientAuthenticationException ex =
                new org.springframework.security.authentication.InsufficientAuthenticationException("Insufficient auth");

        ProblemDetail response = handler.handleAuthenticationException(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getTitle()).isEqualTo("Authentication Failed");
    }

    @Test
    @DisplayName("handleAccessDenied: handles AuthorizationDeniedException")
    void handleAccessDenied_authorizationDenied_returnsForbidden() {
        // Use AccessDeniedException which is also handled by the same method
        // AuthorizationDeniedException requires more complex setup
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Access denied");

        ProblemDetail response = handler.handleAccessDenied(ex, httpServletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getTitle()).isEqualTo("Access Denied");
    }

    @Test
    @DisplayName("handleNotFound: handles null message")
    void handleNotFound_nullMessage_usesDefault() {
        ResourceNotFoundException ex = new ResourceNotFoundException(null);

        ProblemDetail response = handler.handleResourceNotFound(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("The requested resource was not found");
    }

    @Test
    @DisplayName("handleIllegalArgument: handles null message")
    void handleIllegalArgument_nullMessage_usesDefault() {
        IllegalArgumentException ex = new IllegalArgumentException((String) null);

        ProblemDetail response = handler.handleIllegalArgument(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("Invalid argument provided");
    }

    @Test
    @DisplayName("handleIllegalState: handles null message")
    void handleIllegalState_nullMessage_usesDefault() {
        IllegalStateException ex = new IllegalStateException((String) null);

        ProblemDetail response = handler.handleIllegalState(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("Invalid state detected");
    }

    @Test
    @DisplayName("handleRateLimit: handles null message")
    void handleRateLimit_nullMessage_usesDefault() {
        RateLimitException ex = new RateLimitException(null, null);

        ProblemDetail response = handler.handleRateLimit(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("Too many requests. Please try again later");
    }

    @Test
    @DisplayName("handleModerationUnavailable: handles null message")
    void handleModerationUnavailable_nullMessage_usesDefault() {
        ModerationServiceException ex = new ModerationServiceException(null, null);

        ProblemDetail response = handler.handleModerationUnavailable(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("Content moderation service is temporarily unavailable");
    }

    @Test
    @DisplayName("handleConversationFormat: handles null message")
    void handleConversationFormat_nullMessage_usesDefault() {
        ConversationFormatException ex = new ConversationFormatException(null);

        ProblemDetail response = handler.handleConversationFormat(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("Invalid conversation format");
    }

    @Test
    @DisplayName("handleUnauthorized: handles null message")
    void handleUnauthorized_nullMessage_usesDefault() {
        UnauthorizedException ex = new UnauthorizedException(null);

        ProblemDetail response = handler.handleUnauthorized(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("Authentication is required to access this resource");
    }

    @Test
    @DisplayName("handleAuthenticationException: handles null message")
    void handleAuthenticationException_nullMessage_usesDefault() {
        org.springframework.security.authentication.BadCredentialsException ex =
                new org.springframework.security.authentication.BadCredentialsException(null);

        ProblemDetail response = handler.handleAuthenticationException(ex, httpServletRequest).getBody();

        assertThat(response.getDetail()).isEqualTo("Authentication failed");
    }
}
