package uk.gegc.kidsgptbackend.features.subscription.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionUsage;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionUsageRepository;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SubscriptionUsageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SubscriptionUsageRepository subscriptionUsageRepository;

    private User testUser;
    private User anotherUser;
    private SubscriptionUsage activeUsage;
    private SubscriptionUsage expiredUsage;
    private SubscriptionUsage anotherFeatureUsage;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        String currentPeriodKey = "PERIOD_" + UUID.randomUUID();
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

        // Create active usage (current period)
        activeUsage = new SubscriptionUsage();
        activeUsage.setUser(testUser);
        activeUsage.setFeature("chat_limit");
        activeUsage.setPeriodKey(currentPeriodKey);
        activeUsage.setUsedCount(5);
        activeUsage.setPeriodStart(now.minusSeconds(86400)); // 1 day ago
        activeUsage.setPeriodEnd(now.plusSeconds(172800)); // 2 days from now
        activeUsage.setCreatedAt(now);
        activeUsage.setUpdatedAt(now);
        entityManager.persistAndFlush(activeUsage);

        // Create expired usage
        expiredUsage = new SubscriptionUsage();
        expiredUsage.setUser(anotherUser);
        expiredUsage.setFeature("chat_limit");
        expiredUsage.setPeriodKey("EXPIRED_" + anotherUser.getId());
        expiredUsage.setUsedCount(10);
        expiredUsage.setPeriodStart(now.minusSeconds(691200)); // 8 days ago
        expiredUsage.setPeriodEnd(now.minusSeconds(86400)); // 1 day ago (expired)
        expiredUsage.setCreatedAt(now);
        expiredUsage.setUpdatedAt(now);
        entityManager.persistAndFlush(expiredUsage);

        // Create usage for different feature
        anotherFeatureUsage = new SubscriptionUsage();
        anotherFeatureUsage.setUser(testUser);
        anotherFeatureUsage.setFeature("story_generation");
        anotherFeatureUsage.setPeriodKey(currentPeriodKey);
        anotherFeatureUsage.setUsedCount(3);
        anotherFeatureUsage.setPeriodStart(now.minusSeconds(86400)); // 1 day ago
        anotherFeatureUsage.setPeriodEnd(now.plusSeconds(172800)); // 2 days from now
        anotherFeatureUsage.setCreatedAt(now);
        anotherFeatureUsage.setUpdatedAt(now);
        entityManager.persistAndFlush(anotherFeatureUsage);

        entityManager.clear();
    }

    @Test
    @DisplayName("incrementUsage atomically increments existing row and returns 1")
    void incrementUsage_atomicallyIncrementsExistingRowAndReturns1() {
        // Given
        Instant now = Instant.now();
        int initialCount = activeUsage.getUsedCount();

        // When
        int result = subscriptionUsageRepository.incrementUsage(
                testUser, "chat_limit", activeUsage.getPeriodKey(), now);

        // Then
        assertThat(result).isEqualTo(1); // 1 row updated

        // Verify the count was incremented
        Optional<SubscriptionUsage> updated = subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, "chat_limit", activeUsage.getPeriodKey());
        assertThat(updated).isPresent();
        assertThat(updated.get().getUsedCount()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("incrementUsage returns 0 when row does not exist")
    void incrementUsage_returns0WhenRowDoesNotExist() {
        // Given
        Instant now = Instant.now();
        String nonExistentPeriodKey = "NONEXISTENT_" + testUser.getId() + "_" + now.getEpochSecond();

        // When
        int result = subscriptionUsageRepository.incrementUsage(
                testUser, "chat_limit", nonExistentPeriodKey, now);

        // Then
        assertThat(result).isEqualTo(0); // 0 rows updated
    }

    @Test
    @DisplayName("unique constraint on user, feature, period_key is enforced")
    void uniqueConstraint_onUserFeaturePeriodKey_isEnforced() {
        // Given - Try to create duplicate usage record
        SubscriptionUsage duplicate = new SubscriptionUsage();
        duplicate.setUser(testUser);
        duplicate.setFeature("chat_limit");
        duplicate.setPeriodKey(activeUsage.getPeriodKey()); // Same as existing
        duplicate.setUsedCount(1);
        duplicate.setPeriodStart(Instant.now().minusSeconds(86400));
        duplicate.setPeriodEnd(Instant.now().plusSeconds(172800));
        duplicate.setCreatedAt(Instant.now());
        duplicate.setUpdatedAt(Instant.now());

        // When & Then
        try {
            entityManager.persistAndFlush(duplicate);
            entityManager.clear();
            // If we get here, the constraint didn't work
            assertThat(false).as("Expected constraint violation for duplicate user/feature/period combination").isTrue();
        } catch (Exception e) {
            // Expected - constraint violation
            assertThat(e.getMessage()).contains("Unique index or primary key violation");
        }
    }

    @Test
    @DisplayName("findExpiredUsagePeriods returns only expired periods")
    void findExpiredUsagePeriods_returnsOnlyExpiredPeriods() {
        // When
        List<SubscriptionUsage> result = subscriptionUsageRepository.findExpiredUsagePeriods(Instant.now());

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPeriodEnd()).isBefore(Instant.now());
        assertThat(result.get(0).getUser().getId()).isEqualTo(anotherUser.getId());
    }

    @Test
    @DisplayName("findExpiredUsagePeriods excludes active periods")
    void findExpiredUsagePeriods_excludesActivePeriods() {
        // When
        List<SubscriptionUsage> result = subscriptionUsageRepository.findExpiredUsagePeriods(Instant.now());

        // Then
        assertThat(result).allMatch(usage -> usage.getPeriodEnd().isBefore(Instant.now()));
        assertThat(result).noneMatch(usage -> usage.getUser().getId().equals(testUser.getId()));
    }

    @Test
    @DisplayName("getTotalUsageForPeriod sums correctly for existing period")
    void getTotalUsageForPeriod_sumsCorrectlyForExistingPeriod() {
        // When
        Integer total = subscriptionUsageRepository.getTotalUsageForPeriod(
                testUser, "chat_limit", activeUsage.getPeriodKey());

        // Then
        assertThat(total).isEqualTo(5); // Matches the usedCount of activeUsage
    }

    @Test
    @DisplayName("getTotalUsageForPeriod returns 0 for non-existent period")
    void getTotalUsageForPeriod_returns0ForNonExistentPeriod() {
        // Given
        String nonExistentPeriodKey = "NONEXISTENT_" + testUser.getId() + "_" + Instant.now().getEpochSecond();

        // When
        Integer total = subscriptionUsageRepository.getTotalUsageForPeriod(
                testUser, "chat_limit", nonExistentPeriodKey);

        // Then
        assertThat(total).isEqualTo(0);
    }

    @Test
    @DisplayName("getTotalUsageForPeriod returns 0 for different feature")
    void getTotalUsageForPeriod_returns0ForDifferentFeature() {
        // When
        Integer total = subscriptionUsageRepository.getTotalUsageForPeriod(
                testUser, "different_feature", activeUsage.getPeriodKey());

        // Then
        assertThat(total).isEqualTo(0);
    }

    @Test
    @DisplayName("findByUserAndFeatureAndPeriodKey returns correct usage")
    void findByUserAndFeatureAndPeriodKey_returnsCorrectUsage() {
        // When
        Optional<SubscriptionUsage> result = subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, "chat_limit", activeUsage.getPeriodKey());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUsedCount()).isEqualTo(5);
        assertThat(result.get().getFeature()).isEqualTo("chat_limit");
        assertThat(result.get().getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("findByUserAndFeatureAndPeriodKey returns empty when not found")
    void findByUserAndFeatureAndPeriodKey_returnsEmptyWhenNotFound() {
        // When
        Optional<SubscriptionUsage> result = subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, "nonexistent_feature", "nonexistent_period");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByUserAndPeriodKey returns all features for user and period")
    void findByUserAndPeriodKey_returnsAllFeaturesForUserAndPeriod() {
        // When
        List<SubscriptionUsage> result = subscriptionUsageRepository.findByUserAndPeriodKey(
                testUser, activeUsage.getPeriodKey());

        // Then
        assertThat(result).hasSize(2); // chat_limit and story_generation
        assertThat(result).extracting(SubscriptionUsage::getFeature)
                .containsExactlyInAnyOrder("chat_limit", "story_generation");
        assertThat(result).allMatch(usage -> usage.getUser().getId().equals(testUser.getId()));
    }

    @Test
    @DisplayName("deleteByPeriodKey removes all usage records for period")
    void deleteByPeriodKey_removesAllUsageRecordsForPeriod() {
        // Given
        String periodKey = activeUsage.getPeriodKey();
        assertThat(subscriptionUsageRepository.findByUserAndPeriodKey(testUser, periodKey)).hasSize(2);

        // When
        subscriptionUsageRepository.deleteByPeriodKey(periodKey);
        entityManager.flush();
        entityManager.clear();

        // Then
        List<SubscriptionUsage> remaining = subscriptionUsageRepository.findByUserAndPeriodKey(testUser, periodKey);
        assertThat(remaining).isEmpty();
    }

    @Test
    @DisplayName("multiple increments work correctly")
    void multipleIncrements_workCorrectly() {
        // Given
        Instant now = Instant.now();
        int initialCount = activeUsage.getUsedCount();

        // When - Increment multiple times
        int result1 = subscriptionUsageRepository.incrementUsage(
                testUser, "chat_limit", activeUsage.getPeriodKey(), now);
        int result2 = subscriptionUsageRepository.incrementUsage(
                testUser, "chat_limit", activeUsage.getPeriodKey(), now);
        int result3 = subscriptionUsageRepository.incrementUsage(
                testUser, "chat_limit", activeUsage.getPeriodKey(), now);

        // Then
        assertThat(result1).isEqualTo(1);
        assertThat(result2).isEqualTo(1);
        assertThat(result3).isEqualTo(1);

        // Verify final count
        Optional<SubscriptionUsage> updated = subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, "chat_limit", activeUsage.getPeriodKey());
        assertThat(updated).isPresent();
        assertThat(updated.get().getUsedCount()).isEqualTo(initialCount + 3);
    }
}
