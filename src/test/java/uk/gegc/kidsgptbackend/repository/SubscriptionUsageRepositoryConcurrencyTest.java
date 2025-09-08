package uk.gegc.kidsgptbackend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionUsage;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.subscription.SubscriptionUsageRepository;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("SubscriptionUsageRepository Concurrency Tests")
class SubscriptionUsageRepositoryConcurrencyTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SubscriptionUsageRepository subscriptionUsageRepository;

    private User testUser;
    private final String testFeature = "chat_limit";
    private final String testPeriodKey = "2025-01";

    @BeforeEach
    void setUp() {
        // Create test user for each test method
        testUser = createTestUser();
        entityManager.persistAndFlush(testUser);
        entityManager.clear();
    }

    private User createTestUser() {
        User user = new User();
        // Don't set ID - let Hibernate generate it
        user.setUsername("testuser_" + System.currentTimeMillis());
        user.setEmail("test_" + System.currentTimeMillis() + "@example.com");
        user.setHashedPassword("password");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        return user;
    }

    @Test
    @DisplayName("Sequential incrementUsage with existing record - increments are accurate")
    void sequentialIncrementUsage_existingRecord_incrementsAreAccurate() {
        // Given - create existing usage record first
        SubscriptionUsage existingUsage = new SubscriptionUsage();
        existingUsage.setUser(testUser);
        existingUsage.setFeature(testFeature);
        existingUsage.setPeriodKey(testPeriodKey);
        existingUsage.setUsedCount(0);
        existingUsage.setLimitCount(100);
        existingUsage.setPeriodStart(Instant.now());
        existingUsage.setPeriodEnd(Instant.now().plusSeconds(86400));
        existingUsage.setCreatedAt(Instant.now());
        existingUsage.setUpdatedAt(Instant.now());

        entityManager.persistAndFlush(existingUsage);

        // When - simulate sequential increment calls
        int totalIncrements = 0;
        for (int i = 0; i < 10; i++) {
            int result = subscriptionUsageRepository.incrementUsage(
                    testUser, testFeature, testPeriodKey, Instant.now());
            if (result == 1) {
                totalIncrements++;
            }
        }

        // Then - verify all increments succeeded
        assertThat(totalIncrements).isEqualTo(10);

        // Verify the usage count is correct (should be 10)
        var usageRecord = subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, testFeature, testPeriodKey);
        assertThat(usageRecord).isPresent();
        assertThat(usageRecord.get().getUsedCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("Multiple incrementUsage calls - no unique constraint violations")
    void multipleIncrementUsageCalls_noUniqueConstraintViolations() {
        // Given - create existing usage record first
        SubscriptionUsage existingUsage = new SubscriptionUsage();
        existingUsage.setUser(testUser);
        existingUsage.setFeature(testFeature);
        existingUsage.setPeriodKey(testPeriodKey);
        existingUsage.setUsedCount(0);
        existingUsage.setLimitCount(100);
        existingUsage.setPeriodStart(Instant.now());
        existingUsage.setPeriodEnd(Instant.now().plusSeconds(86400));
        existingUsage.setCreatedAt(Instant.now());
        existingUsage.setUpdatedAt(Instant.now());

        entityManager.persistAndFlush(existingUsage);

        // When - simulate multiple increment calls
        int totalIncrements = 0;
        for (int i = 0; i < 20; i++) {
            int result = subscriptionUsageRepository.incrementUsage(
                    testUser, testFeature, testPeriodKey, Instant.now());
            if (result == 1) {
                totalIncrements++;
            }
        }

        // Then - verify all increments succeeded
        assertThat(totalIncrements).isEqualTo(20);

        // Verify the usage count is correct (should be 20)
        var usageRecord = subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, testFeature, testPeriodKey);
        assertThat(usageRecord).isPresent();
        assertThat(usageRecord.get().getUsedCount()).isEqualTo(20);
    }

    @Test
    @DisplayName("incrementUsage returns 0 when no record exists")
    void incrementUsage_returns0WhenNoRecordExists() {
        // Given - no existing usage record

        // When
        int result = subscriptionUsageRepository.incrementUsage(
                testUser, testFeature, testPeriodKey, Instant.now());

        // Then
        assertThat(result).isEqualTo(0);

        // Verify no usage record was created
        var usageRecord = subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, testFeature, testPeriodKey);
        assertThat(usageRecord).isEmpty();
    }
}
