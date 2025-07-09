package uk.gegc.kidsgptbackend.controller.advice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
import uk.gegc.kidsgptbackend.exception.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException exception) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                List.of(exception.getMessage())
        );
    }

    @ExceptionHandler({
            jakarta.validation.ValidationException.class,
            uk.gegc.kidsgptbackend.exception.ValidationException.class,
            ApiError.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(RuntimeException ex) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                List.of(ex.getMessage() != null ? ex.getMessage() : "Invalid request")
        );
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnsupportedOperation(UnsupportedOperationException ex) {
        ex.printStackTrace(); // DEBUG: print stack trace
        String msg = ex.getMessage() != null ? ex.getMessage() : "Operation not supported";
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                List.of(msg)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad request",
                List.of(exception.getMessage() != null ? exception.getMessage() : "Invalid argument")
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalState(IllegalStateException ex) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                List.of(ex.getMessage() != null ? ex.getMessage() : "Invalid state")
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "Unknown error";
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                ex.getStatusCode().value(),
                reason,
                List.of(reason)
        );
        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause() != null ?
                ex.getMostSpecificCause().getMessage() : "Database constraint violation";
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                List.of("Database error: " + cause)
        );
    }

    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ErrorResponse handleRateLimit(RateLimitException ex) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                List.of(ex.getMessage() != null ? ex.getMessage() : "Rate limit exceeded")
        );
    }

    @ExceptionHandler(ModerationServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleModerationUnavailable(ModerationServiceException ex) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service Unavailable",
                List.of(ex.getMessage() != null ? ex.getMessage() : "Moderation service unavailable")
        );
    }

    @ExceptionHandler(ConversationFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConversationFormat(ConversationFormatException ex) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Conversation Format",
                List.of(ex.getMessage() != null ? ex.getMessage() : "Invalid conversation message sequence")
        );
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleUnauthorized(UnauthorizedException ex) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                List.of(ex.getMessage() != null ? ex.getMessage() : "Authentication required")
        );
    }

    @ExceptionHandler(CredentialUpdateException.class)
    public ResponseEntity<ErrorResponse> handleCredentialUpdate(CredentialUpdateException ex) {
        // Check if it's an email conflict error
        if (ex.getMessage() != null && ex.getMessage().contains("Email already in use")) {
            ErrorResponse body = new ErrorResponse(
                    LocalDateTime.now(),
                    HttpStatus.CONFLICT.value(),
                    "Conflict",
                    List.of(ex.getMessage())
            );
            return new ResponseEntity<>(body, HttpStatus.CONFLICT);
        }
        
        // Default to Bad Request for other credential update errors
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Credential Update Failed",
                List.of(ex.getMessage() != null ? ex.getMessage() : "Failed to update credentials")
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // Handle Spring Security authentication exceptions
    @ExceptionHandler({
            AuthenticationException.class,
            BadCredentialsException.class,
            AuthenticationCredentialsNotFoundException.class,
            InsufficientAuthenticationException.class
    })
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthenticationException(AuthenticationException ex) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                List.of(ex.getMessage() != null ? ex.getMessage() : "Authentication failed")
        );
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(Exception ex) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Access Denied",
                List.of(ex.getMessage() != null ? ex.getMessage() : "You do not have permission to access this resource")
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());

        if (details.isEmpty()) {
            details = List.of("Validation constraint violated");
        }

        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                details
        );
    }

    // Handle method argument type mismatch
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' should be of type %s",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                List.of(message)
        );
    }



    // Override Spring's default handlers to ensure they return ErrorResponse

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String msg = ex.getMostSpecificCause() != null ?
                ex.getMostSpecificCause().getMessage() : "Malformed request body";
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Malformed JSON",
                List.of(msg)
        );
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
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

        if (fieldErrors.isEmpty()) {
            fieldErrors = List.of("Validation failed");
        }

        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                fieldErrors
        );
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                List.of(message)
        );
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
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
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Method Not Allowed",
                List.of(message)
        );
        return new ResponseEntity<>(body, headers, HttpStatus.METHOD_NOT_ALLOWED);
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
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "Unsupported Media Type",
                List.of(message)
        );
        return new ResponseEntity<>(body, headers, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String message = String.format("No handler found for %s %s", ex.getHttpMethod(), ex.getRequestURL());
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                List.of(message)
        );
        return new ResponseEntity<>(body, headers, HttpStatus.NOT_FOUND);
    }

    // Catch-all handler - this should handle any exception not caught by more specific handlers
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAllOthers(Exception ex) {
        // Log the exception for debugging
        logger.error("Unhandled exception: ", ex);

        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                List.of("An unexpected error occurred")
        );
    }

    // Ensure this record is complete and properly formatted
    public record ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            List<String> details
    ) {
        // Compact constructor to ensure non-null values
        public ErrorResponse {
            if (timestamp == null) {
                timestamp = LocalDateTime.now();
            }
            if (error == null || error.trim().isEmpty()) {
                error = "Unknown Error";
            }
            if (details == null || details.isEmpty()) {
                details = List.of("No additional details available");
            }
        }
    }
}
