package uk.gegc.kidsgptbackend.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.email")
public class EmailConfig {

    @NotBlank(message = "Email host is required")
    private String host = "smtp.gmail.com";

    @NotNull(message = "Email port is required")
    @Positive(message = "Email port must be positive")
    private Integer port = 587;

    @NotBlank(message = "Email username is required")
    private String username;

    @NotBlank(message = "Email password is required")
    private String password;

    @NotBlank(message = "From email is required")
    @Email(message = "From email must be a valid email address")
    private String from;

    @NotBlank(message = "Frontend URL is required")
    private String frontendUrl = "http://localhost:3000";

    private boolean enabled = true;

    public void validateConfiguration() {
        if (!enabled) {
            log.warn("Email service is disabled. No emails will be sent.");
            return;
        }

        log.info("Email configuration validated:");
        log.info("Host: {}", host);
        log.info("Port: {}", port);
        log.info("Username: {}", username);
        log.info("From: {}", from);
        log.info("Frontend URL: {}", frontendUrl);
    }
} 