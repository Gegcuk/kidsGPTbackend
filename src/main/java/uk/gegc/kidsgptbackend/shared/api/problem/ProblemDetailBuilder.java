package uk.gegc.kidsgptbackend.shared.api.problem;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Helper functions for building RFC 9457 {@link ProblemDetail} instances in a consistent way.
 * 
 * <p>This utility provides methods to create standardized error responses following
 * the RFC 9457 (Problem Details for HTTP APIs) specification.</p>
 * 
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457</a>
 */
public final class ProblemDetailBuilder {

    private ProblemDetailBuilder() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Creates a {@link ProblemDetail} using the provided HTTP request to populate the {@code instance} field.
     * 
     * @param status HTTP status code
     * @param type URI identifying the problem type
     * @param title Short human-readable summary
     * @param detail Detailed human-readable explanation
     * @param request HTTP request to extract instance URI from
     * @return Configured ProblemDetail instance
     */
    public static ProblemDetail create(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Creates a {@link ProblemDetail} using the provided HTTP request and timestamp.
     * 
     * @param status HTTP status code
     * @param type URI identifying the problem type
     * @param title Short human-readable summary
     * @param detail Detailed human-readable explanation
     * @param request HTTP request to extract instance URI from
     * @param timestamp Instant to use for timestamp property
     * @return Configured ProblemDetail instance
     */
    public static ProblemDetail create(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            HttpServletRequest request,
            Instant timestamp
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("timestamp", timestamp);
        return problem;
    }

    /**
     * Creates a {@link ProblemDetail} using Spring's {@link WebRequest} to populate the instance field.
     * Useful inside Spring MVC override methods where an {@link HttpServletRequest} is not available.
     * 
     * @param status HTTP status code
     * @param type URI identifying the problem type
     * @param title Short human-readable summary
     * @param detail Detailed human-readable explanation
     * @param request Spring WebRequest to extract instance URI from
     * @return Configured ProblemDetail instance
     */
    public static ProblemDetail create(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            WebRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        if (request != null) {
            String description = request.getDescription(false);
            if (description != null) {
                String uri = description.startsWith("uri=") ? description.substring(4) : description;
                problem.setInstance(URI.create(uri));
            }
        }
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Creates a {@link ProblemDetail} using Spring's {@link WebRequest} with a custom timestamp.
     * 
     * @param status HTTP status code
     * @param type URI identifying the problem type
     * @param title Short human-readable summary
     * @param detail Detailed human-readable explanation
     * @param request Spring WebRequest to extract instance URI from
     * @param timestamp Instant to use for timestamp property
     * @return Configured ProblemDetail instance
     */
    public static ProblemDetail create(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            WebRequest request,
            Instant timestamp
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        if (request != null) {
            String description = request.getDescription(false);
            if (description != null) {
                String uri = description.startsWith("uri=") ? description.substring(4) : description;
                problem.setInstance(URI.create(uri));
            }
        }
        problem.setProperty("timestamp", timestamp);
        return problem;
    }

    /**
     * Creates a {@link ProblemDetail} without any request context.
     * 
     * @param status HTTP status code
     * @param type URI identifying the problem type
     * @param title Short human-readable summary
     * @param detail Detailed human-readable explanation
     * @return Configured ProblemDetail instance
     */
    public static ProblemDetail create(
            HttpStatus status,
            URI type,
            String title,
            String detail
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Creates a minimal {@link ProblemDetail} with status + detail only.
     * 
     * @param status HTTP status code
     * @param detail Detailed human-readable explanation
     * @return Configured ProblemDetail instance
     */
    public static ProblemDetail createSimple(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Creates a {@link ProblemDetail} and applies the supplied custom properties in a single call.
     * 
     * @param status HTTP status code
     * @param type URI identifying the problem type
     * @param title Short human-readable summary
     * @param detail Detailed human-readable explanation
     * @param request HTTP request to extract instance URI from
     * @param properties Additional custom properties to include
     * @return Configured ProblemDetail instance
     */
    public static ProblemDetail createWithProperties(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            HttpServletRequest request,
            Map<String, Object> properties
    ) {
        ProblemDetail problem = create(status, type, title, detail, request);
        if (properties != null) {
            properties.forEach(problem::setProperty);
        }
        return problem;
    }
}

