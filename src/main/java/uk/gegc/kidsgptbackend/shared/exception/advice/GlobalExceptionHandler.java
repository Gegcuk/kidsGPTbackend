package uk.gegc.kidsgptbackend.shared.exception.advice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gegc.kidsgptbackend.shared.exception.*;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String ERROR_TYPE_BASE = "/errors";
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired
    private Clock clock;

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage() != null ? exception.getMessage() : "Resource not found"
        );
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/not-found"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler({
            jakarta.validation.ValidationException.class,
            uk.gegc.kidsgptbackend.shared.exception.ValidationException.class,
            ApiError.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleBadRequest(RuntimeException ex, WebRequest request) {
        // Sanitize error message to avoid exposing internal details
        String sanitizedMessage = sanitizeErrorMessage(ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                sanitizedMessage
        );
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/bad-request"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleUnsupportedOperation(UnsupportedOperationException ex, WebRequest request) {
        ex.printStackTrace(); // DEBUG: print stack trace
        String msg = ex.getMessage() != null ? ex.getMessage() : "Operation not supported";
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
        problemDetail.setTitle("Unsupported Operation");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/unsupported-operation"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage() != null ? exception.getMessage() : "Invalid argument"
        );
        problemDetail.setTitle("Invalid Argument");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/invalid-argument"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleIllegalState(IllegalStateException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage() != null ? ex.getMessage() : "Invalid state"
        );
        problemDetail.setTitle("Conflict");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/conflict"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(ResponseStatusException ex, WebRequest request) {
        String reason = ex.getReason() != null ? ex.getReason() : "Unknown error";
        HttpStatusCode statusCode = ex.getStatusCode();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(statusCode, reason);
        
        // Set appropriate title based on status code
        String title = switch (statusCode.value()) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Resource Not Found";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            default -> "Request Failed";
        };
        problemDetail.setTitle(title);
        
        // Set appropriate type based on status code
        String typePath = switch (statusCode.value()) {
            case 400 -> "/bad-request";
            case 401 -> "/unauthorized";
            case 403 -> "/forbidden";
            case 404 -> "/not-found";
            case 409 -> "/conflict";
            case 410 -> "/gone";
            case 429 -> "/rate-limit";
            case 500 -> "/internal-server-error";
            default -> "/response-status";
        };
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + typePath));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return new ResponseEntity<>(problemDetail, statusCode);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Database constraint violation"
        );
        problemDetail.setTitle("Data Integrity Violation");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/data-integrity"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ProblemDetail handleRateLimit(RateLimitException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                ex.getMessage() != null ? ex.getMessage() : "Rate limit exceeded"
        );
        problemDetail.setTitle("Too Many Requests");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/rate-limit"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler(ModerationServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ProblemDetail handleModerationUnavailable(ModerationServiceException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage() != null ? ex.getMessage() : "Moderation service unavailable"
        );
        problemDetail.setTitle("Service Unavailable");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/service-unavailable"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler(ConversationFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleConversationFormat(ConversationFormatException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : "Invalid conversation message sequence"
        );
        problemDetail.setTitle("Invalid Conversation Format");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/conversation-format"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage() != null ? ex.getMessage() : "Authentication required"
        );
        problemDetail.setTitle("Unauthorized");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/unauthorized"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler(CredentialUpdateException.class)
    public ResponseEntity<ProblemDetail> handleCredentialUpdate(CredentialUpdateException ex, WebRequest request) {
        // Check if it's an email conflict error
        if (ex.getMessage() != null && ex.getMessage().contains("Email already in use")) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
            problemDetail.setTitle("Conflict");
            problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/credential-conflict"));
            problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
            problemDetail.setProperty("timestamp", Instant.now(clock));
            return new ResponseEntity<>(problemDetail, HttpStatus.CONFLICT);
        }
        
        // Default to Bad Request for other credential update errors
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : "Failed to update credentials"
        );
        problemDetail.setTitle("Credential Update Failed");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/credential-update-failed"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return new ResponseEntity<>(problemDetail, HttpStatus.BAD_REQUEST);
    }

    // Handle Spring Security authentication exceptions
    @ExceptionHandler({
            AuthenticationException.class,
            BadCredentialsException.class,
            AuthenticationCredentialsNotFoundException.class,
            InsufficientAuthenticationException.class
    })
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage() != null ? ex.getMessage() : "Authentication failed"
        );
        problemDetail.setTitle("Unauthorized");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/authentication-failed"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ProblemDetail handleAccessDenied(Exception ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                ex.getMessage() != null ? ex.getMessage() : "You do not have permission to access this resource"
        );
        problemDetail.setTitle("Access Denied");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/access-denied"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());

        String detail = errors.isEmpty() ? "Validation constraint violated" : errors.get(0);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                detail
        );
        problemDetail.setTitle("Validation Failed");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/validation-failed"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        // Store all validation errors in extensions
        if (!errors.isEmpty()) {
            problemDetail.setProperty("errors", errors);
        }
        return problemDetail;
    }

    // Handle method argument type mismatch
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        String message = String.format("Parameter '%s' should be of type %s",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problemDetail.setTitle("Invalid Parameter Type");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/invalid-parameter-type"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        problemDetail.setProperty("parameter", ex.getName());
        problemDetail.setProperty("expectedType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        return problemDetail;
    }



    // Override Spring's default handlers to ensure they return ProblemDetail (RFC 7807)

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String msg = ex.getMostSpecificCause() != null ?
                ex.getMostSpecificCause().getMessage() : "Malformed request body";
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
        problemDetail.setTitle("Malformed Request");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/malformed-request"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return new ResponseEntity<>(problemDetail, headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();

        String detail = fieldErrors.isEmpty() ? "Validation failed" : fieldErrors.get(0);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setTitle("Validation Failed");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/validation-failed"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        // Store all validation errors in extensions
        if (!fieldErrors.isEmpty()) {
            problemDetail.setProperty("errors", fieldErrors);
        }
        return new ResponseEntity<>(problemDetail, headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problemDetail.setTitle("Missing Parameter");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/missing-parameter"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        problemDetail.setProperty("parameter", ex.getParameterName());
        return new ResponseEntity<>(problemDetail, headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String supportedMethods = ex.getSupportedHttpMethods() != null ?
                ex.getSupportedHttpMethods().toString() : "unknown";
        String message = String.format("Method '%s' is not supported. Supported methods: %s",
                ex.getMethod(), supportedMethods);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, message);
        problemDetail.setTitle("Method Not Allowed");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/method-not-allowed"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        problemDetail.setProperty("method", ex.getMethod());
        problemDetail.setProperty("supportedMethods", supportedMethods);
        return new ResponseEntity<>(problemDetail, headers, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String supportedTypes = ex.getSupportedMediaTypes() != null ?
                ex.getSupportedMediaTypes().toString() : "unknown";
        String message = String.format("Media type '%s' is not supported. Supported types: %s",
                ex.getContentType(), supportedTypes);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
        problemDetail.setTitle("Unsupported Media Type");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/unsupported-media-type"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        problemDetail.setProperty("contentType", ex.getContentType() != null ? ex.getContentType().toString() : "unknown");
        problemDetail.setProperty("supportedTypes", supportedTypes);
        return new ResponseEntity<>(problemDetail, headers, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String message = String.format("No handler found for %s %s", ex.getHttpMethod(), ex.getRequestURL());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, message);
        problemDetail.setTitle("Not Found");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/not-found"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        problemDetail.setProperty("method", ex.getHttpMethod());
        problemDetail.setProperty("requestURL", ex.getRequestURL());
        return new ResponseEntity<>(problemDetail, headers, HttpStatus.NOT_FOUND);
    }

    // Catch-all handler - this should handle any exception not caught by more specific handlers
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ProblemDetail handleAllOthers(Exception ex, WebRequest request) {
        // Log the exception for debugging
        logger.error("Unhandled exception: ", ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + "/internal-server-error"));
        problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        problemDetail.setProperty("timestamp", Instant.now(clock));
        return problemDetail;
    }

    /**
     * Sanitizes error messages to avoid exposing internal implementation details.
     * Replaces sensitive internal details with generic messages.
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Invalid request";
        }
        
        // List of sensitive terms that should be replaced
        String[] sensitiveTerms = {
            "database", "connection", "sql", "jdbc", "hibernate", "jpa",
            "internal", "stack", "trace", "exception", "timeout", "deadlock", 
            "constraint", "violation", "duplicate"
        };
        
        String sanitized = message.toLowerCase();
        for (String term : sensitiveTerms) {
            if (sanitized.contains(term)) {
                return "Invalid request";
            }
        }
        
        return message;
    }

}
