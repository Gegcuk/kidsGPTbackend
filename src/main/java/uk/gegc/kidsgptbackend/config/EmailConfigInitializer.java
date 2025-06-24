package uk.gegc.kidsgptbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class EmailConfigInitializer implements CommandLineRunner {

    private final EmailConfig emailConfig;

    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing email configuration...");
        emailConfig.validateConfiguration();
        
        if (emailConfig.isEnabled()) {
            log.info("Email service is enabled and configured");
        } else {
            log.warn("Email service is disabled - no emails will be sent");
        }
    }
} 