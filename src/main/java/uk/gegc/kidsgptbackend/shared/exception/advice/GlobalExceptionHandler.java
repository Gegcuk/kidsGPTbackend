package uk.gegc.kidsgptbackend.shared.exception.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gegc.kidsgptbackend.shared.api.problem.ErrorTypes;
import uk.gegc.kidsgptbackend.shared.api.problem.ProblemDetailBuilder;
import uk.gegc.kidsgptbackend.shared.exception.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Global exception handler for consistent RFC 9457 error responses.
 * 
 * <p>This handler catches all exceptions thrown by the application and converts
 * them to standardized {@link ProblemDetail} responses following RFC 9457.</p>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Consistent error structure using {@link ProblemDetailBuilder}</li>
 *   <li>Centralized error types from {@link ErrorTypes}</li>
 *   <li>Database error sanitization to prevent information disclosure</li>
 *   <li>Clock injection for consistent timestamps</li>
 *   <li>Comprehensive logging for debugging</li>
 * </ul>
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final Clock clock;

    // ==================== Resource Errors ====================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.NOT_FOUND,
                ErrorTypes.RESOURCE_NOT_FOUND,
                "Resource Not Found",
                ex.getMessage(),
                request,
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // ==================== Validation Errors ====================

    @ExceptionHandler({
            jakarta.validation.ValidationException.class,
            uk.gegc.kidsgptbackend.shared.exception.ValidationException.class,
            ApiError.class
    })
    public ResponseEntity<ProblemDetail> handleValidation(RuntimeException ex, HttpServletRequest request) {
        String sanitizedMessage = sanitizeErrorMessage(ex.getMessage());
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.BAD_REQUEST,
                ErrorTypes.VALIDATION_FAILED,
                "Validation Failed",
                sanitizedMessage,
                request,
                Instant.now(clock)
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedOperation(UnsupportedOperationException ex, HttpServletRequest request) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Operation not supported";
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.BAD_REQUEST,
                ErrorTypes.UNSUPPORTED_OPERATION,
                "Unsupported Operation",
                msg,
                request,
                Instant.now(clock)
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.BAD_REQUEST,
                ErrorTypes.INVALID_ARGUMENT,
                "Invalid Argument",
                ex.getMessage(),
                request,
                Instant.now(clock)
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorTypes.ILLEGAL_STATE,
                "Illegal State",
                ex.getMessage(),
                request,
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.BAD_REQUEST,
                ErrorTypes.CONSTRAINT_VIOLATION,
                "Constraint Violation",
                "One or more validation constraints were violated",
                request,
                Instant.now(clock)
        );
        List<ViolationDetail> violations = ex.getConstraintViolations().stream()
                .map(this::toViolationDetail)
                .toList();
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String param = ex.getName();
        Class<?> type = ex.getRequiredType();
        String requiredType = type != null ? type.getSimpleName() : "unknown";
        String msg = String.format("Invalid value for parameter '%s'. Expected type: %s.", param, requiredType);
        
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.BAD_REQUEST,
                ErrorTypes.TYPE_MISMATCH,
                "Type Mismatch",
                msg,
                request,
                Instant.now(clock)
        );
        problem.setProperty("parameter", param);
        problem.setProperty("expectedType", requiredType);
        problem.setProperty("providedValue", ex.getValue());
        return ResponseEntity.badRequest().body(problem);
    }

    // ==================== Content/Moderation Errors ====================

    @ExceptionHandler(ConversationFormatException.class)
    public ResponseEntity<ProblemDetail> handleConversationFormat(ConversationFormatException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.BAD_REQUEST,
                ErrorTypes.CONVERSATION_FORMAT_ERROR,
                "Invalid Conversation Format",
                ex.getMessage(),
                request,
                Instant.now(clock)
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ModerationServiceException.class)
    public ResponseEntity<ProblemDetail> handleModerationUnavailable(ModerationServiceException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorTypes.MODERATION_FAILED,
                "Moderation Service Unavailable",
                ex.getMessage(),
                request,
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    // ==================== Security/Authentication Errors ====================

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.UNAUTHORIZED,
                ErrorTypes.UNAUTHORIZED,
                "Unauthorized",
                ex.getMessage(),
                request,
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler({
            AuthenticationException.class,
            BadCredentialsException.class,
            AuthenticationCredentialsNotFoundException.class,
            InsufficientAuthenticationException.class
    })
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.UNAUTHORIZED,
                ErrorTypes.AUTHENTICATION_FAILED,
                "Authentication Failed",
                ex.getMessage() != null ? ex.getMessage() : "Authentication failed",
                request,
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ProblemDetail> handleAccessDenied(Exception ex, HttpServletRequest request) {
        String detail = ex.getMessage() != null ? ex.getMessage() : "You do not have permission to access this resource";
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.FORBIDDEN,
                ErrorTypes.ACCESS_DENIED,
                "Access Denied",
                detail,
                request,
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    // ==================== User/Credential Errors ====================

    @ExceptionHandler(CredentialUpdateException.class)
    public ResponseEntity<ProblemDetail> handleCredentialUpdate(CredentialUpdateException ex, HttpServletRequest request) {
        // Check if it's an email conflict error
        if (ex.getMessage() != null && ex.getMessage().contains("Email already in use")) {
            ProblemDetail problem = ProblemDetailBuilder.create(
                    HttpStatus.CONFLICT,
                    ErrorTypes.EMAIL_ALREADY_EXISTS,
                    "Email Already Exists",
                    ex.getMessage(),
                    request,
                    Instant.now(clock)
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }
        
        // Default to Bad Request for other credential update errors
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.BAD_REQUEST,
                ErrorTypes.CREDENTIAL_UPDATE_FAILED,
                "Credential Update Failed",
                ex.getMessage() != null ? ex.getMessage() : "Failed to update credentials",
                request,
                Instant.now(clock)
        );
        return ResponseEntity.badRequest().body(problem);
    }

    // ==================== State/Data Errors ====================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Log the full technical error for debugging
        log.error("Data integrity violation: {}", ex.getMostSpecificCause().getMessage(), ex);
        
        // Sanitize the error message for users
        String userFriendlyMessage = sanitizeDatabaseError(ex);
        
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.CONFLICT,
                ErrorTypes.DATA_INTEGRITY_VIOLATION,
                "Data Conflict",
                userFriendlyMessage,
                request,
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.CONFLICT,
                ErrorTypes.OPTIMISTIC_LOCK_CONFLICT,
                "Conflict",
                "Resource has been modified by another process. Please refresh and try again.",
                request,
                Instant.now(clock)
        );
        problem.setProperty("errorCode", "VERSION_CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // ==================== Rate Limiting ====================

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorTypes.RATE_LIMIT_EXCEEDED,
                "Rate Limit Exceeded",
                ex.getMessage(),
                request,
                Instant.now(clock)
        );
        // TODO: Add retryAfterSeconds when RateLimitException supports it
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
    }

    // ==================== ResponseStatusException ====================

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        
        // Map specific status codes to specific error types
        var errorType = switch (status) {
            case UNAUTHORIZED -> ErrorTypes.UNAUTHORIZED;
            case FORBIDDEN -> ErrorTypes.ACCESS_DENIED;
            case NOT_FOUND -> ErrorTypes.RESOURCE_NOT_FOUND;
            case CONFLICT -> ErrorTypes.DATA_CONFLICT;
            case UNPROCESSABLE_ENTITY -> ErrorTypes.ILLEGAL_STATE;
            case BAD_REQUEST -> ErrorTypes.VALIDATION_FAILED;
            default -> ErrorTypes.GENERIC_ERROR;
        };
        
        ProblemDetail problem = ProblemDetailBuilder.create(
                status,
                errorType,
                status.getReasonPhrase(),
                reason,
                request,
                Instant.now(clock)
        );
        return ResponseEntity.status(status).body(problem);
    }

    // ==================== Generic Exception Handler ====================

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.error("RuntimeException occurred: {}", ex.getMessage(), ex);
        
        // Handle special runtime exception cases
        if ("Access denied".equalsIgnoreCase(ex.getMessage())) {
            ProblemDetail problem = ProblemDetailBuilder.create(
                    HttpStatus.FORBIDDEN,
                    ErrorTypes.ACCESS_DENIED,
                    "Access Denied",
                    ex.getMessage(),
                    request,
                    Instant.now(clock)
            );
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
        }
        
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorTypes.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                request,
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllOthers(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorTypes.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                request,
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    // ==================== Override Spring's Default Handlers ====================

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        String msg = ex.getMostSpecificCause() != null 
                ? ex.getMostSpecificCause().getMessage() 
                : "Request body is malformed or cannot be read";
        
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.BAD_REQUEST,
                ErrorTypes.MALFORMED_JSON,
                "Malformed JSON",
                "Request body is malformed or cannot be read",
                request,
                Instant.now(clock)
        );
        problem.setProperty("parseError", msg);
        return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        List<FieldValidationError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage(), error.getRejectedValue()))
                .toList();
        
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.BAD_REQUEST,
                ErrorTypes.VALIDATION_FAILED,
                "Validation Failed",
                "Validation failed for one or more fields",
                request,
                Instant.now(clock)
        );
        problem.setProperty("fieldErrors", fieldErrors);
        return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.BAD_REQUEST,
                ErrorTypes.INVALID_ARGUMENT,
                "Missing Parameter",
                message,
                request,
                Instant.now(clock)
        );
        problem.setProperty("parameter", ex.getParameterName());
        return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            @NonNull HttpRequestMethodNotSupportedException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        String supportedMethods = ex.getSupportedHttpMethods() != null 
                ? ex.getSupportedHttpMethods().toString() 
                : "unknown";
        String message = String.format("Method '%s' is not supported. Supported methods: %s",
                ex.getMethod(), supportedMethods);
        
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorTypes.UNSUPPORTED_OPERATION,
                "Method Not Allowed",
                message,
                request,
                Instant.now(clock)
        );
        problem.setProperty("method", ex.getMethod());
        problem.setProperty("supportedMethods", supportedMethods);
        return new ResponseEntity<>(problem, headers, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            @NonNull HttpMediaTypeNotSupportedException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        String supportedTypes = ex.getSupportedMediaTypes() != null 
                ? ex.getSupportedMediaTypes().toString() 
                : "unknown";
        String message = String.format("Media type '%s' is not supported. Supported types: %s",
                ex.getContentType(), supportedTypes);
        
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorTypes.UNSUPPORTED_OPERATION,
                "Unsupported Media Type",
                message,
                request,
                Instant.now(clock)
        );
        problem.setProperty("contentType", ex.getContentType() != null ? ex.getContentType().toString() : "unknown");
        problem.setProperty("supportedTypes", supportedTypes);
        return new ResponseEntity<>(problem, headers, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            @NonNull NoHandlerFoundException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        String message = String.format("No handler found for %s %s", ex.getHttpMethod(), ex.getRequestURL());
        ProblemDetail problem = ProblemDetailBuilder.create(
                HttpStatus.NOT_FOUND,
                ErrorTypes.RESOURCE_NOT_FOUND,
                "Not Found",
                message,
                request,
                Instant.now(clock)
        );
        problem.setProperty("method", ex.getHttpMethod());
        problem.setProperty("requestURL", ex.getRequestURL());
        return new ResponseEntity<>(problem, headers, HttpStatus.NOT_FOUND);
    }

    // ==================== Helper Methods ====================

    /**
     * Sanitizes error messages to avoid exposing internal implementation details.
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Invalid request";
        }
        
        String[] sensitiveTerms = {
            "database", "connection", "sql", "jdbc", "hibernate", "jpa",
            "internal", "stack", "trace", "exception", "timeout", "deadlock", 
            "constraint", "violation", "duplicate"
        };
        
        String lowerMessage = message.toLowerCase();
        for (String term : sensitiveTerms) {
            if (lowerMessage.contains(term)) {
                return "Invalid request";
            }
        }
        
        return message;
    }

    /**
     * Sanitizes database error messages to avoid exposing internal database details.
     * Provides user-friendly messages without revealing schema, constraints, or binary data.
     */
    private String sanitizeDatabaseError(DataIntegrityViolationException ex) {
        String technicalMessage = ex.getMostSpecificCause().getMessage();
        
        if (technicalMessage == null) {
            return "A data conflict occurred. Please check your input and try again.";
        }
        
        String lowerMessage = technicalMessage.toLowerCase();
        
        // Handle duplicate key violations
        if (lowerMessage.contains("duplicate entry") || lowerMessage.contains("duplicate key")) {
            // Try to extract a user-friendly field name from the constraint
            String fieldHint = extractFieldFromConstraint(technicalMessage);
            if (fieldHint != null) {
                return "A " + fieldHint + " with this value already exists. Please use a different value.";
            }
            return "This record already exists. Please check for duplicates.";
        }
        
        // Handle foreign key violations
        if (lowerMessage.contains("foreign key constraint") || lowerMessage.contains("cannot delete or update a parent row")) {
            return "This record cannot be modified because it is referenced by other data.";
        }
        
        // Handle null constraint violations
        if (lowerMessage.contains("cannot be null") || lowerMessage.contains("not-null")) {
            return "A required field is missing. Please provide all required information.";
        }
        
        // Handle check constraint violations
        if (lowerMessage.contains("check constraint")) {
            return "The data does not meet validation requirements. Please check your input.";
        }
        
        // Generic fallback
        return "A data conflict occurred. Please check your input and try again.";
    }

    /**
     * Attempts to extract a user-friendly field name from a database constraint name.
     * Returns null if no recognizable pattern is found.
     */
    private String extractFieldFromConstraint(String technicalMessage) {
        try {
            // Pattern: for key 'table.UK_fieldname' or for key 'UK_fieldname'
            if (technicalMessage.contains("for key '")) {
                int startIdx = technicalMessage.indexOf("for key '") + 9;
                int endIdx = technicalMessage.indexOf("'", startIdx);
                if (endIdx > startIdx) {
                    String constraintName = technicalMessage.substring(startIdx, endIdx);
                    
                    // Remove table prefix if present (e.g., "users.UK_email" -> "UK_email")
                    if (constraintName.contains(".")) {
                        constraintName = constraintName.substring(constraintName.lastIndexOf(".") + 1);
                    }
                    
                    // Map common constraint patterns to user-friendly names
                    String lowerConstraint = constraintName.toLowerCase();
                    if (lowerConstraint.contains("email")) return "email address";
                    if (lowerConstraint.contains("username")) return "username";
                    if (lowerConstraint.contains("title")) return "title";
                    if (lowerConstraint.contains("name")) return "name";
                    if (lowerConstraint.contains("slug")) return "slug";
                    
                    // Generic: "record"
                    return "record";
                }
            }
        } catch (Exception e) {
            // If parsing fails, return null to use generic message
            log.debug("Failed to extract field from constraint: {}", e.getMessage());
        }
        return null;
    }

    private ViolationDetail toViolationDetail(ConstraintViolation<?> violation) {
        return new ViolationDetail(
                violation.getPropertyPath().toString(),
                violation.getMessage(),
                violation.getInvalidValue()
        );
    }

    // ==================== Internal DTOs ====================

    private record ViolationDetail(String field, String message, Object invalidValue) {
    }

    private record FieldValidationError(String field, String message, Object rejectedValue) {
    }
}
