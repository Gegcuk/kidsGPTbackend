package uk.gegc.kidsgptbackend.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Set;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "app.email")
public class EmailConfig {

    private final Validator validator;

    public EmailConfig(Validator validator) {
        this.validator = validator;
    }

    private String host = "smtp.gmail.com";

    private Integer port = 587;

    private String username;

    private String password;

    private String from;

    private String frontendUrl = "http://localhost:3000";

    private boolean enabled = true;

    /**
     * Helper class for email validation using Jakarta Validation
     */
    private static class EmailValidationTarget {
        @Email(message = "From email must be a valid email address")
        private final String email;

        EmailValidationTarget(String email) {
            this.email = email;
        }
    }

    @PostConstruct
    public void validateConfiguration() {
        if (!enabled) {
            log.warn("Email service is disabled. No emails will be sent.");
            return;
        }

        // Validate required fields only when email is enabled
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("Email host is required when email is enabled");
        }
        if (port == null || port <= 0) {
            throw new IllegalStateException("Email port must be positive when email is enabled");
        }
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("Email username is required when email is enabled");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("Email password is required when email is enabled");
        }
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException("From email is required when email is enabled");
        }
        // Use Jakarta Validation's Email validator for RFC-compliant validation
        EmailValidationTarget validationTarget = new EmailValidationTarget(from);
        Set<ConstraintViolation<EmailValidationTarget>> violations = validator.validate(validationTarget);
        if (!violations.isEmpty()) {
            String errorMessage = violations.iterator().next().getMessage();
            throw new IllegalStateException("From email validation failed: " + errorMessage);
        }
        if (!StringUtils.hasText(frontendUrl)) {
            throw new IllegalStateException("Frontend URL is required when email is enabled");
        }

        log.info("Email configuration validated:");
        log.info("Host: {}", host);
        log.info("Port: {}", port);
        log.info("Username: {}", username);
        log.info("From: {}", from);
        log.info("Frontend URL: {}", frontendUrl);
    }
} 