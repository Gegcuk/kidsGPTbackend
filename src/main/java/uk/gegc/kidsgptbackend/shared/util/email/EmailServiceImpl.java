package uk.gegc.kidsgptbackend.shared.util.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import uk.gegc.kidsgptbackend.shared.config.EmailConfig;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailConfig emailConfig;
    
    @Value("${verification.ttl-minutes:30}")
    private int verificationTtlMinutes;
    
    private String maskEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "***@unknown";
        }
        int at = email.indexOf('@');
        return at > 0 ? "***@" + email.substring(at + 1) : "***@unknown";
    }

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

            log.info("Password reset email sent to: {}", maskEmail(to));
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", maskEmail(to), e);
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

            log.info("Password reset confirmation email sent to: {}", maskEmail(to));
        } catch (Exception e) {
            log.error("Failed to send password reset confirmation email to: {}", maskEmail(to), e);
            // Don't throw exception for confirmation email as password is already reset
        }
    }

    @Override
    public void sendVerificationEmail(String to, String verificationCode) {
        if (!emailConfig.isEnabled()) {
            log.warn("Email service is disabled. Skipping verification email to: {}", to);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailConfig.getFrom());
            message.setTo(to);
            message.setSubject("KidsGPT - Parent Verification Code");

            String emailBody = String.format(
                    "Hello,\n\n" +
                            "Your verification code for KidsGPT parent verification is: %s\n\n" +
                            "This code will expire in %d minutes.\n\n" +
                            "If you did not request this verification, please ignore this email.\n\n" +
                            "Best regards,\n" +
                            "The KidsGPT Team",
                    verificationCode, verificationTtlMinutes
            );

            message.setText(emailBody);
            mailSender.send(message);

            log.info("Verification email sent to: {}", maskEmail(to));
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", maskEmail(to), e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
}

