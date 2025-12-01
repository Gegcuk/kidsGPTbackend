package uk.gegc.kidsgptbackend.features.subscription.application.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionUsage;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionUsageRepository;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.family.application.KidCountingService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionAccessServiceImplTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionUsageRepository subscriptionUsageRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KidCountingService kidCountingService;

    @InjectMocks
    private SubscriptionAccessServiceImpl subscriptionAccessService;

    private User testUser;
    private UserSubscription activeSubscription;
    private SubscriptionPlan plusMonthlyPlan;
    private SubscriptionUsage existingUsage;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setCreatedAt(Instant.now().minusSeconds(86400)); // 1 day ago

        // Create subscription plan
        plusMonthlyPlan = new SubscriptionPlan();
        plusMonthlyPlan.setId(UUID.randomUUID());
        plusMonthlyPlan.setName("Plus Monthly");
        plusMonthlyPlan.setPrice(new BigDecimal("4.99"));
        plusMonthlyPlan.setCurrency("GBP");
        plusMonthlyPlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        plusMonthlyPlan.setMaxKids(10);
        plusMonthlyPlan.setFeatures("{\"chat_limit\": -1}"); // Unlimited
        plusMonthlyPlan.setGooglePlayProductId("plus_monthly");
        plusMonthlyPlan.setActive(true);

        // Create active subscription
        activeSubscription = new UserSubscription();
        activeSubscription.setId(UUID.randomUUID());
        activeSubscription.setUser(testUser);
        activeSubscription.setSubscriptionPlan(plusMonthlyPlan);
        activeSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        activeSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        activeSubscription.setExternalSubscriptionId("purchase_token_123");
        activeSubscription.setCurrentPeriodStart(Instant.now().minusSeconds(86400)); // 1 day ago
        activeSubscription.setCurrentPeriodEnd(Instant.now().plusSeconds(2592000)); // 30 days from now
        activeSubscription.setAutoRenew(true);
        activeSubscription.setStartDate(Instant.now().minusSeconds(86400));

        // Create existing usage record
        existingUsage = new SubscriptionUsage();
        existingUsage.setId(UUID.randomUUID());
        existingUsage.setUser(testUser);
        existingUsage.setFeature("chat_limit");
        existingUsage.setPeriodKey("FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond());
        existingUsage.setUsedCount(5);
        existingUsage.setLimitCount(15);
        existingUsage.setPeriodStart(testUser.getCreatedAt());
        existingUsage.setPeriodEnd(testUser.getCreatedAt().plus(3, ChronoUnit.DAYS));
    }

    // 4.1 Free window logic tests

    @Test
    @DisplayName("hasFeatureAccess - returns true for chat_limit within 3-day free window with remaining usage")
    void hasFeatureAccess_returnsTrueForChatLimitWithinFreeWindowWithRemainingUsage() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, "chat_limit", "FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()))
                .thenReturn(Optional.of(existingUsage));

        // When
        boolean result = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasFeatureAccess - returns false for chat_limit after 3-day free window")
    void hasFeatureAccess_returnsFalseForChatLimitAfterFreeWindow() {
        // Given
        testUser.setCreatedAt(Instant.now().minusSeconds(4 * 86400)); // 4 days ago (outside free window)
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());

        // When
        boolean result = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasFeatureAccess - returns false for non-chat features in free tier")
    void hasFeatureAccess_returnsFalseForNonChatFeaturesInFreeTier() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());

        // When
        boolean result = subscriptionAccessService.hasFeatureAccess(testUser, "image_generation");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getRemainingUsage - creates new SubscriptionUsage with correct free period for new user")
    void getRemainingUsage_createsNewSubscriptionUsageWithCorrectFreePeriodForNewUser() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, "chat_limit", "FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()))
                .thenReturn(Optional.empty());
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenReturn(existingUsage);

        // When
        int result = subscriptionAccessService.getRemainingUsage(testUser, "chat_limit");

        // Then
        assertThat(result).isEqualTo(10); // 15 - 5 = 10

        ArgumentCaptor<SubscriptionUsage> captor = ArgumentCaptor.forClass(SubscriptionUsage.class);
        verify(subscriptionUsageRepository).save(captor.capture());
        SubscriptionUsage savedUsage = captor.getValue();
        assertThat(savedUsage.getUser()).isEqualTo(testUser);
        assertThat(savedUsage.getFeature()).isEqualTo("chat_limit");
        assertThat(savedUsage.getPeriodKey()).isEqualTo("FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond());
        assertThat(savedUsage.getLimitCount()).isEqualTo(15);
        assertThat(savedUsage.getUsedCount()).isEqualTo(0);
        assertThat(savedUsage.getPeriodStart()).isEqualTo(testUser.getCreatedAt());
        assertThat(savedUsage.getPeriodEnd()).isEqualTo(testUser.getCreatedAt().plus(3, ChronoUnit.DAYS));
    }

    // 4.2 Free limit behavior tests

    @Test
    @DisplayName("hasReachedUsageLimit - returns true when free limit of 15 is reached")
    void hasReachedUsageLimit_returnsTrueWhenFreeLimitReached() {
        // Given
        existingUsage.setUsedCount(15); // Reached limit
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, "chat_limit", "FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()))
                .thenReturn(Optional.of(existingUsage));

        // When
        boolean result = subscriptionAccessService.hasReachedUsageLimit(testUser, "chat_limit");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasFeatureAccess - returns false when free limit of 15 is reached")
    void hasFeatureAccess_returnsFalseWhenFreeLimitReached() {
        // Given
        existingUsage.setUsedCount(15); // Reached limit
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, "chat_limit", "FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()))
                .thenReturn(Optional.of(existingUsage));

        // When
        boolean result = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then
        assertThat(result).isFalse();
    }

    // 4.3 Paid plan behavior tests

    @Test
    @DisplayName("getRemainingUsage - returns Integer.MAX_VALUE for unlimited paid plan")
    void getRemainingUsage_returnsMaxValueForUnlimitedPaidPlan() throws JsonProcessingException {
        // Given
        JsonNode featuresNode = mock(JsonNode.class);
        JsonNode chatLimitNode = mock(JsonNode.class);
        when(objectMapper.readTree("{\"chat_limit\": -1}")).thenReturn(featuresNode);
        when(featuresNode.get("chat_limit")).thenReturn(chatLimitNode);
        when(chatLimitNode.isNumber()).thenReturn(true);
        when(chatLimitNode.asInt()).thenReturn(-1);

        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.of(activeSubscription));

        // When
        int result = subscriptionAccessService.getRemainingUsage(testUser, "chat_limit");

        // Then
        assertThat(result).isEqualTo(Integer.MAX_VALUE);
        verify(subscriptionUsageRepository, never()).findByUserAndFeatureAndPeriodKey(any(), any(), any());
    }

    @Test
    @DisplayName("incrementUsage - does not cap for unlimited paid plan")
    void incrementUsage_doesNotCapForUnlimitedPaidPlan() throws JsonProcessingException {
        // Given
        JsonNode featuresNode = mock(JsonNode.class);
        JsonNode chatLimitNode = mock(JsonNode.class);
        when(objectMapper.readTree("{\"chat_limit\": -1}")).thenReturn(featuresNode);
        when(featuresNode.get("chat_limit")).thenReturn(chatLimitNode);
        when(chatLimitNode.isNumber()).thenReturn(true);
        when(chatLimitNode.asInt()).thenReturn(-1);

        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.incrementUsage(
                eq(testUser), eq("chat_limit"), 
                eq("GOOGLE_PLAY_purchase_token_123_" + activeSubscription.getCurrentPeriodStart().getEpochSecond()),
                any(Instant.class))).thenReturn(1);

        // When
        subscriptionAccessService.incrementUsage(testUser, "chat_limit");

        // Then
        verify(subscriptionUsageRepository).incrementUsage(
                eq(testUser), eq("chat_limit"), 
                eq("GOOGLE_PLAY_purchase_token_123_" + activeSubscription.getCurrentPeriodStart().getEpochSecond()),
                any(Instant.class));
        verify(subscriptionUsageRepository, never()).save(any());
    }

    // 4.4 Atomic increment path tests

    @Test
    @DisplayName("incrementUsage - returns 1 when row exists and is updated")
    void incrementUsage_returnsOneWhenRowExistsAndIsUpdated() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.incrementUsage(
                eq(testUser), eq("chat_limit"), 
                eq("FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()),
                any(Instant.class))).thenReturn(1);

        // When
        subscriptionAccessService.incrementUsage(testUser, "chat_limit");

        // Then
        verify(subscriptionUsageRepository).incrementUsage(
                eq(testUser), eq("chat_limit"), 
                eq("FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()),
                any(Instant.class));
        verify(subscriptionUsageRepository, never()).save(any());
    }

    @Test
    @DisplayName("incrementUsage - returns 0 and creates new row when no row exists")
    void incrementUsage_returnsZeroAndCreatesNewRowWhenNoRowExists() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.incrementUsage(
                eq(testUser), eq("chat_limit"), 
                eq("FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()),
                any(Instant.class))).thenReturn(0);
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenReturn(existingUsage);

        // When
        subscriptionAccessService.incrementUsage(testUser, "chat_limit");

        // Then
        verify(subscriptionUsageRepository).incrementUsage(
                eq(testUser), eq("chat_limit"), 
                eq("FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()),
                any(Instant.class));

        ArgumentCaptor<SubscriptionUsage> captor = ArgumentCaptor.forClass(SubscriptionUsage.class);
        verify(subscriptionUsageRepository, times(2)).save(captor.capture());
        List<SubscriptionUsage> savedUsages = captor.getAllValues();
        SubscriptionUsage finalUsage = savedUsages.get(1); // The second save call
        assertThat(finalUsage.getUsedCount()).isEqualTo(1);
    }

    // 4.5 Reset counters tests

    @Test
    @DisplayName("resetUsageCounters - deletes only expired periods for user")
    void resetUsageCounters_deletesOnlyExpiredPeriodsForUser() {
        // Given
        SubscriptionUsage expiredUsage = new SubscriptionUsage();
        expiredUsage.setId(UUID.randomUUID());
        expiredUsage.setUser(testUser);
        expiredUsage.setFeature("chat_limit");
        expiredUsage.setPeriodEnd(Instant.now().minusSeconds(3600)); // Expired

        when(subscriptionUsageRepository.findExpiredUsagePeriods(any(Instant.class)))
                .thenReturn(List.of(expiredUsage));

        // When
        subscriptionAccessService.resetUsageCounters(testUser);

        // Then
        verify(subscriptionUsageRepository).delete(expiredUsage);
    }

    // 4.6 Time-boundary tests

    @Test
    @DisplayName("hasFeatureAccess - free access ends exactly at createdAt + 3 days (exclusive)")
    void hasFeatureAccess_freeAccessEndsExactlyAtCreatedAtPlus3Days() {
        // Given
        testUser.setCreatedAt(Instant.now().minus(3, ChronoUnit.DAYS)); // Exactly 3 days ago
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());

        // When
        boolean result = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then
        assertThat(result).isFalse(); // Should be false because it's exactly at the boundary (exclusive)
    }

    @Test
    @DisplayName("hasFeatureAccess - free access available just before createdAt + 3 days")
    void hasFeatureAccess_freeAccessAvailableJustBeforeCreatedAtPlus3Days() {
        // Given
        testUser.setCreatedAt(Instant.now().minus(3, ChronoUnit.DAYS).plusSeconds(1)); // Just under 3 days ago
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, "chat_limit", "FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()))
                .thenReturn(Optional.of(existingUsage));

        // When
        boolean result = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then
        assertThat(result).isTrue(); // Should be true because it's just before the boundary
    }

    // Additional comprehensive tests

    @Test
    @DisplayName("canPerformAction - maps actions to features correctly")
    void canPerformAction_mapsActionsToFeaturesCorrectly() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                testUser, "chat_limit", "FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()))
                .thenReturn(Optional.of(existingUsage));
        when(kidCountingService.canAddMoreKids(testUser)).thenReturn(true);

        // When & Then
        assertThat(subscriptionAccessService.canPerformAction(testUser, "chat")).isTrue();
        assertThat(subscriptionAccessService.canPerformAction(testUser, "image_generation")).isFalse();
        assertThat(subscriptionAccessService.canPerformAction(testUser, "story_continuation")).isFalse();
        assertThat(subscriptionAccessService.canPerformAction(testUser, "add_kid")).isTrue(); // Free tier allows 1 kid
        assertThat(subscriptionAccessService.canPerformAction(testUser, "unknown_action")).isFalse();
    }

    @Test
    @DisplayName("getRemainingUsage - returns 0 for features not in plan")
    void getRemainingUsage_returnsZeroForFeaturesNotInPlan() throws JsonProcessingException {
        // Given
        JsonNode featuresNode = mock(JsonNode.class);
        when(objectMapper.readTree(anyString())).thenReturn(featuresNode);
        when(featuresNode.get("image_generation")).thenReturn(null);

        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), any(), any())).thenReturn(Optional.empty());
        
        // Create a usage record with limit 0 (feature not in plan)
        SubscriptionUsage usageWithZeroLimit = new SubscriptionUsage();
        usageWithZeroLimit.setUser(testUser);
        usageWithZeroLimit.setFeature("image_generation");
        usageWithZeroLimit.setUsedCount(0);
        usageWithZeroLimit.setLimitCount(0); // Feature not in plan = 0 limit
        usageWithZeroLimit.setPeriodKey("GOOGLE_PLAY_token_1234567890");
        usageWithZeroLimit.setPeriodStart(Instant.now().minus(1, ChronoUnit.DAYS));
        usageWithZeroLimit.setPeriodEnd(Instant.now().plus(1, ChronoUnit.DAYS));
        
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenReturn(usageWithZeroLimit);

        // When
        int result = subscriptionAccessService.getRemainingUsage(testUser, "image_generation");

        // Then
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("incrementUsage - creates usage record with correct period for free tier")
    void incrementUsage_createsUsageRecordWithCorrectPeriodForFreeTier() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.incrementUsage(
                eq(testUser), eq("chat_limit"), 
                eq("FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond()),
                any(Instant.class))).thenReturn(0);
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenReturn(existingUsage);

        // When
        subscriptionAccessService.incrementUsage(testUser, "chat_limit");

        // Then
        ArgumentCaptor<SubscriptionUsage> captor = ArgumentCaptor.forClass(SubscriptionUsage.class);
        verify(subscriptionUsageRepository, times(2)).save(captor.capture());
        List<SubscriptionUsage> savedUsages = captor.getAllValues();
        SubscriptionUsage finalUsage = savedUsages.get(1); // The second save call
        assertThat(finalUsage.getPeriodKey()).isEqualTo("FREE_" + testUser.getId() + "_" + testUser.getCreatedAt().getEpochSecond());
        assertThat(finalUsage.getPeriodStart()).isEqualTo(testUser.getCreatedAt());
        assertThat(finalUsage.getPeriodEnd()).isEqualTo(testUser.getCreatedAt().plus(3, ChronoUnit.DAYS));
        assertThat(finalUsage.getLimitCount()).isEqualTo(15);
        assertThat(finalUsage.getUsedCount()).isEqualTo(1);
    }
}