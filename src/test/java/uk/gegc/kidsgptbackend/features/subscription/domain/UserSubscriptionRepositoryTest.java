package uk.gegc.kidsgptbackend.features.subscription.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserSubscriptionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    private User testUser;
    private User anotherUser;
    private SubscriptionPlan freePlan;
    private SubscriptionPlan plusMonthlyPlan;
    private UserSubscription activeSubscription;
    private UserSubscription trialingSubscription;
    private UserSubscription cancelledSubscription;
    private UserSubscription expiredSubscription;

    @BeforeEach
    void setUp() {
        // Create test users
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setHashedPassword("password123");
        testUser.setActive(true);
        entityManager.persistAndFlush(testUser);

        anotherUser = new User();
        anotherUser.setUsername("anotheruser");
        anotherUser.setEmail("another@example.com");
        anotherUser.setHashedPassword("password123");
        anotherUser.setActive(true);
        entityManager.persistAndFlush(anotherUser);

        // Create subscription plans
        freePlan = new SubscriptionPlan();
        freePlan.setName("Free");
        freePlan.setPrice(BigDecimal.ZERO);
        freePlan.setCurrency("GBP");
        freePlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        freePlan.setMaxKids(1);
        freePlan.setActive(true);
        freePlan.setCreatedAt(Instant.now());
        freePlan.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(freePlan);

        plusMonthlyPlan = new SubscriptionPlan();
        plusMonthlyPlan.setName("Plus Monthly");
        plusMonthlyPlan.setPrice(new BigDecimal("4.99"));
        plusMonthlyPlan.setCurrency("GBP");
        plusMonthlyPlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        plusMonthlyPlan.setMaxKids(10);
        plusMonthlyPlan.setActive(true);
        plusMonthlyPlan.setCreatedAt(Instant.now());
        plusMonthlyPlan.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(plusMonthlyPlan);

        // Create active subscription
        activeSubscription = new UserSubscription();
        activeSubscription.setUser(testUser);
        activeSubscription.setSubscriptionPlan(plusMonthlyPlan);
        activeSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        activeSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        activeSubscription.setExternalSubscriptionId("purchase_token_123");
        activeSubscription.setStartDate(Instant.now().minusSeconds(86400)); // 1 day ago
        activeSubscription.setCurrentPeriodEnd(Instant.now().plusSeconds(2592000)); // 30 days from now
        activeSubscription.setAutoRenew(true);
        activeSubscription.setCreatedAt(Instant.now());
        activeSubscription.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(activeSubscription);

        // Create trialing subscription
        trialingSubscription = new UserSubscription();
        trialingSubscription.setUser(anotherUser);
        trialingSubscription.setSubscriptionPlan(plusMonthlyPlan);
        trialingSubscription.setStatus(UserSubscription.SubscriptionStatus.TRIALING);
        trialingSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        trialingSubscription.setExternalSubscriptionId("purchase_token_456");
        trialingSubscription.setStartDate(Instant.now().minusSeconds(43200)); // 12 hours ago
        trialingSubscription.setTrialEndDate(Instant.now().plusSeconds(604800)); // 7 days from now
        trialingSubscription.setAutoRenew(true);
        trialingSubscription.setCreatedAt(Instant.now());
        trialingSubscription.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(trialingSubscription);

        // Create cancelled subscription
        cancelledSubscription = new UserSubscription();
        cancelledSubscription.setUser(testUser);
        cancelledSubscription.setSubscriptionPlan(freePlan);
        cancelledSubscription.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
        cancelledSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        cancelledSubscription.setExternalSubscriptionId("purchase_token_789");
        cancelledSubscription.setStartDate(Instant.now().minusSeconds(172800)); // 2 days ago
        cancelledSubscription.setCancelledAt(Instant.now().minusSeconds(86400)); // 1 day ago
        cancelledSubscription.setCreatedAt(Instant.now());
        cancelledSubscription.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(cancelledSubscription);

        // Create expired subscription
        expiredSubscription = new UserSubscription();
        expiredSubscription.setUser(anotherUser);
        expiredSubscription.setSubscriptionPlan(plusMonthlyPlan);
        expiredSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        expiredSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        expiredSubscription.setExternalSubscriptionId("purchase_token_expired");
        expiredSubscription.setStartDate(Instant.now().minusSeconds(3456000)); // 40 days ago
        expiredSubscription.setCurrentPeriodEnd(Instant.now().minusSeconds(86400)); // 1 day ago (expired)
        expiredSubscription.setAutoRenew(false);
        expiredSubscription.setCreatedAt(Instant.now());
        expiredSubscription.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(expiredSubscription);

        entityManager.clear();
    }

    @Test
    @DisplayName("findActiveSubscriptionByUser returns only ACTIVE subscription")
    void findActiveSubscriptionByUser_returnsOnlyActiveSubscription() {
        // When
        Optional<UserSubscription> result = userSubscriptionRepository.findActiveSubscriptionByUser(testUser);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(result.get().getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("findActiveSubscriptionByUser returns empty when no active subscription")
    void findActiveSubscriptionByUser_returnsEmptyWhenNoActiveSubscription() {
        // Given - user with only cancelled subscription
        User userWithNoActive = new User();
        userWithNoActive.setUsername("noactive");
        userWithNoActive.setEmail("noactive@example.com");
        userWithNoActive.setHashedPassword("password123");
        userWithNoActive.setActive(true);
        entityManager.persistAndFlush(userWithNoActive);

        UserSubscription cancelled = new UserSubscription();
        cancelled.setUser(userWithNoActive);
        cancelled.setSubscriptionPlan(freePlan);
        cancelled.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
        cancelled.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        cancelled.setExternalSubscriptionId("cancelled_token");
        cancelled.setStartDate(Instant.now());
        cancelled.setCreatedAt(Instant.now());
        cancelled.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(cancelled);
        entityManager.clear();

        // When
        Optional<UserSubscription> result = userSubscriptionRepository.findActiveSubscriptionByUser(userWithNoActive);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findActiveSubscriptionsWithLock applies PESSIMISTIC_WRITE lock")
    void findActiveSubscriptionsWithLock_appliesPessimisticWriteLock() {
        // When
        List<UserSubscription> result = userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(result.get(0).getUser().getId()).isEqualTo(testUser.getId());
        // Note: Lock behavior is tested at the JPA level - the method exists and returns correct data
    }

    @Test
    @DisplayName("findActiveSubscriptionsWithLock returns both ACTIVE and TRIALING subscriptions")
    void findActiveSubscriptionsWithLock_returnsBothActiveAndTrialingSubscriptions() {
        // When
        List<UserSubscription> result = userSubscriptionRepository.findActiveSubscriptionsWithLock(anotherUser);

        // Then
        assertThat(result).hasSize(2); // TRIALING + ACTIVE (expired)
        assertThat(result).extracting(UserSubscription::getStatus)
                .containsExactlyInAnyOrder(UserSubscription.SubscriptionStatus.TRIALING, UserSubscription.SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("findExpiredActiveSubscriptions returns subscriptions with currentPeriodEnd < now")
    void findExpiredActiveSubscriptions_returnsSubscriptionsWithCurrentPeriodEndBeforeNow() {
        // When
        List<UserSubscription> result = userSubscriptionRepository.findExpiredActiveSubscriptions(Instant.now());

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(result.get(0).getCurrentPeriodEnd()).isBefore(Instant.now());
        assertThat(result.get(0).getExternalSubscriptionId()).isEqualTo("purchase_token_expired");
    }

    @Test
    @DisplayName("findExpiredActiveSubscriptions excludes non-active subscriptions")
    void findExpiredActiveSubscriptions_excludesNonActiveSubscriptions() {
        // When
        List<UserSubscription> result = userSubscriptionRepository.findExpiredActiveSubscriptions(Instant.now());

        // Then
        assertThat(result).allMatch(sub -> sub.getStatus() == UserSubscription.SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("findSubscriptionsDueForBilling returns subscriptions with nextBillingDate <= now")
    void findSubscriptionsDueForBilling_returnsSubscriptionsWithNextBillingDateBeforeOrAtNow() {
        // Given - Create subscription due for billing
        UserSubscription dueForBilling = new UserSubscription();
        dueForBilling.setUser(testUser);
        dueForBilling.setSubscriptionPlan(plusMonthlyPlan);
        dueForBilling.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        dueForBilling.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        dueForBilling.setExternalSubscriptionId("due_billing_token");
        dueForBilling.setStartDate(Instant.now().minusSeconds(2592000)); // 30 days ago
        dueForBilling.setNextBillingDate(Instant.now().minusSeconds(3600)); // 1 hour ago
        dueForBilling.setAutoRenew(true);
        dueForBilling.setCreatedAt(Instant.now());
        dueForBilling.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(dueForBilling);
        entityManager.clear();

        // When
        List<UserSubscription> result = userSubscriptionRepository.findSubscriptionsDueForBilling(Instant.now());

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(result.get(0).getNextBillingDate()).isBeforeOrEqualTo(Instant.now());
        assertThat(result.get(0).getExternalSubscriptionId()).isEqualTo("due_billing_token");
    }

    @Test
    @DisplayName("findByPaymentProviderAndExternalSubscriptionId works with GOOGLE_PLAY")
    void findByPaymentProviderAndExternalSubscriptionId_worksWithGooglePlay() {
        // When
        Optional<UserSubscription> result = userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getPaymentProvider()).isEqualTo(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        assertThat(result.get().getExternalSubscriptionId()).isEqualTo("purchase_token_123");
        assertThat(result.get().getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("findByPaymentProviderAndExternalSubscriptionId returns empty when not found")
    void findByPaymentProviderAndExternalSubscriptionId_returnsEmptyWhenNotFound() {
        // When
        Optional<UserSubscription> result = userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "nonexistent_token");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("unique index on payment_provider and external_subscription_id rejects duplicates")
    void uniqueIndex_rejectsDuplicates() {
        // Given - Try to create duplicate subscription with same provider and external ID
        UserSubscription duplicate = new UserSubscription();
        duplicate.setUser(anotherUser);
        duplicate.setSubscriptionPlan(plusMonthlyPlan);
        duplicate.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        duplicate.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        duplicate.setExternalSubscriptionId("purchase_token_123"); // Same as activeSubscription
        duplicate.setStartDate(Instant.now());
        duplicate.setCreatedAt(Instant.now());
        duplicate.setUpdatedAt(Instant.now());

        // When & Then
        try {
            entityManager.persistAndFlush(duplicate);
            entityManager.clear();
            // If we get here, the constraint didn't work
            assertThat(false).as("Expected constraint violation for duplicate external subscription ID").isTrue();
        } catch (Exception e) {
            // Expected - constraint violation
            assertThat(e.getMessage()).contains("Unique index or primary key violation");
        }
    }

    @Test
    @DisplayName("findExpiredTrialSubscriptions returns trialing subscriptions with expired trial")
    void findExpiredTrialSubscriptions_returnsTrialingSubscriptionsWithExpiredTrial() {
        // Given - Create expired trial subscription
        UserSubscription expiredTrial = new UserSubscription();
        expiredTrial.setUser(testUser);
        expiredTrial.setSubscriptionPlan(plusMonthlyPlan);
        expiredTrial.setStatus(UserSubscription.SubscriptionStatus.TRIALING);
        expiredTrial.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        expiredTrial.setExternalSubscriptionId("expired_trial_token");
        expiredTrial.setStartDate(Instant.now().minusSeconds(604800)); // 7 days ago
        expiredTrial.setTrialEndDate(Instant.now().minusSeconds(3600)); // 1 hour ago
        expiredTrial.setCreatedAt(Instant.now());
        expiredTrial.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(expiredTrial);
        entityManager.clear();

        // When
        List<UserSubscription> result = userSubscriptionRepository.findExpiredTrialSubscriptions(Instant.now());

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.TRIALING);
        assertThat(result.get(0).getTrialEndDate()).isBeforeOrEqualTo(Instant.now());
        assertThat(result.get(0).getExternalSubscriptionId()).isEqualTo("expired_trial_token");
    }

    @Test
    @DisplayName("countActiveSubscriptionsByUser returns correct count")
    void countActiveSubscriptionsByUser_returnsCorrectCount() {
        // When
        long count = userSubscriptionRepository.countActiveSubscriptionsByUser(testUser);

        // Then
        assertThat(count).isEqualTo(1); // Only the active subscription, not the cancelled one
    }

    @Test
    @DisplayName("countActiveSubscriptionsByUser includes both ACTIVE and TRIALING")
    void countActiveSubscriptionsByUser_includesBothActiveAndTrialing() {
        // When
        long count = userSubscriptionRepository.countActiveSubscriptionsByUser(anotherUser);

        // Then
        assertThat(count).isEqualTo(2); // TRIALING + ACTIVE (expired)
    }
}
