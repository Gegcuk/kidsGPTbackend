package uk.gegc.kidsgptbackend.service.email.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import uk.gegc.kidsgptbackend.config.EmailConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailConfig emailConfig;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        // Set up a real EmailConfig with test values
        emailConfig = new EmailConfig();
        emailConfig.setFrom("noreply@kidsgpt.com");
        emailConfig.setFrontendUrl("http://localhost:3000");
        emailConfig.setEnabled(true);
        emailConfig.setHost("smtp.test.com");
        emailConfig.setPort(587);
        emailConfig.setUsername("testuser@test.com");
        emailConfig.setPassword("testpass");
        emailService = new EmailServiceImpl(mailSender, emailConfig);
    }

    @Test
    @DisplayName("sendPasswordResetEmail: success")
    void sendPasswordResetEmail_success() {
        String to = "test@example.com";
        String resetToken = "reset-token-123";
        String username = "testuser";

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendPasswordResetEmail(to, resetToken, username);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendPasswordResetEmail: handles mail sender exception")
    void sendPasswordResetEmail_mailSenderException_throwsException() {
        String to = "test@example.com";
        String resetToken = "reset-token-123";
        String username = "testuser";

        doThrow(new RuntimeException("Mail server error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> emailService.sendPasswordResetEmail(to, resetToken, username))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to send password reset email");
    }

    @Test
    @DisplayName("sendPasswordResetConfirmation: success")
    void sendPasswordResetConfirmation_success() {
        String to = "test@example.com";
        String username = "testuser";

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendPasswordResetConfirmation(to, username);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendPasswordResetConfirmation: handles mail sender exception gracefully")
    void sendPasswordResetConfirmation_mailSenderException_handlesGracefully() {
        String to = "test@example.com";
        String username = "testuser";

        doThrow(new RuntimeException("Mail server error")).when(mailSender).send(any(SimpleMailMessage.class));

        // Should not throw exception for confirmation email
        emailService.sendPasswordResetConfirmation(to, username);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendPasswordResetEmail: verifies message content")
    void sendPasswordResetEmail_verifiesMessageContent() {
        String to = "test@example.com";
        String resetToken = "reset-token-123";
        String username = "testuser";

        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            assertThat(message.getTo()).contains(to);
            assertThat(message.getFrom()).isEqualTo("noreply@kidsgpt.com");
            assertThat(message.getSubject()).isEqualTo("KidsGPT - Password Reset Request");
            assertThat(message.getText()).contains(resetToken);
            assertThat(message.getText()).contains(username);
            assertThat(message.getText()).contains("http://localhost:3000/reset-password?token=" + resetToken);
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendPasswordResetEmail(to, resetToken, username);
    }

    @Test
    @DisplayName("sendPasswordResetConfirmation: verifies message content")
    void sendPasswordResetConfirmation_verifiesMessageContent() {
        String to = "test@example.com";
        String username = "testuser";

        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            assertThat(message.getTo()).contains(to);
            assertThat(message.getFrom()).isEqualTo("noreply@kidsgpt.com");
            assertThat(message.getSubject()).isEqualTo("KidsGPT - Password Reset Successful");
            assertThat(message.getText()).contains(username);
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendPasswordResetConfirmation(to, username);
    }

    @Test
    @DisplayName("sendPasswordResetEmail: handles null username")
    void sendPasswordResetEmail_nullUsername_handlesGracefully() {
        String to = "test@example.com";
        String resetToken = "reset-token-123";
        String username = null;

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendPasswordResetEmail(to, resetToken, username);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendPasswordResetConfirmation: handles null username")
    void sendPasswordResetConfirmation_nullUsername_handlesGracefully() {
        String to = "test@example.com";
        String username = null;

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendPasswordResetConfirmation(to, username);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendPasswordResetEmail: handles empty email")
    void sendPasswordResetEmail_emptyEmail_handlesGracefully() {
        String to = "";
        String resetToken = "reset-token-123";
        String username = "testuser";

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendPasswordResetEmail(to, resetToken, username);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendPasswordResetConfirmation: handles empty email")
    void sendPasswordResetConfirmation_emptyEmail_handlesGracefully() {
        String to = "";
        String username = "testuser";

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendPasswordResetConfirmation(to, username);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
} 