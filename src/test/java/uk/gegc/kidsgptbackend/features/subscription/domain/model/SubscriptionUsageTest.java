package uk.gegc.kidsgptbackend.features.subscription.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionUsageRepository;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.test.BaseRepositoryTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubscriptionUsage Entity Tests")
class SubscriptionUsageTest extends BaseRepositoryTest {

    private User testUser;

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
    }

    @Test
    @DisplayName("hasReachedLimit - returns true when usedCount equals limitCount")
    void hasReachedLimit_returnsTrueWhenUsedCountEqualsLimitCount() {
        // Given
        SubscriptionUsage usage = createUsage(10, 10); // usedCount = limitCount

        // When
        boolean result = usage.hasReachedLimit();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasReachedLimit - returns true when usedCount exceeds limitCount")
    void hasReachedLimit_returnsTrueWhenUsedCountExceedsLimitCount() {
        // Given
        SubscriptionUsage usage = createUsage(11, 10); // usedCount > limitCount

        // When
        boolean result = usage.hasReachedLimit();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasReachedLimit - returns false when usedCount is less than limitCount")
    void hasReachedLimit_returnsFalseWhenUsedCountLessThanLimitCount() {
        // Given
        SubscriptionUsage usage = createUsage(5, 10); // usedCount < limitCount

        // When
        boolean result = usage.hasReachedLimit();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasReachedLimit - returns false when limitCount is null")
    void hasReachedLimit_returnsFalseWhenLimitCountIsNull() {
        // Given
        SubscriptionUsage usage = createUsage(5, null);

        // When
        boolean result = usage.hasReachedLimit();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasReachedLimit - returns false when limitCount is zero")
    void hasReachedLimit_returnsFalseWhenLimitCountIsZero() {
        // Given
        SubscriptionUsage usage = createUsage(5, 0);

        // When
        boolean result = usage.hasReachedLimit();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasReachedLimit - returns false when limitCount is negative")
    void hasReachedLimit_returnsFalseWhenLimitCountIsNegative() {
        // Given
        SubscriptionUsage usage = createUsage(5, -1);

        // When
        boolean result = usage.hasReachedLimit();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasReachedLimit - returns false when usedCount is zero and limitCount is zero")
    void hasReachedLimit_returnsFalseWhenUsedCountZeroAndLimitCountZero() {
        // Given
        SubscriptionUsage usage = createUsage(0, 0);

        // When
        boolean result = usage.hasReachedLimit();

        // Then
        assertThat(result).isFalse(); // limitCount > 0 is false, so hasReachedLimit returns false
    }

    @Test
    @DisplayName("getRemainingUsage - returns correct remaining when under limit")
    void getRemainingUsage_returnsCorrectRemainingWhenUnderLimit() {
        // Given
        SubscriptionUsage usage = createUsage(3, 10);

        // When
        int result = usage.getRemainingUsage();

        // Then
        assertThat(result).isEqualTo(7); // 10 - 3
    }

    @Test
    @DisplayName("getRemainingUsage - returns zero when at limit")
    void getRemainingUsage_returnsZeroWhenAtLimit() {
        // Given
        SubscriptionUsage usage = createUsage(10, 10);

        // When
        int result = usage.getRemainingUsage();

        // Then
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("getRemainingUsage - returns Integer.MAX_VALUE when limitCount is null")
    void getRemainingUsage_returnsMaxValueWhenLimitCountIsNull() {
        // Given
        SubscriptionUsage usage = createUsage(5, null);

        // When
        int result = usage.getRemainingUsage();

        // Then
        assertThat(result).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("getRemainingUsage - returns Integer.MAX_VALUE when limitCount is -1 (unlimited)")
    void getRemainingUsage_returnsMaxValueWhenLimitCountIsUnlimited() {
        // Given
        SubscriptionUsage usage = createUsage(5, -1);

        // When
        int result = usage.getRemainingUsage();

        // Then
        assertThat(result).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("getRemainingUsage - returns zero when usedCount exceeds limitCount")
    void getRemainingUsage_returnsZeroWhenUsedCountExceedsLimitCount() {
        // Given
        SubscriptionUsage usage = createUsage(15, 10);

        // When
        int result = usage.getRemainingUsage();

        // Then
        assertThat(result).isEqualTo(0); // max(0, 10 - 15) = 0
    }

    private SubscriptionUsage createUsage(Integer usedCount, Integer limitCount) {
        SubscriptionUsage usage = new SubscriptionUsage();
        usage.setUser(testUser);
        usage.setFeature("chat_limit");
        usage.setPeriodKey("2025-01");
        usage.setUsedCount(usedCount);
        usage.setLimitCount(limitCount);
        usage.setPeriodStart(Instant.now().minusSeconds(86400));
        usage.setPeriodEnd(Instant.now().plusSeconds(86400));
        usage.setCreatedAt(Instant.now());
        return persistAndFlush(usage);
    }
}

