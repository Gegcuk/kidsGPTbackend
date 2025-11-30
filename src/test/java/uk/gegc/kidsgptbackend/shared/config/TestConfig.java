package uk.gegc.kidsgptbackend.shared.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
@Import({TestAiConfig.class})
public class TestConfig {
    // Mock-only testing configuration for all AI services
    // Fast, reliable, cost-free testing for all OpenAI integrations
} 