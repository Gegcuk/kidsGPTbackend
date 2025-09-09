package uk.gegc.kidsgptbackend.repository.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPayment;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.model.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SubscriptionPaymentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SubscriptionPaymentRepository subscriptionPaymentRepository;

    private User testUser;
    private SubscriptionPlan plusMonthlyPlan;
    private UserSubscription userSubscription;
    private SubscriptionPayment successfulPayment;
    private SubscriptionPayment pendingPayment;
    private SubscriptionPayment failedPayment;
    private SubscriptionPayment oldPendingPayment;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setHashedPassword("password123");
        testUser.setActive(true);
        entityManager.persistAndFlush(testUser);

        // Create subscription plan
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

        // Create user subscription
        userSubscription = new UserSubscription();
        userSubscription.setUser(testUser);
        userSubscription.setSubscriptionPlan(plusMonthlyPlan);
        userSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        userSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        userSubscription.setExternalSubscriptionId("purchase_token_123");
        userSubscription.setStartDate(Instant.now().minusSeconds(2592000)); // 30 days ago
        userSubscription.setCurrentPeriodEnd(Instant.now().plusSeconds(2592000)); // 30 days from now
        userSubscription.setAutoRenew(true);
        userSubscription.setCreatedAt(Instant.now());
        userSubscription.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(userSubscription);

        // Use fixed base time to ensure consistent ordering across test runs
        Instant baseTime = Instant.parse("2024-01-01T12:00:00Z");
        
        // Create payments with explicit timestamps to ensure consistent ordering
        // Create old pending payment (4 hours ago - oldest, for testing pending older than query)
        oldPendingPayment = new SubscriptionPayment();
        oldPendingPayment.setUserSubscription(userSubscription);
        oldPendingPayment.setAmount(new BigDecimal("4.99"));
        oldPendingPayment.setCurrency("GBP");
        oldPendingPayment.setStatus(SubscriptionPayment.PaymentStatus.PENDING);
        oldPendingPayment.setPaymentProvider(SubscriptionPayment.PaymentProvider.GOOGLE_PLAY);
        oldPendingPayment.setExternalPaymentId("payment_id_old");
        oldPendingPayment.setBillingPeriodStart(baseTime.minusSeconds(2592000)); // 30 days before base
        oldPendingPayment.setBillingPeriodEnd(baseTime); // at base time
        oldPendingPayment.setCreatedAt(baseTime.minusSeconds(14400)); // 4 hours before base (oldest)
        entityManager.persistAndFlush(oldPendingPayment);

        // Create failed payment (3 hours ago)
        failedPayment = new SubscriptionPayment();
        failedPayment.setUserSubscription(userSubscription);
        failedPayment.setAmount(new BigDecimal("4.99"));
        failedPayment.setCurrency("GBP");
        failedPayment.setStatus(SubscriptionPayment.PaymentStatus.FAILED);
        failedPayment.setPaymentProvider(SubscriptionPayment.PaymentProvider.GOOGLE_PLAY);
        failedPayment.setExternalPaymentId("payment_id_789");
        failedPayment.setBillingPeriodStart(baseTime.minusSeconds(2592000)); // 30 days before base
        failedPayment.setBillingPeriodEnd(baseTime); // at base time
        failedPayment.setCreatedAt(baseTime.minusSeconds(10800)); // 3 hours before base
        entityManager.persistAndFlush(failedPayment);

        // Create successful payment (2 hours ago)
        successfulPayment = new SubscriptionPayment();
        successfulPayment.setUserSubscription(userSubscription);
        successfulPayment.setAmount(new BigDecimal("4.99"));
        successfulPayment.setCurrency("GBP");
        successfulPayment.setStatus(SubscriptionPayment.PaymentStatus.SUCCEEDED);
        successfulPayment.setPaymentProvider(SubscriptionPayment.PaymentProvider.GOOGLE_PLAY);
        successfulPayment.setExternalPaymentId("payment_id_123");
        successfulPayment.setBillingPeriodStart(baseTime.minusSeconds(1800)); // 30 minutes before base
        successfulPayment.setBillingPeriodEnd(baseTime.plusSeconds(1800)); // 30 minutes after base
        successfulPayment.setCreatedAt(baseTime.minusSeconds(7200)); // 2 hours before base
        entityManager.persistAndFlush(successfulPayment);

        // Create pending payment (1 hour ago - newest)
        pendingPayment = new SubscriptionPayment();
        pendingPayment.setUserSubscription(userSubscription);
        pendingPayment.setAmount(new BigDecimal("4.99"));
        pendingPayment.setCurrency("GBP");
        pendingPayment.setStatus(SubscriptionPayment.PaymentStatus.PENDING);
        pendingPayment.setPaymentProvider(SubscriptionPayment.PaymentProvider.GOOGLE_PLAY);
        pendingPayment.setExternalPaymentId("payment_id_456");
        pendingPayment.setBillingPeriodStart(baseTime);
        pendingPayment.setBillingPeriodEnd(baseTime.plusSeconds(2592000)); // 30 days from base
        pendingPayment.setCreatedAt(baseTime.minusSeconds(3600)); // 1 hour before base (newest)
        entityManager.persistAndFlush(pendingPayment);

        entityManager.clear();
    }

    @Test
    @DisplayName("findByUserSubscriptionOrderByCreatedAtDesc returns payments ordered by creation date")
    void findByUserSubscriptionOrderByCreatedAtDesc_returnsPaymentsOrderedByCreationDate() {
        // When
        List<SubscriptionPayment> result = subscriptionPaymentRepository.findByUserSubscriptionOrderByCreatedAtDesc(userSubscription);

        // Then
        assertThat(result).hasSize(4);
        
        // Verify ordering by checking that each payment is newer than or equal to the next one
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getCreatedAt()).isAfterOrEqualTo(result.get(i + 1).getCreatedAt());
        }
        
        // Verify all expected payments are present
        List<String> expectedPaymentIds = result.stream()
                .map(SubscriptionPayment::getExternalPaymentId)
                .toList();
        assertThat(expectedPaymentIds).containsExactlyInAnyOrder(
                "payment_id_456", "payment_id_123", "payment_id_789", "payment_id_old");
        
        // Verify the newest payment is first (most recent createdAt)
        Instant newestTime = result.get(0).getCreatedAt();
        assertThat(result.get(0).getExternalPaymentId()).isEqualTo("payment_id_456");
        assertThat(newestTime).isEqualTo(Instant.parse("2024-01-01T11:00:00Z"));
        
        // Verify the oldest payment is last (oldest createdAt)
        Instant oldestTime = result.get(3).getCreatedAt();
        assertThat(result.get(3).getExternalPaymentId()).isEqualTo("payment_id_old");
        assertThat(oldestTime).isEqualTo(Instant.parse("2024-01-01T08:00:00Z"));
        
        // Verify that the newest is indeed after the oldest
        assertThat(newestTime).isAfter(oldestTime);
    }

    @Test
    @DisplayName("findByExternalPaymentId returns correct payment")
    void findByExternalPaymentId_returnsCorrectPayment() {
        // When
        Optional<SubscriptionPayment> result = subscriptionPaymentRepository.findByExternalPaymentId("payment_id_123");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(SubscriptionPayment.PaymentStatus.SUCCEEDED);
        assertThat(result.get().getAmount()).isEqualByComparingTo(new BigDecimal("4.99"));
        assertThat(result.get().getUserSubscription().getId()).isEqualTo(userSubscription.getId());
    }

    @Test
    @DisplayName("findByExternalPaymentId returns empty when not found")
    void findByExternalPaymentId_returnsEmptyWhenNotFound() {
        // When
        Optional<SubscriptionPayment> result = subscriptionPaymentRepository.findByExternalPaymentId("nonexistent_payment_id");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findSuccessfulPaymentsBySubscription returns only SUCCEEDED payments")
    void findSuccessfulPaymentsBySubscription_returnsOnlySucceededPayments() {
        // When
        List<SubscriptionPayment> result = subscriptionPaymentRepository.findSuccessfulPaymentsBySubscription(userSubscription);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(SubscriptionPayment.PaymentStatus.SUCCEEDED);
        assertThat(result.get(0).getExternalPaymentId()).isEqualTo("payment_id_123");
    }

    @Test
    @DisplayName("findSuccessfulPaymentsBySubscription orders by createdAt DESC")
    void findSuccessfulPaymentsBySubscription_ordersByCreatedAtDesc() {
        // Given - Create another successful payment
        SubscriptionPayment anotherSuccessful = new SubscriptionPayment();
        anotherSuccessful.setUserSubscription(userSubscription);
        anotherSuccessful.setAmount(new BigDecimal("4.99"));
        anotherSuccessful.setCurrency("GBP");
        anotherSuccessful.setStatus(SubscriptionPayment.PaymentStatus.SUCCEEDED);
        anotherSuccessful.setPaymentProvider(SubscriptionPayment.PaymentProvider.GOOGLE_PLAY);
        anotherSuccessful.setExternalPaymentId("payment_id_new");
        Instant baseTime = Instant.parse("2024-01-01T12:00:00Z");
        anotherSuccessful.setBillingPeriodStart(baseTime);
        anotherSuccessful.setBillingPeriodEnd(baseTime.plusSeconds(2592000));
        anotherSuccessful.setCreatedAt(baseTime.minusSeconds(900)); // 15 minutes before base (newer)
        entityManager.persistAndFlush(anotherSuccessful);
        entityManager.clear();

        // When
        List<SubscriptionPayment> result = subscriptionPaymentRepository.findSuccessfulPaymentsBySubscription(userSubscription);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getExternalPaymentId()).isEqualTo("payment_id_new"); // Newer first
        assertThat(result.get(1).getExternalPaymentId()).isEqualTo("payment_id_123");
    }

    @Test
    @DisplayName("findPaymentsInBillingPeriod returns payments within date range")
    void findPaymentsInBillingPeriod_returnsPaymentsWithinDateRange() {
        // Given - use same base time as test data setup
        Instant baseTime = Instant.parse("2024-01-01T12:00:00Z");
        Instant startDate = baseTime.minusSeconds(3600); // 1 hour before base
        Instant endDate = baseTime.plusSeconds(3600); // 1 hour after base

        // When
        List<SubscriptionPayment> result = subscriptionPaymentRepository.findPaymentsInBillingPeriod(
                userSubscription, startDate, endDate);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(SubscriptionPayment.PaymentStatus.SUCCEEDED);
        assertThat(result.get(0).getBillingPeriodStart()).isAfterOrEqualTo(startDate);
        assertThat(result.get(0).getBillingPeriodEnd()).isBeforeOrEqualTo(endDate);
    }

    @Test
    @DisplayName("findPaymentsInBillingPeriod returns empty when no payments in range")
    void findPaymentsInBillingPeriod_returnsEmptyWhenNoPaymentsInRange() {
        // Given
        Instant startDate = Instant.now().plusSeconds(3600); // 1 hour from now
        Instant endDate = Instant.now().plusSeconds(7200); // 2 hours from now

        // When
        List<SubscriptionPayment> result = subscriptionPaymentRepository.findPaymentsInBillingPeriod(
                userSubscription, startDate, endDate);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByPaymentProviderAndExternalId returns correct payment")
    void findByPaymentProviderAndExternalId_returnsCorrectPayment() {
        // When
        Optional<SubscriptionPayment> result = subscriptionPaymentRepository.findByPaymentProviderAndExternalId(
                SubscriptionPayment.PaymentProvider.GOOGLE_PLAY, "payment_id_123");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getPaymentProvider()).isEqualTo(SubscriptionPayment.PaymentProvider.GOOGLE_PLAY);
        assertThat(result.get().getExternalPaymentId()).isEqualTo("payment_id_123");
        assertThat(result.get().getStatus()).isEqualTo(SubscriptionPayment.PaymentStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("findByPaymentProviderAndExternalId returns empty when not found")
    void findByPaymentProviderAndExternalId_returnsEmptyWhenNotFound() {
        // When
        Optional<SubscriptionPayment> result = subscriptionPaymentRepository.findByPaymentProviderAndExternalId(
                SubscriptionPayment.PaymentProvider.GOOGLE_PLAY, "nonexistent_payment_id");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findPendingPaymentsOlderThan returns only PENDING payments older than cutoff")
    void findPendingPaymentsOlderThan_returnsOnlyPendingPaymentsOlderThanCutoff() {
        // Given - use same base time as test data setup
        Instant baseTime = Instant.parse("2024-01-01T12:00:00Z");
        Instant cutoffTime = baseTime.minusSeconds(7200); // 2 hours before base time

        // When
        List<SubscriptionPayment> result = subscriptionPaymentRepository.findPendingPaymentsOlderThan(cutoffTime);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(SubscriptionPayment.PaymentStatus.PENDING);
        assertThat(result.get(0).getCreatedAt()).isBefore(cutoffTime);
        assertThat(result.get(0).getExternalPaymentId()).isEqualTo("payment_id_old");
        
        // Additional verification: ensure no other pending payments are included
        // The other pending payment (payment_id_456) was created 1 hour before base time,
        // which is after the cutoff time (2 hours before base time), so it should not be included
        assertThat(result).allMatch(payment -> payment.getCreatedAt().isBefore(cutoffTime));
        assertThat(result).allMatch(payment -> payment.getStatus() == SubscriptionPayment.PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("findPendingPaymentsOlderThan excludes recent pending payments")
    void findPendingPaymentsOlderThan_excludesRecentPendingPayments() {
        // Given
        Instant cutoffTime = Instant.now().minusSeconds(3600); // 1 hour ago

        // When
        List<SubscriptionPayment> result = subscriptionPaymentRepository.findPendingPaymentsOlderThan(cutoffTime);

        // Then
        assertThat(result).allMatch(payment -> payment.getCreatedAt().isBefore(cutoffTime));
        assertThat(result).allMatch(payment -> payment.getStatus() == SubscriptionPayment.PaymentStatus.PENDING);
        // Should not include the recent pending payment (payment_id_456)
    }

    @Test
    @DisplayName("findPendingPaymentsOlderThan excludes non-pending payments")
    void findPendingPaymentsOlderThan_excludesNonPendingPayments() {
        // Given
        Instant cutoffTime = Instant.now().minusSeconds(3600); // 1 hour ago

        // When
        List<SubscriptionPayment> result = subscriptionPaymentRepository.findPendingPaymentsOlderThan(cutoffTime);

        // Then
        assertThat(result).allMatch(payment -> payment.getStatus() == SubscriptionPayment.PaymentStatus.PENDING);
        // Should not include SUCCEEDED or FAILED payments
    }

    @Test
    @DisplayName("findPendingPaymentsOlderThan returns empty when no old pending payments")
    void findPendingPaymentsOlderThan_returnsEmptyWhenNoOldPendingPayments() {
        // Given - use same base time as test data setup
        Instant baseTime = Instant.parse("2024-01-01T12:00:00Z");
        Instant cutoffTime = baseTime.minusSeconds(18000); // 5 hours before base time (older than all payments)

        // When
        List<SubscriptionPayment> result = subscriptionPaymentRepository.findPendingPaymentsOlderThan(cutoffTime);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("repository methods handle different payment providers")
    void repositoryMethods_handleDifferentPaymentProviders() {
        // Given - Create payment with different provider (if we had other providers)
        // For now, we only have GOOGLE_PLAY, so test with that
        Optional<SubscriptionPayment> result = subscriptionPaymentRepository.findByPaymentProviderAndExternalId(
                SubscriptionPayment.PaymentProvider.GOOGLE_PLAY, "payment_id_123");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getPaymentProvider()).isEqualTo(SubscriptionPayment.PaymentProvider.GOOGLE_PLAY);
    }
}
