package uk.gegc.kidsgptbackend.features.subscription.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gegc.kidsgptbackend.features.subscription.application.impl.SubscriptionAccessServiceImpl;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionUsage;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionUsageRepository;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.family.application.KidCountingService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SubscriptionAccessServiceImplTest extends uk.gegc.kidsgptbackend.test.BaseUnitTest {

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

    @Override
    @BeforeEach
    protected void setUp() {
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
        String todayKey = java.time.LocalDate.now(ZoneOffset.UTC).toString();
        existingUsage.setPeriodKey("DAILY_FREE_" + testUser.getId() + "_" + todayKey);
        existingUsage.setUsedCount(1);
        existingUsage.setLimitCount(5);
        existingUsage.setPeriodStart(java.time.LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC));
        existingUsage.setPeriodEnd(java.time.LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC));
    }

    // 4.1 Daily free tier logic tests

    @Test
    @DisplayName("hasFeatureAccess - returns true for chat_limit when daily free remaining exists for free user")
    void hasFeatureAccess_returnsTrueForChatLimitWhenDailyFreeRemaining() {
        SubscriptionUsage dailyUsage = new SubscriptionUsage();
        dailyUsage.setUser(testUser);
        dailyUsage.setFeature("daily_free_ai_messages");
        dailyUsage.setLimitCount(5);
        dailyUsage.setUsedCount(1);
        String periodKey = "DAILY_FREE_" + testUser.getId() + "_" + java.time.LocalDate.now(ZoneOffset.UTC);
        dailyUsage.setPeriodKey(periodKey);

        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                eq(testUser), eq("daily_free_ai_messages"), eq(periodKey)))
                .thenReturn(Optional.of(dailyUsage));

        boolean result = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasFeatureAccess - returns false for chat_limit when daily free is exhausted")
    void hasFeatureAccess_returnsFalseWhenDailyFreeExhausted() {
        SubscriptionUsage dailyUsage = new SubscriptionUsage();
        dailyUsage.setUser(testUser);
        dailyUsage.setFeature("daily_free_ai_messages");
        dailyUsage.setLimitCount(5);
        dailyUsage.setUsedCount(5);
        String periodKey = "DAILY_FREE_" + testUser.getId() + "_" + java.time.LocalDate.now(ZoneOffset.UTC);
        dailyUsage.setPeriodKey(periodKey);

        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                eq(testUser), eq("daily_free_ai_messages"), eq(periodKey)))
                .thenReturn(Optional.of(dailyUsage));

        boolean result = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasFeatureAccess - returns false for non-chat features in free tier")
    void hasFeatureAccess_returnsFalseForNonChatFeaturesInFreeTier() {
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());

        boolean result = subscriptionAccessService.hasFeatureAccess(testUser, "image_generation");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getRemainingUsage - creates daily usage record with 5-message limit for free chat")
    void getRemainingUsage_createsDailyUsageRecordWithFiveLimit() {
        String expectedKey = "DAILY_FREE_" + testUser.getId() + "_" + java.time.LocalDate.now(ZoneOffset.UTC);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                eq(testUser), eq("daily_free_ai_messages"), eq(expectedKey)))
                .thenReturn(Optional.empty());
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenAnswer(invocation -> {
            SubscriptionUsage usage = invocation.getArgument(0);
            usage.setId(UUID.randomUUID());
            return usage;
        });

        int result = subscriptionAccessService.getRemainingUsage(testUser, "chat_limit");

        assertThat(result).isEqualTo(5);

        ArgumentCaptor<SubscriptionUsage> captor = ArgumentCaptor.forClass(SubscriptionUsage.class);
        verify(subscriptionUsageRepository, atLeastOnce()).save(captor.capture());
        SubscriptionUsage savedUsage = captor.getValue();
        assertThat(savedUsage.getUser()).isEqualTo(testUser);
        assertThat(savedUsage.getFeature()).isEqualTo("daily_free_ai_messages");
        assertThat(savedUsage.getPeriodKey()).isEqualTo(expectedKey);
        assertThat(savedUsage.getLimitCount()).isEqualTo(5);
        assertThat(savedUsage.getUsedCount()).isEqualTo(0);
    }

    // 4.2 Free limit behavior tests

    @Test
    @DisplayName("hasReachedUsageLimit - returns true when daily free limit of 5 is reached")
    void hasReachedUsageLimit_returnsTrueWhenDailyFreeLimitReached() {
        existingUsage.setFeature("daily_free_ai_messages");
        existingUsage.setUsedCount(5); // Reached limit
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                eq(testUser), eq("daily_free_ai_messages"), anyString()))
                .thenReturn(Optional.of(existingUsage));

        boolean result = subscriptionAccessService.hasReachedUsageLimit(testUser, "chat_limit");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasFeatureAccess - returns false when daily free limit of 5 is reached")
    void hasFeatureAccess_returnsFalseWhenDailyFreeLimitReached() {
        existingUsage.setFeature("daily_free_ai_messages");
        existingUsage.setUsedCount(5); // Reached limit
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                eq(testUser), eq("daily_free_ai_messages"), anyString()))
                .thenReturn(Optional.of(existingUsage));

        boolean result = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

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
                eq(testUser), eq("daily_free_ai_messages"),
                eq("DAILY_FREE_" + testUser.getId() + "_" + java.time.LocalDate.now(ZoneOffset.UTC)),
                any(Instant.class))).thenReturn(1);

        // When
        subscriptionAccessService.incrementUsage(testUser, "chat_limit");

        // Then
        verify(subscriptionUsageRepository).incrementUsage(
                eq(testUser), eq("daily_free_ai_messages"),
                eq("DAILY_FREE_" + testUser.getId() + "_" + java.time.LocalDate.now(ZoneOffset.UTC)),
                any(Instant.class));
        verify(subscriptionUsageRepository, never()).save(any());
    }

    @Test
    @DisplayName("incrementUsage - returns 0 and creates new row when no row exists")
    void incrementUsage_returnsZeroAndCreatesNewRowWhenNoRowExists() {
        // Given
        String expectedKey = "DAILY_FREE_" + testUser.getId() + "_" + java.time.LocalDate.now(ZoneOffset.UTC);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.incrementUsage(
                eq(testUser), eq("daily_free_ai_messages"),
                eq(expectedKey),
                any(Instant.class))).thenReturn(0);
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenReturn(existingUsage);

        // When
        subscriptionAccessService.incrementUsage(testUser, "chat_limit");

        // Then
        verify(subscriptionUsageRepository).incrementUsage(
                eq(testUser), eq("daily_free_ai_messages"),
                eq(expectedKey),
                any(Instant.class));

        ArgumentCaptor<SubscriptionUsage> captor = ArgumentCaptor.forClass(SubscriptionUsage.class);
        verify(subscriptionUsageRepository, times(2)).save(captor.capture());
        List<SubscriptionUsage> savedUsages = captor.getAllValues();
        SubscriptionUsage finalUsage = savedUsages.get(1); // The second save call
        assertThat(finalUsage.getPeriodKey()).isEqualTo(expectedKey);
        assertThat(finalUsage.getLimitCount()).isEqualTo(5);
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

    // Additional comprehensive tests

    @Test
    @DisplayName("canPerformAction - maps actions to features correctly")
    void canPerformAction_mapsActionsToFeaturesCorrectly() {
        // Given
        existingUsage.setFeature("daily_free_ai_messages");
        existingUsage.setLimitCount(5);
        existingUsage.setUsedCount(1);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                eq(testUser), eq("daily_free_ai_messages"), anyString()))
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
    @DisplayName("getRemainingUsage - defaults image_generation to 2 when feature missing in plan")
    void getRemainingUsage_defaultsImageGenerationWhenMissingInPlan() throws JsonProcessingException {
        // Given
        JsonNode featuresNode = mock(JsonNode.class);
        when(objectMapper.readTree(anyString())).thenReturn(featuresNode);
        when(featuresNode.get("image_generation")).thenReturn(null);

        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.of(activeSubscription));
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), any(), any())).thenReturn(Optional.empty());
        
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenAnswer(invocation -> {
            SubscriptionUsage usage = invocation.getArgument(0);
            usage.setId(UUID.randomUUID());
            return usage;
        });

        // When
        int result = subscriptionAccessService.getRemainingUsage(testUser, "image_generation");

        // Then
        assertThat(result).isEqualTo(2);
    }

    @Test
    @DisplayName("incrementUsage - creates usage record with correct period for free tier")
    void incrementUsage_createsUsageRecordWithCorrectPeriodForFreeTier() {
        // Given
        String expectedKey = "DAILY_FREE_" + testUser.getId() + "_" + java.time.LocalDate.now(ZoneOffset.UTC);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.incrementUsage(
                eq(testUser), eq("daily_free_ai_messages"),
                eq(expectedKey),
                any(Instant.class))).thenReturn(0);
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenReturn(existingUsage);

        // When
        subscriptionAccessService.incrementUsage(testUser, "chat_limit");

        // Then
        ArgumentCaptor<SubscriptionUsage> captor = ArgumentCaptor.forClass(SubscriptionUsage.class);
        verify(subscriptionUsageRepository, times(2)).save(captor.capture());
        SubscriptionUsage finalUsage = captor.getAllValues().get(1);
        assertThat(finalUsage.getPeriodKey()).isEqualTo(expectedKey);
        assertThat(finalUsage.getPeriodStart()).isNotNull();
        assertThat(finalUsage.getPeriodEnd()).isNotNull();
        assertThat(finalUsage.getLimitCount()).isEqualTo(5);
        assertThat(finalUsage.getUsedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getRemainingDailyFreeMessagesForSubject - creates daily usage record with 5-message limit")
    void getRemainingDailyFreeMessagesForSubject_createsDailyUsageRecordWithFiveLimit() {
        UUID kidId = UUID.randomUUID();
        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(
                any(User.class),
                eq("daily_free_ai_messages"),
                anyString())
        ).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class)))
                .thenAnswer(invocation -> {
                    SubscriptionUsage usage = invocation.getArgument(0);
                    usage.setId(UUID.randomUUID());
                    return usage;
                });

        int remaining = subscriptionAccessService.getRemainingDailyFreeMessagesForSubject(testUser, kidId);

        assertThat(remaining).isEqualTo(5);

        ArgumentCaptor<SubscriptionUsage> captor = ArgumentCaptor.forClass(SubscriptionUsage.class);
        verify(subscriptionUsageRepository, atLeastOnce()).save(captor.capture());
        SubscriptionUsage savedUsage = captor.getValue();

        String expectedDate = java.time.LocalDate.now(ZoneOffset.UTC).toString();
        assertThat(savedUsage.getUser()).isEqualTo(testUser);
        assertThat(savedUsage.getFeature()).isEqualTo("daily_free_ai_messages");
        assertThat(savedUsage.getPeriodKey()).isEqualTo("DAILY_FREE_" + kidId + "_" + expectedDate);
        assertThat(savedUsage.getLimitCount()).isEqualTo(5);
        assertThat(savedUsage.getUsedCount()).isEqualTo(0);
        assertThat(savedUsage.getPeriodStart()).isNotNull();
        assertThat(savedUsage.getPeriodEnd()).isNotNull();
    }

    @Test
    @DisplayName("incrementDailyFreeMessagesForSubject - uses atomic increment and does not create new record when row exists")
    void incrementDailyFreeMessagesForSubject_usesAtomicIncrementWhenRowExists() {
        UUID kidId = UUID.randomUUID();
        String expectedDate = java.time.LocalDate.now(ZoneOffset.UTC).toString();
        String expectedPeriodKey = "DAILY_FREE_" + kidId + "_" + expectedDate;

        when(subscriptionUsageRepository.incrementUsage(
                eq(testUser),
                eq("daily_free_ai_messages"),
                eq(expectedPeriodKey),
                any(Instant.class)
        )).thenReturn(1);

        subscriptionAccessService.incrementDailyFreeMessagesForSubject(testUser, kidId);

        verify(subscriptionUsageRepository).incrementUsage(
                eq(testUser),
                eq("daily_free_ai_messages"),
                eq(expectedPeriodKey),
                any(Instant.class)
        );
        verify(subscriptionUsageRepository, never()).save(any(SubscriptionUsage.class));
    }

    @Test
    @DisplayName("addUsageCredits - increases image_generation limit within the current period")
    void addUsageCredits_increasesImageLimit() throws Exception {
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser)).thenReturn(Optional.of(activeSubscription));
        activeSubscription.setCurrentPeriodStart(Instant.now().minus(1, ChronoUnit.DAYS));
        activeSubscription.setCurrentPeriodEnd(Instant.now().plus(29, ChronoUnit.DAYS));

        JsonNode featuresNode = mock(JsonNode.class);
        when(objectMapper.readTree(anyString())).thenReturn(featuresNode);
        when(featuresNode.get("image_generation")).thenReturn(null); // triggers default limit 2

        when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(), any(), any())).thenReturn(Optional.empty());
        when(subscriptionUsageRepository.save(any(SubscriptionUsage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionAccessService.addUsageCredits(testUser, "image_generation", 5);

        ArgumentCaptor<SubscriptionUsage> captor = ArgumentCaptor.forClass(SubscriptionUsage.class);
        verify(subscriptionUsageRepository, atLeastOnce()).save(captor.capture());
        SubscriptionUsage savedUsage = captor.getValue();
        assertThat(savedUsage.getFeature()).isEqualTo("image_generation");
        assertThat(savedUsage.getLimitCount()).isEqualTo(7); // base 2 + 5 credits
    }
}
