package uk.gegc.kidsgptbackend.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClockConfig Tests")
class ClockConfigTest extends BaseUnitTest {

    @Test
    @DisplayName("when timezone null then defaults to UTC")
    void whenTimezoneNull_thenDefaultsToUtc() {
        // Given
        ClockConfig config = new ClockConfig();
        ReflectionTestUtils.setField(config, "timezone", null);
        
        // When
        Clock clock = config.clock();
        
        // Then
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("when timezone blank then defaults to UTC")
    void whenTimezoneBlank_thenDefaultsToUtc() {
        // Given
        ClockConfig config = new ClockConfig();
        ReflectionTestUtils.setField(config, "timezone", "   ");
        
        // When
        Clock clock = config.clock();
        
        // Then
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("when timezone configured to UTC then uses UTC zone")
    void whenTimezoneConfiguredToUtc_thenUsesUtcZone() {
        // Given
        ClockConfig config = new ClockConfig();
        ReflectionTestUtils.setField(config, "timezone", "UTC");
        
        // When
        Clock clock = config.clock();
        
        // Then
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("when timezone configured to America/New_York then uses that zone")
    void whenTimezoneConfiguredToNewYork_thenUsesThatZone() {
        // Given
        ClockConfig config = new ClockConfig();
        ReflectionTestUtils.setField(config, "timezone", "America/New_York");
        
        // When
        Clock clock = config.clock();
        
        // Then
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    @DisplayName("when timezone configured to Europe/London then uses that zone")
    void whenTimezoneConfiguredToLondon_thenUsesThatZone() {
        // Given
        ClockConfig config = new ClockConfig();
        ReflectionTestUtils.setField(config, "timezone", "Europe/London");
        
        // When
        Clock clock = config.clock();
        
        // Then
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/London"));
    }

    @Test
    @DisplayName("when timezone configured with spaces then trims and uses zone")
    void whenTimezoneConfiguredWithSpaces_thenTrimsAndUsesZone() {
        // Given
        ClockConfig config = new ClockConfig();
        ReflectionTestUtils.setField(config, "timezone", "  UTC  ");
        
        // When
        Clock clock = config.clock();
        
        // Then
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("when utc clock created then uses UTC zone")
    void whenUtcClockCreated_thenUsesUtcZone() {
        // Given
        ClockConfig config = new ClockConfig();
        
        // When
        Clock utcClock = config.utcClock();
        
        // Then - "UTC" and "Z" are equivalent representations
        assertThat(utcClock.getZone()).isIn(ZoneId.of("UTC"), ZoneId.of("Z"));
    }

    @Test
    @DisplayName("when system clock created then uses system default zone")
    void whenSystemClockCreated_thenUsesSystemDefaultZone() {
        // Given
        ClockConfig config = new ClockConfig();
        
        // When
        Clock systemClock = config.systemClock();
        
        // Then
        assertThat(systemClock.getZone()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    @DisplayName("when clock used then returns instant")
    void whenClockUsed_thenReturnsInstant() {
        // Given
        ClockConfig config = new ClockConfig();
        ReflectionTestUtils.setField(config, "timezone", "UTC");
        Clock clock = config.clock();
        
        // When
        Instant instant = clock.instant();
        
        // Then
        assertThat(instant).isNotNull();
        assertThat(instant).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("when multiple clocks created then all work independently")
    void whenMultipleClocksCreated_thenAllWorkIndependently() {
        // Given
        ClockConfig config = new ClockConfig();
        ReflectionTestUtils.setField(config, "timezone", "UTC");
        
        // When
        Clock primaryClock = config.clock();
        Clock utcClock = config.utcClock();
        Clock systemClock = config.systemClock();
        
        // Then
        assertThat(primaryClock).isNotNull();
        assertThat(utcClock).isNotNull();
        assertThat(systemClock).isNotNull();
        
        // All clocks should return current time
        Instant now = Instant.now();
        assertThat(primaryClock.instant()).isBeforeOrEqualTo(now.plusSeconds(1));
        assertThat(utcClock.instant()).isBeforeOrEqualTo(now.plusSeconds(1));
        assertThat(systemClock.instant()).isBeforeOrEqualTo(now.plusSeconds(1));
    }
}
