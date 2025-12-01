package uk.gegc.kidsgptbackend.shared.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link EmailConfig}.
 * <p>
 * Tests email configuration validation including:
 * - Email disabled scenario (no validation)
 * - Required fields validation when enabled
 * - Email format validation using EmailValidationTarget
 * - Valid and invalid email addresses
 */
@DisplayName("EmailConfig Tests")
class EmailConfigTest extends BaseUnitTest {

    @Mock
    private Validator validator;

    private EmailConfig emailConfig;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        emailConfig = new EmailConfig(validator);
    }

    @Test
    @DisplayName("validateConfiguration: when email disabled then no validation performed")
    void validateConfiguration_emailDisabled_noValidationPerformed() {
        // Given
        emailConfig.setEnabled(false);

        // When - should not throw
        emailConfig.validateConfiguration();

        // Then
        verify(validator, never()).validate(any());
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and host missing then throws exception")
    void validateConfiguration_emailEnabledHostMissing_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost(null);
        emailConfig.setPort(587);
        emailConfig.setUsername("user");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email host is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and port invalid then throws exception")
    void validateConfiguration_emailEnabledPortInvalid_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(0);
        emailConfig.setUsername("user");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email port must be positive when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and port null then throws exception")
    void validateConfiguration_emailEnabledPortNull_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(null);
        emailConfig.setUsername("user");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email port must be positive when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and port negative then throws exception")
    void validateConfiguration_emailEnabledPortNegative_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(-1);
        emailConfig.setUsername("user");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email port must be positive when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and host empty string then throws exception")
    void validateConfiguration_emailEnabledHostEmptyString_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("");
        emailConfig.setPort(587);
        emailConfig.setUsername("user");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email host is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and host whitespace only then throws exception")
    void validateConfiguration_emailEnabledHostWhitespace_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("   ");
        emailConfig.setPort(587);
        emailConfig.setUsername("user");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email host is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and username empty string then throws exception")
    void validateConfiguration_emailEnabledUsernameEmptyString_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email username is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and password empty string then throws exception")
    void validateConfiguration_emailEnabledPasswordEmptyString_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user");
        emailConfig.setPassword("");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email password is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and frontendUrl empty string then throws exception")
    void validateConfiguration_emailEnabledFrontendUrlEmptyString_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Frontend URL is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and username missing then throws exception")
    void validateConfiguration_emailEnabledUsernameMissing_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername(null);
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email username is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and password missing then throws exception")
    void validateConfiguration_emailEnabledPasswordMissing_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user");
        emailConfig.setPassword(null);
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email password is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and from missing then throws exception")
    void validateConfiguration_emailEnabledFromMissing_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user");
        emailConfig.setPassword("pass");
        emailConfig.setFrom(null);
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("From email is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and frontendUrl missing then throws exception")
    void validateConfiguration_emailEnabledFrontendUrlMissing_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl(null);

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Frontend URL is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and valid email then passes validation")
    void validateConfiguration_emailEnabledValidEmail_passesValidation() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user@example.com");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test@example.com");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // Mock validator to return no violations (valid email)
        when(validator.validate(any())).thenReturn(new HashSet<>());

        // When - should not throw
        emailConfig.validateConfiguration();

        // Then
        verify(validator).validate(any());
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and invalid email then throws exception with validation message")
    void validateConfiguration_emailEnabledInvalidEmail_throwsExceptionWithValidationMessage() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user@example.com");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("invalid-email");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // Mock validator to return violation with the expected message
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("From email must be a valid email address");
        
        Set<ConstraintViolation<Object>> violations = new HashSet<>();
        violations.add(violation);
        
        when(validator.validate(any())).thenReturn(violations);

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("From email validation failed: From email must be a valid email address");
        
        // Verify EmailValidationTarget was created and validated
        verify(validator).validate(any());
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled and email with multiple violations then uses first violation message")
    void validateConfiguration_emailEnabledMultipleViolations_usesFirstViolationMessage() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user@example.com");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("invalid-email");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // Mock validator to return multiple violations
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation1 = mock(ConstraintViolation.class);
        when(violation1.getMessage()).thenReturn("From email must be a valid email address");
        
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation2 = mock(ConstraintViolation.class);
        when(violation2.getMessage()).thenReturn("Another validation error");
        
        // Use LinkedHashSet to preserve insertion order so first violation is returned
        Set<ConstraintViolation<Object>> violations = new LinkedHashSet<>();
        violations.add(violation1);
        violations.add(violation2);
        
        when(validator.validate(any())).thenReturn(violations);

        // When/Then
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("From email validation failed: From email must be a valid email address");
        
        // Verify EmailValidationTarget was created and validated
        verify(validator).validate(any());
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled with valid complex email then passes")
    void validateConfiguration_emailEnabledValidComplexEmail_passes() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user@example.com");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("test.user+tag@subdomain.example.co.uk");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // Mock validator to return no violations (valid email)
        when(validator.validate(any())).thenReturn(new HashSet<>());

        // When - should not throw
        emailConfig.validateConfiguration();

        // Then
        verify(validator).validate(any());
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled with empty string email then throws exception")
    void validateConfiguration_emailEnabledEmptyStringEmail_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user@example.com");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then - should fail at required field check before validation
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("From email is required when email is enabled");
    }

    @Test
    @DisplayName("validateConfiguration: when email enabled with whitespace-only email then throws exception")
    void validateConfiguration_emailEnabledWhitespaceOnlyEmail_throwsException() {
        // Given
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.example.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("user@example.com");
        emailConfig.setPassword("pass");
        emailConfig.setFrom("   ");
        emailConfig.setFrontendUrl("http://localhost:3000");

        // When/Then - should fail at required field check before validation
        assertThatThrownBy(() -> emailConfig.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("From email is required when email is enabled");
    }
}

