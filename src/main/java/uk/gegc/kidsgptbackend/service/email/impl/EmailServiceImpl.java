package uk.gegc.kidsgptbackend.service.email.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import uk.gegc.kidsgptbackend.config.EmailConfig;
import uk.gegc.kidsgptbackend.service.email.EmailService;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailConfig emailConfig;

    @Override
    public void sendPasswordResetEmail(String to, String resetToken, String username) {
        if (!emailConfig.isEnabled()) {
            log.warn("Email service is disabled. Skipping password reset email to: {}", to);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailConfig.getFrom());
            message.setTo(to);
            message.setSubject("KidsGPT - Password Reset Request");

            String resetUrl = emailConfig.getFrontendUrl() + "/reset-password?token=" + resetToken;
            String emailBody = String.format(
                    "Hello %s,\n\n" +
                            "You have requested to reset your password for your KidsGPT account.\n\n" +
                            "Click the link below to reset your password:\n" +
                            "%s\n\n" +
                            "This link will expire in 1 hour.\n\n" +
                            "If you didn't request this password reset, please ignore this email.\n\n" +
                            "Best regards,\n" +
                            "The KidsGPT Team",
                    username, resetUrl
            );

            message.setText(emailBody);
            mailSender.send(message);

            log.info("Password reset email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", to, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    @Override
    public void sendPasswordResetConfirmation(String to, String username) {
        if (!emailConfig.isEnabled()) {
            log.warn("Email service is disabled. Skipping password reset confirmation email to: {}", to);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailConfig.getFrom());
            message.setTo(to);
            message.setSubject("KidsGPT - Password Reset Successful");

            String emailBody = String.format(
                    "Hello %s,\n\n" +
                            "Your password has been successfully reset for your KidsGPT account.\n\n" +
                            "If you did not perform this action, please contact our support team immediately.\n\n" +
                            "Best regards,\n" +
                            "The KidsGPT Team",
                    username
            );

            message.setText(emailBody);
            mailSender.send(message);

            log.info("Password reset confirmation email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset confirmation email to: {}", to, e);
            // Don't throw exception for confirmation email as password is already reset
        }
    }
} 