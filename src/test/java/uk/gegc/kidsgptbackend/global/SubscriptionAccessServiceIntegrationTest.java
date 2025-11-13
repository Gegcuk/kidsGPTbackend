package uk.gegc.kidsgptbackend.global;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionUsage;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.subscription.SubscriptionUsageRepository;
import uk.gegc.kidsgptbackend.repository.subscription.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.service.family.KidCountingService;
import uk.gegc.kidsgptbackend.service.subscription.impl.SubscriptionAccessServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

/**
 * Integration tests for SubscriptionAccessServiceImpl with realistic scenarios:
 * - Complex subscription states and transitions
 * - Multiple feature usage patterns
 * - Edge cases and boundary conditions
 * - Real-world usage patterns
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionAccessService Integration Tests")
class SubscriptionAccessServiceIntegrationTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    
    @Mock
    private SubscriptionUsageRepository subscriptionUsageRepository;
    
    @Mock
    private KidCountingService kidCountingService;
    
    @Mock
    private User user;
    
    @Mock
    private UserSubscription premiumSubscription;
    
    @Mock
    private UserSubscription basicSubscription;
    
    @Mock
    private UserSubscription expiredSubscription;
    
    @Mock
    private SubscriptionPlan premiumPlan;
    
    @Mock
    private SubscriptionPlan basicPlan;
    
    @Mock
    private SubscriptionUsage chatUsage;
    
    @Mock
    private SubscriptionUsage imageUsage;

    private SubscriptionAccessServiceImpl subscriptionAccessService;
    private Clock fixedClock;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);
        objectMapper = new ObjectMapper();
        subscriptionAccessService = new SubscriptionAccessServiceImpl(
                userSubscriptionRepository, 
                subscriptionUsageRepository, 
                objectMapper,
                kidCountingService
        );
        
        // Set up common mocks
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("testuser");
        when(user.getCreatedAt()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));
        
        when(premiumSubscription.getId()).thenReturn(UUID.randomUUID());
        when(premiumSubscription.getUser()).thenReturn(user);
        when(premiumSubscription.getSubscriptionPlan()).thenReturn(premiumPlan);
        when(premiumSubscription.getCurrentPeriodStart()).thenReturn(Instant.now(fixedClock).minus(1, ChronoUnit.DAYS));
        when(premiumSubscription.getCurrentPeriodEnd()).thenReturn(Instant.now(fixedClock).plus(30, ChronoUnit.DAYS));
        when(premiumSubscription.getPaymentProvider()).thenReturn(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        when(premiumSubscription.getExternalSubscriptionId()).thenReturn("premium_sub_123");
        
        when(basicSubscription.getId()).thenReturn(UUID.randomUUID());
        when(basicSubscription.getUser()).thenReturn(user);
        when(basicSubscription.getSubscriptionPlan()).thenReturn(basicPlan);
        when(basicSubscription.getCurrentPeriodStart()).thenReturn(Instant.now(fixedClock).minus(1, ChronoUnit.DAYS));
        when(basicSubscription.getCurrentPeriodEnd()).thenReturn(Instant.now(fixedClock).plus(30, ChronoUnit.DAYS));
        when(basicSubscription.getPaymentProvider()).thenReturn(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        when(basicSubscription.getExternalSubscriptionId()).thenReturn("basic_sub_456");
        
        when(expiredSubscription.getId()).thenReturn(UUID.randomUUID());
        when(expiredSubscription.getUser()).thenReturn(user);
        when(expiredSubscription.getSubscriptionPlan()).thenReturn(basicPlan);
        when(expiredSubscription.getCurrentPeriodStart()).thenReturn(Instant.now(fixedClock).minus(31, ChronoUnit.DAYS));
        when(expiredSubscription.getCurrentPeriodEnd()).thenReturn(Instant.now(fixedClock).minus(1, ChronoUnit.DAYS));
        when(expiredSubscription.getPaymentProvider()).thenReturn(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        when(expiredSubscription.getExternalSubscriptionId()).thenReturn("expired_sub_789");
        
        when(premiumPlan.getFeatures()).thenReturn("{\"chat_limit\": -1, \"image_generation\": 100, \"story_continuation\": 50}");
        when(basicPlan.getFeatures()).thenReturn("{\"chat_limit\": 50, \"image_generation\": 10, \"story_continuation\": 5}");
        
        when(chatUsage.getUser()).thenReturn(user);
        when(chatUsage.getFeature()).thenReturn("chat_limit");
        when(chatUsage.getUsedCount()).thenReturn(0);
        when(chatUsage.getLimitCount()).thenReturn(50);
        when(chatUsage.getRemainingUsage()).thenReturn(50);
        
        when(imageUsage.getUser()).thenReturn(user);
        when(imageUsage.getFeature()).thenReturn("image_generation");
        when(imageUsage.getUsedCount()).thenReturn(0);
        when(imageUsage.getLimitCount()).thenReturn(10);
        when(imageUsage.getRemainingUsage()).thenReturn(10);
    }

    @Test
    @DisplayName("Integration: User transitions from free tier to premium subscription")
    void integration_userTransitionsFromFreeTierToPremiumSubscription() {
        // Given - User within free tier window (ensure user is within 3-day window)
        when(user.getCreatedAt()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(chatUsage));
        when(chatUsage.getLimitCount()).thenReturn(15);
        when(chatUsage.getRemainingUsage()).thenReturn(15);
        
        // When - Check free tier access
        boolean freeTierAccess = subscriptionAccessService.hasFeatureAccess(user, "chat_limit");
        int freeTierRemaining = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Free tier should work
        assertThat(freeTierAccess).isTrue();
        assertThat(freeTierRemaining).isEqualTo(15);
        
        // Given - User gets premium subscription
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(chatUsage));
        when(chatUsage.getLimitCount()).thenReturn(-1);
        when(chatUsage.getRemainingUsage()).thenReturn(Integer.MAX_VALUE);
        
        // When - Check premium access
        boolean premiumAccess = subscriptionAccessService.hasFeatureAccess(user, "chat_limit");
        int premiumRemaining = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Premium should work with unlimited
        assertThat(premiumAccess).isTrue();
        assertThat(premiumRemaining).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Integration: User with premium subscription uses multiple features")
    void integration_userWithPremiumSubscriptionUsesMultipleFeatures() {
        // Given - User with premium subscription
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(chatUsage));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("image_generation"), any()))
                .thenReturn(Optional.of(imageUsage));
        when(chatUsage.getLimitCount()).thenReturn(-1);
        when(chatUsage.getRemainingUsage()).thenReturn(Integer.MAX_VALUE);
        when(imageUsage.getLimitCount()).thenReturn(100);
        when(imageUsage.getRemainingUsage()).thenReturn(100);
        
        // When - Check multiple features
        boolean chatAccess = subscriptionAccessService.hasFeatureAccess(user, "chat_limit");
        boolean imageAccess = subscriptionAccessService.hasFeatureAccess(user, "image_generation");
        int chatRemaining = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        int imageRemaining = subscriptionAccessService.getRemainingUsage(user, "image_generation");
        
        // Then - Both features should work
        assertThat(chatAccess).isTrue();
        assertThat(imageAccess).isTrue();
        assertThat(chatRemaining).isEqualTo(Integer.MAX_VALUE);
        assertThat(imageRemaining).isEqualTo(100);
    }

    @Test
    @DisplayName("Integration: User with basic subscription reaches limits")
    void integration_userWithBasicSubscriptionReachesLimits() {
        // Given - User with basic subscription at limit
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(basicSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("image_generation"), any()))
                .thenReturn(Optional.of(imageUsage));
        when(imageUsage.getLimitCount()).thenReturn(10);
        when(imageUsage.getUsedCount()).thenReturn(10);
        when(imageUsage.getRemainingUsage()).thenReturn(0);
        
        // When - Check access at limit
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(user, "image_generation");
        boolean hasReachedLimit = subscriptionAccessService.hasReachedUsageLimit(user, "image_generation");
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "image_generation");
        
        // Then - Should be at limit
        assertThat(hasAccess).isFalse();
        assertThat(hasReachedLimit).isTrue();
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with expired subscription falls back to free tier")
    void integration_userWithExpiredSubscriptionFallsBackToFreeTier() {
        // Given - User with expired subscription but within free tier window
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(expiredSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(chatUsage));
        when(chatUsage.getLimitCount()).thenReturn(15);
        when(chatUsage.getRemainingUsage()).thenReturn(15);
        
        // When - Check access with expired subscription
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(user, "chat_limit");
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should fall back to free tier
        assertThat(hasAccess).isTrue();
        assertThat(remainingUsage).isEqualTo(15);
    }

    @Test
    @DisplayName("Integration: User outside free tier window with no subscription")
    void integration_userOutsideFreeTierWindowWithNoSubscription() {
        // Given - User created 4 days ago (outside free tier window)
        when(user.getCreatedAt()).thenReturn(Instant.now(fixedClock).minus(4, ChronoUnit.DAYS));
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.empty());
        
        // When - Check access
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(user, "chat_limit");
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should be denied
        assertThat(hasAccess).isFalse();
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User performs multiple actions with different access levels")
    void integration_userPerformsMultipleActionsWithDifferentAccessLevels() {
        // Given - User with basic subscription
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(basicSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(chatUsage));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("image_generation"), any()))
                .thenReturn(Optional.of(imageUsage));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("story_continuation"), any()))
                .thenReturn(Optional.of(chatUsage)); // Reuse chatUsage mock
        when(chatUsage.getRemainingUsage()).thenReturn(50);
        when(imageUsage.getRemainingUsage()).thenReturn(10);
        when(kidCountingService.canAddMoreKids(user)).thenReturn(true);
        
        // When - Check multiple actions
        boolean canChat = subscriptionAccessService.canPerformAction(user, "chat");
        boolean canGenerateImage = subscriptionAccessService.canPerformAction(user, "image_generation");
        boolean canAddKid = subscriptionAccessService.canPerformAction(user, "add_kid");
        boolean canContinueStory = subscriptionAccessService.canPerformAction(user, "story_continuation");
        
        // Then - Actions should work based on subscription
        assertThat(canChat).isTrue();
        assertThat(canGenerateImage).isTrue();
        assertThat(canAddKid).isTrue();
        assertThat(canContinueStory).isTrue();
    }

    @Test
    @DisplayName("Integration: User with subscription but no provider period data")
    void integration_userWithSubscriptionButNoProviderPeriodData() {
        // Given - User with subscription but null provider period
        when(premiumSubscription.getCurrentPeriodStart()).thenReturn(null);
        when(premiumSubscription.getCurrentPeriodEnd()).thenReturn(null);
        when(premiumSubscription.getSubscriptionPlan()).thenReturn(basicPlan); // Use basic plan with limited features
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), eq("chat_limit"), any()))
                .thenReturn(Optional.of(chatUsage));
        
        // When - Check access
        subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should use monthly fallback (YYYY-MM based on current system time)
        String expectedMonthKey = YearMonth.now().toString(); // e.g., "2024-11"
        verify(subscriptionUsageRepository).findByUserAndFeatureAndPeriodKey(
                eq(user), 
                eq("chat_limit"), 
                eq(expectedMonthKey)
        );
    }

    @Test
    @DisplayName("Integration: User with subscription but malformed features JSON")
    void integration_userWithSubscriptionButMalformedFeaturesJson() {
        // Given - User with subscription but malformed JSON
        when(premiumPlan.getFeatures()).thenReturn("invalid json");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for malformed JSON
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but unknown feature")
    void integration_userWithSubscriptionButUnknownFeature() {
        // Given - User with subscription but unknown feature
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check unknown feature
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "unknown_feature");
        
        // Then - Should return 0 for unknown feature
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but non-numeric feature value")
    void integration_userWithSubscriptionButNonNumericFeatureValue() {
        // Given - User with subscription but non-numeric feature value
        when(premiumPlan.getFeatures()).thenReturn("{\"chat_limit\": \"unlimited\", \"image_generation\": 100}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check non-numeric feature
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for non-numeric value
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but null features")
    void integration_userWithSubscriptionButNullFeatures() {
        // Given - User with subscription but null features
        when(premiumPlan.getFeatures()).thenReturn(null);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for null features
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but null plan")
    void integration_userWithSubscriptionButNullPlan() {
        // Given - User with subscription but null plan
        when(premiumSubscription.getSubscriptionPlan()).thenReturn(null);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for null plan
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but null plan features")
    void integration_userWithSubscriptionButNullPlanFeatures() {
        // Given - User with subscription but null plan features
        when(premiumPlan.getFeatures()).thenReturn(null);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for null features
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but empty features JSON")
    void integration_userWithSubscriptionButEmptyFeaturesJson() {
        // Given - User with subscription but empty features JSON
        when(premiumPlan.getFeatures()).thenReturn("{}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for empty features
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but whitespace-only features JSON")
    void integration_userWithSubscriptionButWhitespaceOnlyFeaturesJson() {
        // Given - User with subscription but whitespace-only features JSON
        when(premiumPlan.getFeatures()).thenReturn("   ");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for whitespace-only features
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but features JSON with null values")
    void integration_userWithSubscriptionButFeaturesJsonWithNullValues() {
        // Given - User with subscription but features JSON with null values
        when(premiumPlan.getFeatures()).thenReturn("{\"chat_limit\": null, \"image_generation\": 100}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for null feature value
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but features JSON with boolean values")
    void integration_userWithSubscriptionButFeaturesJsonWithBooleanValues() {
        // Given - User with subscription but features JSON with boolean values
        when(premiumPlan.getFeatures()).thenReturn("{\"chat_limit\": true, \"image_generation\": 100}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for boolean feature value
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but features JSON with string values")
    void integration_userWithSubscriptionButFeaturesJsonWithStringValues() {
        // Given - User with subscription but features JSON with string values
        when(premiumPlan.getFeatures()).thenReturn("{\"chat_limit\": \"50\", \"image_generation\": 100}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for string feature value
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but features JSON with array values")
    void integration_userWithSubscriptionButFeaturesJsonWithArrayValues() {
        // Given - User with subscription but features JSON with array values
        when(premiumPlan.getFeatures()).thenReturn("{\"chat_limit\": [50], \"image_generation\": 100}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for array feature value
        assertThat(remainingUsage).isEqualTo(0);
    }

    @Test
    @DisplayName("Integration: User with subscription but features JSON with object values")
    void integration_userWithSubscriptionButFeaturesJsonWithObjectValues() {
        // Given - User with subscription but features JSON with object values
        when(premiumPlan.getFeatures()).thenReturn("{\"chat_limit\": {\"limit\": 50}, \"image_generation\": 100}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(user)).thenReturn(Optional.of(premiumSubscription));
        
        // When - Check access
        int remainingUsage = subscriptionAccessService.getRemainingUsage(user, "chat_limit");
        
        // Then - Should return 0 for object feature value
        assertThat(remainingUsage).isEqualTo(0);
    }
}
