package uk.gegc.kidsgptbackend.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;

/**
 * Configuration for centralized Clock management.
 * Provides a single source of time for the entire application.
 * 
 * This configuration allows for:
 * - Consistent time handling across the application
 * - Easy testing with fixed clocks
 * - Configurable timezone support
 * - Better control over time-dependent operations
 */
@Configuration
@Slf4j
public class ClockConfig {

    /**
     * Default timezone for the application.
     * Can be overridden via application properties.
     */
    @Value("${app.timezone:UTC}")
    private String timezone;

    /**
     * Creates the primary Clock bean for the application.
     * 
     * The Clock provides:
     * - Current time in the configured timezone
     * - Consistent time source for all time operations
     * - Easy mocking for testing
     * 
     * Note: This bean is not active in the test profile to avoid conflicts
     * with TestClockConfig's fixed Clock bean.
     * 
     * @return Clock instance configured with the application timezone
     * @throws IllegalStateException if timezone configuration is invalid after fallback
     */
    @Bean
    @Primary
    @Profile("!test")
    public Clock clock() {
        String configuredZone = timezone == null || timezone.isBlank()
                ? "UTC"
                : timezone.trim();
        
        try {
            return Clock.system(ZoneId.of(configuredZone));
        } catch (ZoneRulesException e) {
            log.warn("Invalid timezone configured: '{}'. Falling back to UTC.", configuredZone, e);
            return Clock.systemUTC();
        }
    }

    /**
     * Creates a UTC Clock bean for operations that specifically need UTC time.
     * 
     * Note: Not active in test profile to avoid bean conflicts.
     * In tests, use the primary Clock bean which is fixed to UTC anyway.
     * 
     * @return Clock instance configured for UTC
     */
    @Bean("utcClock")
    @Profile("!test")
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    /**
     * Creates a system default Clock bean for operations that need system timezone.
     * 
     * Note: Not active in test profile to avoid bean conflicts.
     * In tests, use the primary Clock bean which is fixed.
     * 
     * @return Clock instance using system default timezone
     */
    @Bean("systemClock")
    @Profile("!test")
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
