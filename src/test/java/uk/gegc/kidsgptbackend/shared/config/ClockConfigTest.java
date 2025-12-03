package uk.gegc.kidsgptbackend.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClockConfig Tests")
class ClockConfigTest extends BaseUnitTest {

    @Test
    @DisplayName("when clock created with UTC then returns system UTC clock")
    void whenClockCreatedWithUtc_thenReturnsSystemUtcClock() {
        // Given
        ClockConfig config = new ClockConfig();
        
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
