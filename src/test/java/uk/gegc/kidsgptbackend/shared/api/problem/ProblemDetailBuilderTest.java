package uk.gegc.kidsgptbackend.shared.api.problem;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProblemDetailBuilder Tests")
class ProblemDetailBuilderTest {

    @Test
    @DisplayName("when creating with HttpServletRequest then populates all fields")
    void whenCreatingWithHttpServletRequest_thenPopulatesAllFields() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        
        // When
        ProblemDetail problem = ProblemDetailBuilder.create(
            HttpStatus.NOT_FOUND,
            ErrorTypes.RESOURCE_NOT_FOUND,
            "Resource Not Found",
            "The requested resource was not found",
            request
        );
        
        // Then
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getType()).isEqualTo(ErrorTypes.RESOURCE_NOT_FOUND);
        assertThat(problem.getTitle()).isEqualTo("Resource Not Found");
        assertThat(problem.getDetail()).isEqualTo("The requested resource was not found");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/test"));
        assertThat(problem.getProperties()).containsKey("timestamp");
        assertThat(problem.getProperties().get("timestamp")).isInstanceOf(Instant.class);
    }

    @Test
    @DisplayName("when creating with HttpServletRequest and timestamp then uses provided timestamp")
    void whenCreatingWithHttpServletRequestAndTimestamp_thenUsesProvidedTimestamp() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        Instant fixedTime = Instant.parse("2024-01-01T12:00:00Z");
        
        // When
        ProblemDetail problem = ProblemDetailBuilder.create(
            HttpStatus.BAD_REQUEST,
            ErrorTypes.VALIDATION_FAILED,
            "Validation Failed",
            "Invalid input",
            request,
            fixedTime
        );
        
        // Then
        assertThat(problem.getProperties().get("timestamp")).isEqualTo(fixedTime);
    }

    @Test
    @DisplayName("when creating with null HttpServletRequest then no instance field")
    void whenCreatingWithNullHttpServletRequest_thenNoInstanceField() {
        // When
        ProblemDetail problem = ProblemDetailBuilder.create(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorTypes.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            "An error occurred",
            (HttpServletRequest) null
        );
        
        // Then
        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("when creating with WebRequest then extracts URI correctly")
    void whenCreatingWithWebRequest_thenExtractsUriCorrectly() {
        // Given
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/api/v1/users");
        WebRequest webRequest = new ServletWebRequest(servletRequest);
        
        // When
        ProblemDetail problem = ProblemDetailBuilder.create(
            HttpStatus.FORBIDDEN,
            ErrorTypes.ACCESS_DENIED,
            "Access Denied",
            "You don't have permission",
            webRequest
        );
        
        // Then
        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/users"));
    }

    @Test
    @DisplayName("when creating with WebRequest and timestamp then uses provided timestamp")
    void whenCreatingWithWebRequestAndTimestamp_thenUsesProvidedTimestamp() {
        // Given
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/api/v1/test");
        WebRequest webRequest = new ServletWebRequest(servletRequest);
        Instant fixedTime = Instant.parse("2024-06-15T10:30:00Z");
        
        // When
        ProblemDetail problem = ProblemDetailBuilder.create(
            HttpStatus.CONFLICT,
            ErrorTypes.DATA_CONFLICT,
            "Data Conflict",
            "Resource already exists",
            webRequest,
            fixedTime
        );
        
        // Then
        assertThat(problem.getProperties().get("timestamp")).isEqualTo(fixedTime);
    }

    @Test
    @DisplayName("when creating without request context then no instance field")
    void whenCreatingWithoutRequestContext_thenNoInstanceField() {
        // When
        ProblemDetail problem = ProblemDetailBuilder.create(
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorTypes.SERVICE_UNAVAILABLE,
            "Service Unavailable",
            "Service is temporarily unavailable"
        );
        
        // Then
        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("when creating simple then only status and detail")
    void whenCreatingSimple_thenOnlyStatusAndDetail() {
        // When
        ProblemDetail problem = ProblemDetailBuilder.createSimple(
            HttpStatus.BAD_REQUEST,
            "Invalid request"
        );
        
        // Then
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Invalid request");
        // Note: Spring sets type to "about:blank" and title to status reason phrase by default
        assertThat(problem.getType()).isEqualTo(URI.create("about:blank"));
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("when creating with properties then includes custom properties")
    void whenCreatingWithProperties_thenIncludesCustomProperties() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        Map<String, Object> customProperties = Map.of(
            "errorCode", "ERR_123",
            "field", "email",
            "retryAfter", 60
        );
        
        // When
        ProblemDetail problem = ProblemDetailBuilder.createWithProperties(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ErrorTypes.VALIDATION_FAILED,
            "Validation Failed",
            "Email format is invalid",
            request,
            customProperties
        );
        
        // Then
        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getProperties()).containsEntry("errorCode", "ERR_123");
        assertThat(problem.getProperties()).containsEntry("field", "email");
        assertThat(problem.getProperties()).containsEntry("retryAfter", 60);
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("when creating with null properties then no custom properties added")
    void whenCreatingWithNullProperties_thenNoCustomPropertiesAdded() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        
        // When
        ProblemDetail problem = ProblemDetailBuilder.createWithProperties(
            HttpStatus.BAD_REQUEST,
            ErrorTypes.INVALID_ARGUMENT,
            "Invalid Argument",
            "Invalid input",
            request,
            null
        );
        
        // Then
        assertThat(problem.getProperties()).containsKey("timestamp");
        assertThat(problem.getProperties()).hasSize(1); // Only timestamp
    }

    @Test
    @DisplayName("when instantiating utility class then throws AssertionError")
    void whenInstantiatingUtilityClass_thenThrowsAssertionError() {
        // Then
        assertThatThrownBy(() -> {
            var constructor = ProblemDetailBuilder.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .isInstanceOf(Exception.class) // Could be InvocationTargetException
        .cause()
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Utility class - do not instantiate");
    }
}

