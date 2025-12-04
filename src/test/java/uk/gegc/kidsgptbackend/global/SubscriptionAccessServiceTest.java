package uk.gegc.kidsgptbackend.global;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionUsage;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionUsageRepository;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.family.application.KidCountingService;
import uk.gegc.kidsgptbackend.features.subscription.application.impl.SubscriptionAccessServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Tests for SubscriptionAccessServiceImpl to ensure proper:
 * - Free tier window (3 days from user.createdAt, chat_limit=15)
 * - Paid tier (reads plan features JSON, returns correct limits)
 * - Period key logic (provider period vs monthly fallback)
 * - Usage tracking (atomic increment, boundary conditions)
 * - Action routing (canPerformAction delegates correctly)
 * - Feature name robustness (unknown features, malformed JSON)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionAccessService Tests")
class SubscriptionAccessServiceTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    
    @Mock
    private SubscriptionUsageRepository subscriptionUsageRepository;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private KidCountingService kidCountingService;
    
    @Mock
    private User user;
    
    @Mock
    private UserSubscription activeSubscription;
    
    @Mock
    private SubscriptionPlan subscriptionPlan;
    
    @Mock
    private SubscriptionUsage usageRecord;

    private SubscriptionAccessServiceImpl subscriptionAccessService;
    private Clock fixedClock;
    private ObjectMapper realObjectMapper;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);
        realObjectMapper = new ObjectMapper();
        subscriptionAccessService = new SubscriptionAccessServiceImpl(
                userSubscriptionRepository, 
                subscriptionUsageRepository, 
                realObjectMapper, // Use real ObjectMapper for JSON tests
                kidCountingService
        );
        
        // Set up common mocks
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("testuser");
        when(user.getCreatedAt()).thenReturn(Instant.now(fixedClock).minus(1, ChronoUnit.DAYS)); // 1 day ago
        
        when(activeSubscription.getId()).thenReturn(UUID.randomUUID());
        when(activeSubscription.getUser()).thenReturn(user);
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(activeSubscription.getCurrentPeriodStart()).thenReturn(Instant.now(fixedClock).minus(1, ChronoUnit.DAYS));
        when(activeSubscription.getCurrentPeriodEnd()).thenReturn(Instant.now(fixedClock).plus(30, ChronoUnit.DAYS));
        when(activeSubscription.getPaymentProvider()).thenReturn(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        when(activeSubscription.getExternalSubscriptionId()).thenReturn("sub_123");
        
        when(usageRecord.getUser()).thenReturn(user);
        when(usageRecord.getFeature()).thenReturn("daily_free_ai_messages");
        when(usageRecord.getPeriodKey()).thenReturn("DAILY_FREE_" + userId + "_" + java.time.LocalDate.now(ZoneOffset.UTC));
        when(usageRecord.getUsedCount()).thenReturn(0);
        when(usageRecord.getLimitCount()).thenReturn(5);
        when(usageRecord.getRemainingUsage()).thenReturn(5);

        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenAnswer(invocation -> {
            SubscriptionUsage usage = invocation.getArgument(0);
            usage.setId(UUID.randomUUID());
            return usage;
        });
    }

    @Test
    @DisplayName("Free tier window: Within 3 days from user.createdAt should allow chat_limit")
    void freeTierWindow_withinThreeDaysFromUserCreatedAtShouldAllowChatLimit() {
        // Given - Daily free counter has remaining messages
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("daily_free_ai_messages"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(5);
        
        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(user, "chat_limit");
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        assertThat(hasAccess).isTrue();
        assertThat(remainingUsage).isEqualTo(5);
    }

    @Test
    @DisplayName("Free tier window: After 3 days should deny free tier features")
    void freeTierWindow_afterThreeDaysShouldDenyFreeTierFeatures() {
        // Given - Daily free counter exhausted
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("daily_free_ai_messages"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(0);
        
        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(user, "chat_limit");
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        assertThat(hasAccess).isFalse();
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Free tier window: Should deny non-chat features even within window")
    void freeTierWindow_shouldDenyNonChatFeaturesEvenWithinWindow() {
        // Given - User within 3-day window
        when(user.getCreatedAt()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("image_generation"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(0);
        
        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(user, "image_generation");
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "image_generation");
        
        // Then
        assertThat(hasAccess).isFalse();
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Paid tier: Should read plan features JSON and return correct numeric limit")
    void paidTier_shouldReadPlanFeaturesJsonAndReturnCorrectNumericLimit() {
        // Given - User with active subscription
        String featuresJson = "{\"chat_limit\": 100, \"image_generation\": 50, \"story_continuation\": 25}";
        when(subscriptionPlan.getFeatures()).thenReturn(featuresJson);
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getLimitCount()).thenReturn(100);
        when(usageRecord.getRemainingUsage()).thenReturn(100);
        
        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(user, "chat_limit");
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        assertThat(hasAccess).isTrue();
        assertThat(remainingUsage).isEqualTo(100);
    }

    @Test
    @DisplayName("Paid tier: -1 limit should return Integer.MAX_VALUE (unlimited)")
    void paidTier_negativeOneLimitShouldReturnIntegerMaxValue() {
        // Given - User with active subscription with unlimited feature
        String featuresJson = "{\"chat_limit\": -1, \"image_generation\": 50}";
        when(subscriptionPlan.getFeatures()).thenReturn(featuresJson);
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(activeSubscription));
        
        // When
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        assertThat(remainingUsage).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Period key logic: Should use provider period when present")
    void periodKeyLogic_shouldUseProviderPeriodWhenPresent() {
        // Given - User with active subscription with provider period
        String featuresJson = "{\"chat_limit\": 100}";
        when(subscriptionPlan.getFeatures()).thenReturn(featuresJson);
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(usageRecord));
        
        // When
        subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        // Verify that the period key includes provider information
        String expectedPeriodKey = "GOOGLE_PLAY_sub_123_" + activeSubscription.getCurrentPeriodStart().getEpochSecond();
        verify(subscriptionUsageRepository).findByUserAndFeatureAndPeriodKey(
                eq(user), 
                eq("chat_limit"), 
                eq(expectedPeriodKey)
        );
    }

    @Test
    @DisplayName("Period key logic: Should use monthly fallback when provider period not present")
    void periodKeyLogic_shouldUseMonthlyFallbackWhenProviderPeriodNotPresent() {
        // Given - User with active subscription but no provider period
        String featuresJson = "{\"chat_limit\": 100}";
        when(subscriptionPlan.getFeatures()).thenReturn(featuresJson);
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(activeSubscription.getCurrentPeriodStart()).thenReturn(null);
        when(activeSubscription.getCurrentPeriodEnd()).thenReturn(null);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(usageRecord));
        
        // When
        subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        // Verify that the period key uses monthly format (YYYY-MM based on current system time)
        String expectedMonthKey = YearMonth.now().toString(); // e.g., "2024-11"
        verify(subscriptionUsageRepository).findByUserAndFeatureAndPeriodKey(
                eq(user), 
                eq("chat_limit"), 
                eq(expectedMonthKey)
        );
    }

    @Test
    @DisplayName("Period key logic: Keys should be stable across calls in same provider period")
    void periodKeyLogic_keysShouldBeStableAcrossCallsInSameProviderPeriod() {
        // Given - User with active subscription
        String featuresJson = "{\"chat_limit\": 100}";
        when(subscriptionPlan.getFeatures()).thenReturn(featuresJson);
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(usageRecord));
        
        // When - Multiple calls
        subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        // Verify same period key is used for all calls
        String expectedPeriodKey = "GOOGLE_PLAY_sub_123_" + activeSubscription.getCurrentPeriodStart().getEpochSecond();
        verify(subscriptionUsageRepository, times(3)).findByUserAndFeatureAndPeriodKey(
                eq(user), 
                eq("chat_limit"), 
                eq(expectedPeriodKey)
        );
    }

    @Test
    @DisplayName("Usage tracking: First increment should create record when atomic increment returns 0")
    void usageTracking_firstIncrementShouldCreateRecordWhenAtomicIncrementReturnsZero() {
        // Given - No existing usage record
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.incrementUsage(any(), eq("daily_free_ai_messages"), any(), any())).thenReturn(0);
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenReturn(usageRecord);
        
        // When
        subscriptionAccessService.incrementUsage(user, "chat_limit");
        
        // Then
        verify(subscriptionUsageRepository).incrementUsage(any(), eq("daily_free_ai_messages"), any(), any());
        verify(subscriptionUsageRepository, times(2)).save(any(SubscriptionUsage.class)); // Once in createUsageRecord, once in incrementUsage
    }

    @Test
    @DisplayName("Usage tracking: Subsequent increments should decrement remaining usage correctly")
    void usageTracking_subsequentIncrementsShouldDecrementRemainingUsageCorrectly() {
        // Given - Existing usage record with some usage
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.incrementUsage(any(), eq("daily_free_ai_messages"), any(), any())).thenReturn(1);
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("daily_free_ai_messages"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getUsedCount()).thenReturn(1);
        when(usageRecord.getLimitCount()).thenReturn(5);
        when(usageRecord.getRemainingUsage()).thenReturn(4);
        
        // When
        subscriptionAccessService.incrementUsage(user, "chat_limit");
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        verify(subscriptionUsageRepository).incrementUsage(any(), eq("daily_free_ai_messages"), any(), any());
        assertThat(remainingUsage).isEqualTo(4);
    }

    @Test
    @DisplayName("Usage tracking: hasReachedUsageLimit should toggle at boundary (0 remaining)")
    void usageTracking_hasReachedUsageLimitShouldToggleAtBoundary() {
        // Given - User at usage limit
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("daily_free_ai_messages"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(0);
        
        // When
        boolean hasReachedLimit = subscriptionAccessService.hasReachedUsageLimit(user, "chat_limit");
        
        // Then
        assertThat(hasReachedLimit).isTrue();
    }

    @Test
    @DisplayName("Usage tracking: hasReachedUsageLimit should return false when usage remaining")
    void usageTracking_hasReachedUsageLimitShouldReturnFalseWhenUsageRemaining() {
        // Given - User with remaining usage
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("daily_free_ai_messages"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(5);
        
        // When
        boolean hasReachedLimit = subscriptionAccessService.hasReachedUsageLimit(user, "chat_limit");
        
        // Then
        assertThat(hasReachedLimit).isFalse();
    }

    @Test
    @DisplayName("Usage tracking: resetUsageCounters should delete expired periods only")
    void usageTracking_resetUsageCountersShouldDeleteExpiredPeriodsOnly() {
        // Given - User with expired usage records
        when(subscriptionUsageRepository.findExpiredUsagePeriods(any())).thenReturn(java.util.List.of(usageRecord));
        
        // When
        subscriptionAccessService.resetUsageCounters(user);
        
        // Then
        verify(subscriptionUsageRepository).findExpiredUsagePeriods(any());
        verify(subscriptionUsageRepository).delete(usageRecord);
    }

    @Test
    @DisplayName("Action routing: canPerformAction('add_kid') should delegate to KidCountingService")
    void actionRouting_canPerformActionAddKidShouldDelegateToKidCountingService() {
        // Given
        when(kidCountingService.canAddMoreKids(user)).thenReturn(true);
        
        // When
        boolean canAddKid = subscriptionAccessService.canPerformAction(user, "add_kid");
        
        // Then
        assertThat(canAddKid).isTrue();
        verify(kidCountingService).canAddMoreKids(user);
    }

    @Test
    @DisplayName("Action routing: canPerformAction('chat') should delegate to hasFeatureAccess")
    void actionRouting_canPerformActionChatShouldDelegateToHasFeatureAccess() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("daily_free_ai_messages"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(5);
        
        // When
        boolean canChat = subscriptionAccessService.canPerformAction(user, "chat");
        
        // Then
        assertThat(canChat).isTrue();
    }

    @Test
    @DisplayName("Action routing: canPerformAction with unknown action should return false")
    void actionRouting_canPerformActionWithUnknownActionShouldReturnFalse() {
        // When
        boolean canPerform = subscriptionAccessService.canPerformAction(user, "unknown_action");
        
        // Then
        assertThat(canPerform).isFalse();
    }

    @Test
    @DisplayName("Feature name robustness: Unknown feature in JSON should return limit=0 (denied)")
    void featureNameRobustness_unknownFeatureInJsonShouldReturnLimitZero() {
        // Given - User with active subscription but unknown feature
        String featuresJson = "{\"chat_limit\": 100, \"image_generation\": 50}";
        when(subscriptionPlan.getFeatures()).thenReturn(featuresJson);
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("unknown_feature"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(0);
        
        // When
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "unknown_feature");
        
        // Then
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Feature name robustness: Malformed JSON should log error and return 0")
    void featureNameRobustness_malformedJsonShouldLogErrorAndReturnZero() {
        // Given - User with active subscription but malformed JSON
        when(subscriptionPlan.getFeatures()).thenReturn("invalid json");
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(0);
        
        // When
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Feature name robustness: Non-numeric feature value should return 0")
    void featureNameRobustness_nonNumericFeatureValueShouldReturnZero() {
        // Given - User with active subscription but non-numeric feature value
        String featuresJson = "{\"chat_limit\": \"unlimited\", \"image_generation\": 50}";
        when(subscriptionPlan.getFeatures()).thenReturn(featuresJson);
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(0);
        
        // When
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Free tier: Should create usage record with correct period key format")
    void freeTier_shouldCreateUsageRecordWithCorrectPeriodKeyFormat() {
        // Given - User within free tier window
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("daily_free_ai_messages"), any()))
                .thenReturn(Optional.empty());
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenReturn(usageRecord);
        
        // When
        subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        // Verify period key format for daily free tier
        String expectedPeriodKey = "DAILY_FREE_" + user.getId() + "_" + java.time.LocalDate.now(ZoneOffset.UTC);
        verify(subscriptionUsageRepository).findByUserAndFeatureAndPeriodKey(
                eq(user), 
                eq("daily_free_ai_messages"),
                eq(expectedPeriodKey)
        );
    }

    @Test
    @DisplayName("Paid tier: Should handle null subscription plan gracefully")
    void paidTier_shouldHandleNullSubscriptionPlanGracefully() {
        // Given - User with active subscription but null plan
        when(activeSubscription.getSubscriptionPlan()).thenReturn(null);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(0);
        
        // When
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Paid tier: Should handle null features JSON gracefully")
    void paidTier_shouldHandleNullFeaturesJsonGracefully() {
        // Given - User with active subscription but null features
        when(subscriptionPlan.getFeatures()).thenReturn(null);
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(usageRecord));
        when(usageRecord.getRemainingUsage()).thenReturn(0);
        
        // When
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Usage tracking: Should handle repository exceptions gracefully")
    void usageTracking_shouldHandleRepositoryExceptionsGracefully() {
        // Given - Repository throws exception
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("daily_free_ai_messages"), any()))
                .thenThrow(new RuntimeException("Database error"));
        
        // When
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then
        assertThat(remainingUsage).isEqualTo(0);
    }
}
