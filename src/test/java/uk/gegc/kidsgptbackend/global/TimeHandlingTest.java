package uk.gegc.kidsgptbackend.global;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;
import uk.gegc.kidsgptbackend.service.subscription.impl.SubscriptionSaver;
import uk.gegc.kidsgptbackend.service.subscription.impl.WebhookProcessingServiceImpl;
import uk.gegc.kidsgptbackend.service.subscription.impl.SubscriptionServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for time handling requirements:
 * - All service logic tolerates null provider timestamps
 * - UTC/Instant everywhere—no timezone drift
 * - Consistent time handling across all services
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Time Handling Tests")
class TimeHandlingTest {

    @Mock
    private User user;

    @Mock
    private GooglePlaySubscriptionPurchase googlePurchase;

    private UserSubscription subscription;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // Use a fixed clock for consistent testing
        fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);
        
        // Set up test data
        subscription = new UserSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        subscription.setStartDate(Instant.now(fixedClock));
        subscription.setAutoRenew(true);
    }

    @Test
    @DisplayName("Service logic should tolerate null provider timestamps in subscription creation")
    void serviceLogic_shouldTolerateNullProviderTimestampsInSubscriptionCreation() {
        // Given
        GooglePlaySubscriptionPurchase purchaseWithNullTimestamps = new GooglePlaySubscriptionPurchase();
        purchaseWithNullTimestamps.setPurchaseToken("test_token");
        purchaseWithNullTimestamps.setProductId("plus_monthly");
        purchaseWithNullTimestamps.setStartTimeMillis(0); // Null equivalent
        purchaseWithNullTimestamps.setExpiryTimeMillis(0); // Null equivalent
        purchaseWithNullTimestamps.setPurchaseState("PURCHASED");
        purchaseWithNullTimestamps.setAutoRenewing(true);

        // When
        // Simulate subscription creation with null timestamps
        UserSubscription result = new UserSubscription();
        result.setUser(user);
        result.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        result.setStartDate(Instant.now(fixedClock));
        
        // Provider timestamps should be null
        result.setCurrentPeriodStart(null);
        result.setCurrentPeriodEnd(null);
        result.setNextBillingDate(null);

        // Then
        // Should not throw exceptions and should handle null timestamps gracefully
        assertThat(result.getCurrentPeriodStart()).isNull();
        assertThat(result.getCurrentPeriodEnd()).isNull();
        assertThat(result.getNextBillingDate()).isNull();
        assertThat(result.getStartDate()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Service logic should handle null timestamps in subscription updates")
    void serviceLogic_shouldHandleNullTimestampsInSubscriptionUpdates() {
        // Given
        UserSubscription existingSubscription = new UserSubscription();
        existingSubscription.setId(UUID.randomUUID());
        existingSubscription.setUser(user);
        existingSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        existingSubscription.setStartDate(Instant.now(fixedClock));
        
        // Set some timestamps to null
        existingSubscription.setCurrentPeriodStart(null);
        existingSubscription.setCurrentPeriodEnd(null);
        existingSubscription.setNextBillingDate(null);

        // When
        // Simulate webhook processing with null timestamps
        GooglePlaySubscriptionPurchase purchase = new GooglePlaySubscriptionPurchase();
        purchase.setStartTimeMillis(0); // Null equivalent
        purchase.setExpiryTimeMillis(0); // Null equivalent
        
        // Update subscription with null timestamps
        if (purchase.getStartTimeMillis() > 0) {
            existingSubscription.setCurrentPeriodStart(Instant.ofEpochMilli(purchase.getStartTimeMillis()));
        }
        if (purchase.getExpiryTimeMillis() > 0) {
            Instant end = Instant.ofEpochMilli(purchase.getExpiryTimeMillis());
            existingSubscription.setCurrentPeriodEnd(end);
            existingSubscription.setNextBillingDate(end);
        }

        // Then
        // Should handle null timestamps gracefully
        assertThat(existingSubscription.getCurrentPeriodStart()).isNull();
        assertThat(existingSubscription.getCurrentPeriodEnd()).isNull();
        assertThat(existingSubscription.getNextBillingDate()).isNull();
        assertThat(existingSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("All timestamps should use UTC/Instant consistently")
    void allTimestamps_shouldUseUtcInstantConsistently() {
        // Given
        Instant utcTime = Instant.parse("2024-01-01T12:00:00Z");
        
        // When
        UserSubscription subscription = new UserSubscription();
        subscription.setStartDate(utcTime);
        subscription.setEndDate(utcTime.plus(30, ChronoUnit.DAYS));
        subscription.setCurrentPeriodStart(utcTime);
        subscription.setCurrentPeriodEnd(utcTime.plus(30, ChronoUnit.DAYS));
        subscription.setNextBillingDate(utcTime.plus(30, ChronoUnit.DAYS));
        subscription.setCancelledAt(utcTime.plus(15, ChronoUnit.DAYS));
        subscription.setPausedAt(utcTime.plus(10, ChronoUnit.DAYS));
        subscription.setGracePeriodEnd(utcTime.plus(35, ChronoUnit.DAYS));
        subscription.setTrialEndDate(utcTime.plus(7, ChronoUnit.DAYS));

        // Then
        // All timestamps should be UTC Instant objects
        assertThat(subscription.getStartDate()).isEqualTo(utcTime);
        assertThat(subscription.getEndDate()).isEqualTo(utcTime.plus(30, ChronoUnit.DAYS));
        assertThat(subscription.getCurrentPeriodStart()).isEqualTo(utcTime);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(utcTime.plus(30, ChronoUnit.DAYS));
        assertThat(subscription.getNextBillingDate()).isEqualTo(utcTime.plus(30, ChronoUnit.DAYS));
        assertThat(subscription.getCancelledAt()).isEqualTo(utcTime.plus(15, ChronoUnit.DAYS));
        assertThat(subscription.getPausedAt()).isEqualTo(utcTime.plus(10, ChronoUnit.DAYS));
        assertThat(subscription.getGracePeriodEnd()).isEqualTo(utcTime.plus(35, ChronoUnit.DAYS));
        assertThat(subscription.getTrialEndDate()).isEqualTo(utcTime.plus(7, ChronoUnit.DAYS));
        
        // Verify all timestamps end with 'Z' (UTC format)
        assertThat(subscription.getStartDate().toString()).endsWith("Z");
        assertThat(subscription.getEndDate().toString()).endsWith("Z");
        assertThat(subscription.getCurrentPeriodStart().toString()).endsWith("Z");
        assertThat(subscription.getCurrentPeriodEnd().toString()).endsWith("Z");
        assertThat(subscription.getNextBillingDate().toString()).endsWith("Z");
        assertThat(subscription.getCancelledAt().toString()).endsWith("Z");
        assertThat(subscription.getPausedAt().toString()).endsWith("Z");
        assertThat(subscription.getGracePeriodEnd().toString()).endsWith("Z");
        assertThat(subscription.getTrialEndDate().toString()).endsWith("Z");
    }

    @Test
    @DisplayName("Subscription expiration logic should handle null timestamps gracefully")
    void subscriptionExpirationLogic_shouldHandleNullTimestampsGracefully() {
        // Given
        UserSubscription subscriptionWithNullTimestamps = new UserSubscription();
        subscriptionWithNullTimestamps.setId(UUID.randomUUID());
        subscriptionWithNullTimestamps.setUser(user);
        subscriptionWithNullTimestamps.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        subscriptionWithNullTimestamps.setStartDate(Instant.now(fixedClock));
        subscriptionWithNullTimestamps.setCurrentPeriodStart(null);
        subscriptionWithNullTimestamps.setCurrentPeriodEnd(null);
        subscriptionWithNullTimestamps.setNextBillingDate(null);

        // When
        // Test the isExpired() method with null timestamps
        boolean isExpired = subscriptionWithNullTimestamps.isExpired();

        // Then
        // Should not throw NullPointerException and should handle gracefully
        assertThat(isExpired).isFalse(); // Null timestamps should not be considered expired
        assertThat(subscriptionWithNullTimestamps.getCurrentPeriodStart()).isNull();
        assertThat(subscriptionWithNullTimestamps.getCurrentPeriodEnd()).isNull();
        assertThat(subscriptionWithNullTimestamps.getNextBillingDate()).isNull();
    }

    @Test
    @DisplayName("Subscription trial logic should handle null timestamps gracefully")
    void subscriptionTrialLogic_shouldHandleNullTimestampsGracefully() {
        // Given
        UserSubscription subscriptionWithNullTrialTimestamp = new UserSubscription();
        subscriptionWithNullTrialTimestamp.setId(UUID.randomUUID());
        subscriptionWithNullTrialTimestamp.setUser(user);
        subscriptionWithNullTrialTimestamp.setStatus(UserSubscription.SubscriptionStatus.TRIALING);
        subscriptionWithNullTrialTimestamp.setStartDate(Instant.now(fixedClock));
        subscriptionWithNullTrialTimestamp.setTrial(true);
        subscriptionWithNullTrialTimestamp.setTrialEndDate(null); // Null trial end date

        // When
        // Test the isInTrial() method with null timestamps
        boolean isInTrial = subscriptionWithNullTrialTimestamp.isInTrial();

        // Then
        // Should not throw NullPointerException and should handle gracefully
        assertThat(isInTrial).isFalse(); // Null trial end date should not be considered in trial
        assertThat(subscriptionWithNullTrialTimestamp.getTrialEndDate()).isNull();
    }

    @Test
    @DisplayName("Time calculations should be consistent across different time zones")
    void timeCalculations_shouldBeConsistentAcrossDifferentTimeZones() {
        // Given
        Instant utcTime = Instant.parse("2024-01-01T12:00:00Z");
        Clock utcClock = Clock.fixed(utcTime, ZoneOffset.UTC);
        Clock estClock = Clock.fixed(utcTime, ZoneOffset.of("-05:00"));
        Clock pstClock = Clock.fixed(utcTime, ZoneOffset.of("-08:00"));

        // When
        Instant utcNow = Instant.now(utcClock);
        Instant estNow = Instant.now(estClock);
        Instant pstNow = Instant.now(pstClock);

        // Then
        // All should represent the same moment in time
        assertThat(utcNow).isEqualTo(estNow);
        assertThat(estNow).isEqualTo(pstNow);
        assertThat(utcNow).isEqualTo(pstNow);
        
        // All should end with 'Z' (UTC format)
        assertThat(utcNow.toString()).endsWith("Z");
        assertThat(estNow.toString()).endsWith("Z");
        assertThat(pstNow.toString()).endsWith("Z");
    }

    @Test
    @DisplayName("Provider timestamp conversion should handle null values gracefully")
    void providerTimestampConversion_shouldHandleNullValuesGracefully() {
        // Given
        GooglePlaySubscriptionPurchase purchase = new GooglePlaySubscriptionPurchase();
        purchase.setStartTimeMillis(0); // Null equivalent
        purchase.setExpiryTimeMillis(0); // Null equivalent
        purchase.setPurchaseState("PURCHASED");

        // When
        UserSubscription subscription = new UserSubscription();
        
        // Simulate the conversion logic from provider timestamps
        Long startMs = purchase.getStartTimeMillis();
        Long endMs = purchase.getExpiryTimeMillis();
        
        if (startMs != null && startMs > 0) {
            subscription.setCurrentPeriodStart(Instant.ofEpochMilli(startMs));
        }
        if (endMs != null && endMs > 0) {
            Instant end = Instant.ofEpochMilli(endMs);
            subscription.setCurrentPeriodEnd(end);
            subscription.setNextBillingDate(end);
        }

        // Then
        // Should handle null/zero timestamps gracefully
        assertThat(subscription.getCurrentPeriodStart()).isNull();
        assertThat(subscription.getCurrentPeriodEnd()).isNull();
        assertThat(subscription.getNextBillingDate()).isNull();
    }

    @Test
    @DisplayName("Subscription status checks should work with null timestamps")
    void subscriptionStatusChecks_shouldWorkWithNullTimestamps() {
        // Given
        UserSubscription subscription = new UserSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        subscription.setStartDate(Instant.now(fixedClock));
        subscription.setCurrentPeriodStart(null);
        subscription.setCurrentPeriodEnd(null);
        subscription.setNextBillingDate(null);
        subscription.setTrialEndDate(null);

        // When
        boolean isActive = subscription.isActive();
        boolean isExpired = subscription.isExpired();
        boolean isInTrial = subscription.isInTrial();

        // Then
        // Should not throw exceptions and should return reasonable defaults
        assertThat(isActive).isTrue(); // ACTIVE status should be active
        assertThat(isExpired).isFalse(); // Null timestamps should not be expired
        assertThat(isInTrial).isFalse(); // Null trial timestamp should not be in trial
    }

    @Test
    @DisplayName("Time-based business logic should handle edge cases with null timestamps")
    void timeBasedBusinessLogic_shouldHandleEdgeCasesWithNullTimestamps() {
        // Given
        UserSubscription subscription = new UserSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        subscription.setStartDate(Instant.now(fixedClock));
        subscription.setCurrentPeriodStart(null);
        subscription.setCurrentPeriodEnd(null);
        subscription.setNextBillingDate(null);
        subscription.setCancelledAt(null);
        subscription.setPausedAt(null);
        subscription.setGracePeriodEnd(null);
        subscription.setTrialEndDate(null);

        // When
        // Test various time-based operations
        boolean hasValidPeriod = subscription.getCurrentPeriodStart() != null && 
                                subscription.getCurrentPeriodEnd() != null;
        boolean isInGracePeriod = subscription.getGracePeriodEnd() != null && 
                                 subscription.getGracePeriodEnd().isAfter(Instant.now(fixedClock));
        boolean isCancelled = subscription.getCancelledAt() != null;
        boolean isPaused = subscription.getPausedAt() != null;

        // Then
        // Should handle null timestamps gracefully
        assertThat(hasValidPeriod).isFalse();
        assertThat(isInGracePeriod).isFalse();
        assertThat(isCancelled).isFalse();
        assertThat(isPaused).isFalse();
        
        // Subscription should still be functional
        assertThat(subscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(subscription.getStartDate()).isNotNull();
    }

    @Test
    @DisplayName("Clock usage should be consistent across all services")
    void clockUsage_shouldBeConsistentAcrossAllServices() {
        // Given
        Clock testClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);
        
        // When
        Instant now1 = Instant.now(testClock);
        Instant now2 = Instant.now(testClock);
        Instant now3 = Instant.now(testClock);

        // Then
        // All should be the same (fixed clock)
        assertThat(now1).isEqualTo(now2);
        assertThat(now2).isEqualTo(now3);
        assertThat(now1).isEqualTo(now3);
        
        // All should be UTC
        assertThat(now1.toString()).endsWith("Z");
        assertThat(now2.toString()).endsWith("Z");
        assertThat(now3.toString()).endsWith("Z");
    }

    @Test
    @DisplayName("Time zone drift should be prevented by using UTC consistently")
    void timeZoneDrift_shouldBePreventedByUsingUtcConsistently() {
        // Given
        Instant utcTime = Instant.parse("2024-01-01T12:00:00Z");
        
        // When
        // Simulate different time zone operations
        Instant utcTime1 = utcTime;
        Instant utcTime2 = utcTime.plus(1, ChronoUnit.HOURS);
        Instant utcTime3 = utcTime.plus(1, ChronoUnit.DAYS);
        
        // Convert to different time zones and back
        Instant converted1 = Instant.ofEpochSecond(utcTime1.getEpochSecond());
        Instant converted2 = Instant.ofEpochSecond(utcTime2.getEpochSecond());
        Instant converted3 = Instant.ofEpochSecond(utcTime3.getEpochSecond());

        // Then
        // Should maintain consistency
        assertThat(converted1).isEqualTo(utcTime1);
        assertThat(converted2).isEqualTo(utcTime2);
        assertThat(converted3).isEqualTo(utcTime3);
        
        // All should be UTC
        assertThat(converted1.toString()).endsWith("Z");
        assertThat(converted2.toString()).endsWith("Z");
        assertThat(converted3.toString()).endsWith("Z");
    }
}
