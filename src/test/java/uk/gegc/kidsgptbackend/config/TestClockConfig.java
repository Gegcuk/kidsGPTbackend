package uk.gegc.kidsgptbackend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Test configuration for Clock management in tests.
 * 
 * This configuration provides a fixed Clock for testing purposes,
 * allowing tests to have predictable time behavior and avoid
 * flaky tests due to timing issues.
 * 
 * Note: This configuration is active only in the test profile and provides
 * the primary Clock bean to replace ClockConfig's clock() bean which is
 * excluded from the test profile via @Profile("!test").
 * 
 * Important: The fixed time is set to a future date to ensure JWT tokens
 * created in tests don't appear expired (jjwt library uses system time
 * for expiration validation, not our injected Clock).
 */
@TestConfiguration
@Profile("test")
public class TestClockConfig {

    /**
     * Fixed instant for testing - January 1, 2030, 12:00:00 UTC
     * 
     * Using a future date ensures JWT tokens with 12-hour expiry
     * (expires at 2030-01-02T00:00:00Z) won't appear expired when
     * validated by jjwt library using system time.
     */
    private static final Instant FIXED_INSTANT = Instant.parse("2030-01-01T12:00:00Z");

    /**
     * Creates a fixed Clock for testing.
     * 
     * This Clock always returns the same time, making tests predictable
     * and avoiding timing-related flakiness.
     * 
     * This bean is marked as @Primary to replace the production Clock bean
     * from ClockConfig which is excluded from the test profile.
     * 
     * @return Fixed Clock instance for testing
     */
    @Bean
    @Primary
    @Profile("test")
    public Clock testClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));
    }

    /**
     * Gets the fixed instant used in tests.
     * 
     * @return The fixed instant for testing
     */
    public static Instant getFixedInstant() {
        return FIXED_INSTANT;
    }
}

