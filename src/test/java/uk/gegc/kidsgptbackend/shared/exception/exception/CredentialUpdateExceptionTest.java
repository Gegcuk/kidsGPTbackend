package uk.gegc.kidsgptbackend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialUpdateExceptionTest {

    @Test
    @DisplayName("CredentialUpdateException should have correct message")
    void credentialUpdateException_shouldHaveCorrectMessage() {
        String message = "Email already in use";
        CredentialUpdateException exception = new CredentialUpdateException(message);
        
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("CredentialUpdateException should have cause")
    void credentialUpdateException_shouldHaveCause() {
        String message = "Password update failed";
        Throwable cause = new RuntimeException("Database error");
        CredentialUpdateException exception = new CredentialUpdateException(message, cause);
        
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
} 