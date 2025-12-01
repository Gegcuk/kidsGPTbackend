package uk.gegc.kidsgptbackend.features.subscription.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionPlanRepository;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.test.BaseRepositoryTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserSubscription Entity Tests")
class UserSubscriptionTest extends BaseRepositoryTest {

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    private User testUser;
    private SubscriptionPlan testPlan;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        
        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setHashedPassword("hashedPassword");
        testUser.setCreatedAt(Instant.now());
        testUser = persistAndFlush(testUser);

        // Create test plan
        testPlan = new SubscriptionPlan();
        testPlan.setName("Test Plan");
        testPlan.setPrice(java.math.BigDecimal.valueOf(9.99));
        testPlan.setCurrency("GBP");
        testPlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        testPlan.setMaxKids(5);
        testPlan.setActive(true);
        testPlan.setCreatedAt(Instant.now());
        testPlan = persistAndFlush(testPlan);
    }

    @Test
    @DisplayName("isActive - returns true for ACTIVE status")
    void isActive_returnsTrueForActiveStatus() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.ACTIVE, false, null);

        // When
        boolean result = subscription.isActive();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isActive - returns true for TRIALING status")
    void isActive_returnsTrueForTrialingStatus() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.TRIALING, false, null);

        // When
        boolean result = subscription.isActive();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isActive - returns false for CANCELLED status")
    void isActive_returnsFalseForCancelledStatus() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.CANCELLED, false, null);

        // When
        boolean result = subscription.isActive();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isActive - returns false for EXPIRED status")
    void isActive_returnsFalseForExpiredStatus() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.EXPIRED, false, null);

        // When
        boolean result = subscription.isActive();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isExpired - returns true when currentPeriodEnd is in the past")
    void isExpired_returnsTrueWhenCurrentPeriodEndIsInPast() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.ACTIVE, false, null);
        subscription.setCurrentPeriodEnd(Instant.now().minusSeconds(3600)); // 1 hour ago

        // When
        boolean result = subscription.isExpired();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isExpired - returns false when currentPeriodEnd is in the future")
    void isExpired_returnsFalseWhenCurrentPeriodEndIsInFuture() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.ACTIVE, false, null);
        subscription.setCurrentPeriodEnd(Instant.now().plusSeconds(3600)); // 1 hour from now

        // When
        boolean result = subscription.isExpired();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isExpired - returns false when currentPeriodEnd is null")
    void isExpired_returnsFalseWhenCurrentPeriodEndIsNull() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.ACTIVE, false, null);
        subscription.setCurrentPeriodEnd(null);

        // When
        boolean result = subscription.isExpired();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isInTrial - returns true when isTrial is true and trialEndDate is in the future")
    void isInTrial_returnsTrueWhenIsTrialTrueAndTrialEndDateInFuture() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.TRIALING, true, Instant.now().plusSeconds(86400));

        // When
        boolean result = subscription.isInTrial();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isInTrial - returns false when isTrial is false")
    void isInTrial_returnsFalseWhenIsTrialFalse() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.ACTIVE, false, Instant.now().plusSeconds(86400));

        // When
        boolean result = subscription.isInTrial();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isInTrial - returns false when trialEndDate is null")
    void isInTrial_returnsFalseWhenTrialEndDateIsNull() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.TRIALING, true, null);

        // When
        boolean result = subscription.isInTrial();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isInTrial - returns false when trialEndDate is in the past")
    void isInTrial_returnsFalseWhenTrialEndDateIsInPast() {
        // Given
        UserSubscription subscription = createSubscription(UserSubscription.SubscriptionStatus.TRIALING, true, Instant.now().minusSeconds(86400));

        // When
        boolean result = subscription.isInTrial();

        // Then
        assertThat(result).isFalse();
    }

    private UserSubscription createSubscription(UserSubscription.SubscriptionStatus status, boolean isTrial, Instant trialEndDate) {
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(testUser);
        subscription.setSubscriptionPlan(testPlan);
        subscription.setStatus(status);
        subscription.setStartDate(Instant.now());
        subscription.setTrial(isTrial);
        subscription.setTrialEndDate(trialEndDate);
        subscription.setExternalSubscriptionId("external_" + UUID.randomUUID());
        subscription.setCreatedAt(Instant.now());
        return persistAndFlush(subscription);
    }
}

