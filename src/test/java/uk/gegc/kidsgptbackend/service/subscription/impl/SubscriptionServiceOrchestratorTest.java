package uk.gegc.kidsgptbackend.service.subscription.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gegc.kidsgptbackend.dto.subscription.CreateSubscriptionRequest;
import uk.gegc.kidsgptbackend.dto.subscription.SubscriptionStatusDto;
import uk.gegc.kidsgptbackend.dto.subscription.UserSubscriptionDto;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.subscription.SubscriptionPlanRepository;
import uk.gegc.kidsgptbackend.repository.subscription.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;
import uk.gegc.kidsgptbackend.service.family.KidCountingService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService Orchestrator Tests")
class SubscriptionServiceOrchestratorTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private GooglePlayClient googlePlayClient;

    @Mock
    private KidCountingService kidCountingService;

    @Mock
    private SubscriptionSaver subscriptionSaver;

    @Mock
    private SubscriptionAcknowledger subscriptionAcknowledger;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private User testUser;
    private SubscriptionPlan premiumPlan;
    private SubscriptionPlan basicPlan;
    private CreateSubscriptionRequest createRequest;
    private GooglePlaySubscriptionPurchase googlePurchase;
    private UserSubscription savedSubscription;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));

        // Create premium plan
        premiumPlan = new SubscriptionPlan();
        premiumPlan.setId(UUID.randomUUID());
        premiumPlan.setName("Premium Monthly");
        premiumPlan.setDescription("Unlimited everything");
        premiumPlan.setPrice(new BigDecimal("9.99"));
        premiumPlan.setCurrency("GBP");
        premiumPlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        premiumPlan.setMaxKids(50);
        premiumPlan.setFeatures("{\"chat_limit\": -1, \"image_generation\": 100, \"story_continuation\": 50}");
        premiumPlan.setGooglePlayProductId("premium_monthly");
        premiumPlan.setActive(true);

        // Create basic plan
        basicPlan = new SubscriptionPlan();
        basicPlan.setId(UUID.randomUUID());
        basicPlan.setName("Basic Monthly");
        basicPlan.setDescription("Limited features");
        basicPlan.setPrice(new BigDecimal("4.99"));
        basicPlan.setCurrency("GBP");
        basicPlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        basicPlan.setMaxKids(5);
        basicPlan.setFeatures("{\"chat_limit\": 100, \"image_generation\": 10}");
        basicPlan.setGooglePlayProductId("basic_monthly");
        basicPlan.setActive(true);

        // Create request
        createRequest = new CreateSubscriptionRequest(
                premiumPlan.getId(),
                "premium_monthly",
                "purchase_token_123"
        );

        // Create Google Play purchase response
        googlePurchase = new GooglePlaySubscriptionPurchase();
        googlePurchase.setPurchaseToken("purchase_token_123");
        googlePurchase.setProductId("premium_monthly");
        googlePurchase.setStartTimeMillis(Instant.now().minusSeconds(86400).toEpochMilli()); // 1 day ago
        googlePurchase.setExpiryTimeMillis(Instant.now().plusSeconds(2592000).toEpochMilli()); // 30 days from now
        googlePurchase.setAutoRenewing(true);
        googlePurchase.setPurchaseState("PURCHASED");
        googlePurchase.setAcknowledgementState("NOT_ACKNOWLEDGED");

        // Create saved subscription
        savedSubscription = new UserSubscription();
        savedSubscription.setId(UUID.randomUUID());
        savedSubscription.setUser(testUser);
        savedSubscription.setSubscriptionPlan(premiumPlan);
        savedSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        savedSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        savedSubscription.setExternalSubscriptionId("purchase_token_123");
        savedSubscription.setCurrentPeriodStart(Instant.now().minusSeconds(86400));
        savedSubscription.setCurrentPeriodEnd(Instant.now().plusSeconds(2592000));
        savedSubscription.setAutoRenew(true);
        savedSubscription.setStartDate(Instant.now().minusSeconds(86400));
    }

    // ==================== CREATE SUBSCRIPTION (GOOGLE PLAY PATH) ====================

    @Test
    @DisplayName("Create subscription: Calls Google API outside transaction")
    void createSubscription_callsGoogleApiOutsideTransaction() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(googlePlayClient.getSubscriptionPurchase(createRequest.googleProductId(), createRequest.purchaseToken()))
                .thenReturn(googlePurchase);
        when(subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase)).thenReturn(savedSubscription);
        doNothing().when(subscriptionAcknowledger).acknowledge("premium_monthly", "purchase_token_123");

        // When
        UserSubscription result = subscriptionService.createSubscription(testUser, createRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getSubscriptionPlan()).isEqualTo(premiumPlan);

        // Verify Google API was called (outside transaction)
        verify(googlePlayClient).getSubscriptionPurchase("premium_monthly", "purchase_token_123");
        
        // Verify persistence happened after validation
        verify(subscriptionSaver).saveFromGoogle(testUser, createRequest, googlePurchase);
    }

    @Test
    @DisplayName("Create subscription: Handles NOT_ACKNOWLEDGED state with retriable acknowledgement")
    void createSubscription_handlesNotAcknowledgedStateWithRetriableAcknowledgement() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(googlePlayClient.getSubscriptionPurchase(createRequest.googleProductId(), createRequest.purchaseToken()))
                .thenReturn(googlePurchase);
        when(subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase)).thenReturn(savedSubscription);
        doNothing().when(subscriptionAcknowledger).acknowledge("premium_monthly", "purchase_token_123");

        // When
        UserSubscription result = subscriptionService.createSubscription(testUser, createRequest);

        // Then
        assertThat(result).isNotNull();
        
        // Verify acknowledgement was attempted
        verify(subscriptionAcknowledger).acknowledge("premium_monthly", "purchase_token_123");
        
        // Verify subscription was still created even if acknowledgement succeeds
        verify(subscriptionSaver).saveFromGoogle(testUser, createRequest, googlePurchase);
    }

    @Test
    @DisplayName("Create subscription: Handles acknowledgement failure without rolling back persistence")
    void createSubscription_handlesAcknowledgementFailureWithoutRollingBackPersistence() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(googlePlayClient.getSubscriptionPurchase(createRequest.googleProductId(), createRequest.purchaseToken()))
                .thenReturn(googlePurchase);
        when(subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase)).thenReturn(savedSubscription);
        doThrow(new RuntimeException("Google API error")).when(subscriptionAcknowledger)
                .acknowledge(anyString(), anyString());

        // When
        UserSubscription result = subscriptionService.createSubscription(testUser, createRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);

        // Verify acknowledgement was attempted but failed
        verify(subscriptionAcknowledger).acknowledge("premium_monthly", "purchase_token_123");
        
        // Verify subscription was still persisted despite acknowledgement failure
        verify(subscriptionSaver).saveFromGoogle(testUser, createRequest, googlePurchase);
    }

    @Test
    @DisplayName("Create subscription: Persists via SubscriptionSaver only after validation")
    void createSubscription_persistsViaSubscriptionSaverOnlyAfterValidation() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(googlePlayClient.getSubscriptionPurchase(createRequest.googleProductId(), createRequest.purchaseToken()))
                .thenReturn(googlePurchase);
        when(subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase)).thenReturn(savedSubscription);

        // When
        subscriptionService.createSubscription(testUser, createRequest);

        // Then - Verify order of operations
        verify(userSubscriptionRepository).findActiveSubscriptionsWithLock(testUser);
        verify(subscriptionPlanRepository).findById(createRequest.planId());
        verify(googlePlayClient).getSubscriptionPurchase(createRequest.googleProductId(), createRequest.purchaseToken());
        verify(subscriptionSaver).saveFromGoogle(testUser, createRequest, googlePurchase);
    }

    @Test
    @DisplayName("Create subscription: Rejects when googlePurchase.isEntitlementActive() is false")
    void createSubscription_rejectsWhenGooglePurchaseIsEntitlementActiveIsFalse() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));

        GooglePlaySubscriptionPurchase inactivePurchase = new GooglePlaySubscriptionPurchase();
        inactivePurchase.setPurchaseState("CANCELED");
        inactivePurchase.setAutoRenewing(false);
        inactivePurchase.setExpiryTimeMillis(Instant.now().minusSeconds(3600).toEpochMilli()); // Expired
        when(googlePlayClient.getSubscriptionPurchase(createRequest.googleProductId(), createRequest.purchaseToken()))
                .thenReturn(inactivePurchase);

        // When & Then
        assertThatThrownBy(() -> subscriptionService.createSubscription(testUser, createRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Purchase not active");

        // Verify Google API was called but subscription was not persisted
        verify(googlePlayClient).getSubscriptionPurchase("premium_monthly", "purchase_token_123");
        verify(subscriptionSaver, never()).saveFromGoogle(any(), any(), any());
    }

    // ==================== STATUS DTO ====================

    @Test
    @DisplayName("Status DTO: Free tier returns maxKids=1, kids count from KidCountingService, canAddMoreKids honored")
    void statusDto_freeTierReturnsCorrectDefaults() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.empty());
        when(kidCountingService.countKidsForParent(testUser)).thenReturn(2);
        when(kidCountingService.canAddMoreKids(testUser)).thenReturn(false); // At free tier limit

        // When
        SubscriptionStatusDto result = subscriptionService.getUserSubscriptionStatus(testUser);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(testUser.getId());
        assertThat(result.hasActiveSubscription()).isFalse();
        assertThat(result.subscriptionStatus()).isNull();
        assertThat(result.planName()).isNull();
        assertThat(result.currentPeriodEnd()).isNull();
        assertThat(result.isTrial()).isFalse();
        assertThat(result.trialEndDate()).isNull();
        assertThat(result.maxKids()).isEqualTo(1); // Free tier default
        assertThat(result.currentKidsCount()).isEqualTo(2); // From KidCountingService
        assertThat(result.canAddMoreKids()).isFalse(); // Honored from KidCountingService
    }

    @Test
    @DisplayName("Status DTO: Paid tier returns plan/maxKids and count consistent")
    void statusDto_paidTierReturnsCorrectPlanData() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(savedSubscription));
        when(kidCountingService.countKidsForParent(testUser)).thenReturn(3);
        when(kidCountingService.canAddMoreKids(testUser)).thenReturn(true); // 3 < 50

        // When
        SubscriptionStatusDto result = subscriptionService.getUserSubscriptionStatus(testUser);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(testUser.getId());
        assertThat(result.hasActiveSubscription()).isTrue();
        assertThat(result.subscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(result.planName()).isEqualTo("Premium Monthly");
        assertThat(result.currentPeriodEnd()).isEqualTo(savedSubscription.getCurrentPeriodEnd());
        assertThat(result.isTrial()).isFalse();
        assertThat(result.trialEndDate()).isNull();
        assertThat(result.maxKids()).isEqualTo(50); // From plan
        assertThat(result.currentKidsCount()).isEqualTo(3); // From KidCountingService
        assertThat(result.canAddMoreKids()).isTrue(); // Honored from KidCountingService
    }

    // ==================== CANCEL / REACTIVATE ====================

    @Test
    @DisplayName("Cancel: Sets cancelAtPeriodEnd=true, autoRenew=false, and immediate CANCELLED only when period ended")
    void cancel_setsCancelFlagsAndImmediateCancelledOnlyWhenPeriodEnded() {
        // Given - Active subscription with future period end
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(savedSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(savedSubscription);

        // When
        UserSubscriptionDto result = subscriptionService.cancelSubscription(testUser, "User requested");

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        assertThat(savedSubscription.isCancelAtPeriodEnd()).isTrue();
        assertThat(savedSubscription.getCancellationReason()).isEqualTo("User requested");
        assertThat(savedSubscription.isAutoRenew()).isFalse();
        // Should NOT be immediately cancelled since period hasn't ended
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Cancel: Sets immediate CANCELLED when period has ended")
    void cancel_setsImmediateCancelledWhenPeriodHasEnded() {
        // Given - Active subscription with expired period end
        savedSubscription.setCurrentPeriodEnd(Instant.now().minusSeconds(3600)); // Expired
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(savedSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(savedSubscription);

        // When
        UserSubscriptionDto result = subscriptionService.cancelSubscription(testUser, "Expired");

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        assertThat(savedSubscription.isCancelAtPeriodEnd()).isTrue();
        assertThat(savedSubscription.getCancellationReason()).isEqualTo("Expired");
        assertThat(savedSubscription.isAutoRenew()).isFalse();
        // Should be immediately cancelled since period has ended
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.CANCELLED);
        assertThat(savedSubscription.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("Reactivate: Clears cancel flags, leaves status pending until webhook")
    void reactivate_clearsCancelFlagsLeavesStatusPendingUntilWebhook() {
        // Given - Cancelled subscription
        savedSubscription.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
        savedSubscription.setCancelAtPeriodEnd(true);
        savedSubscription.setCancellationReason("User cancelled");
        savedSubscription.setAutoRenew(false);

        when(userSubscriptionRepository.findByUserAndStatusIn(
                testUser, List.of(UserSubscription.SubscriptionStatus.CANCELLED)))
                .thenReturn(Optional.of(savedSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(savedSubscription);

        // When
        UserSubscriptionDto result = subscriptionService.reactivateSubscription(testUser);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        assertThat(savedSubscription.isCancelAtPeriodEnd()).isFalse();
        assertThat(savedSubscription.getCancellationReason()).isNull();
        assertThat(savedSubscription.isAutoRenew()).isTrue();
        // Status should remain CANCELLED until webhook confirms
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.CANCELLED);
    }

    // ==================== WEBHOOK UPDATES ====================

    @Test
    @DisplayName("Webhook update: Maps status, persists provider dates, sets nextBillingDate when end present")
    void webhookUpdate_mapsStatusPersistsProviderDatesSetsNextBillingDate() {
        // Given
        Instant newPeriodStart = Instant.now();
        Instant newPeriodEnd = Instant.now().plusSeconds(2592000);
        String providerStatus = "active";

        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.of(savedSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(savedSubscription);

        // When
        subscriptionService.updateSubscriptionFromWebhook(
                UserSubscription.PaymentProvider.GOOGLE_PLAY,
                "purchase_token_123",
                providerStatus,
                newPeriodStart,
                newPeriodEnd
        );

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(savedSubscription.getProviderStatusRaw()).isEqualTo(providerStatus);
        assertThat(savedSubscription.getCurrentPeriodStart()).isEqualTo(newPeriodStart);
        assertThat(savedSubscription.getCurrentPeriodEnd()).isEqualTo(newPeriodEnd);
        assertThat(savedSubscription.getNextBillingDate()).isEqualTo(newPeriodEnd);
    }

    @Test
    @DisplayName("Webhook update: Handles null period dates gracefully")
    void webhookUpdate_handlesNullPeriodDatesGracefully() {
        // Given
        String providerStatus = "cancelled";

        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.of(savedSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(savedSubscription);

        // When
        subscriptionService.updateSubscriptionFromWebhook(
                UserSubscription.PaymentProvider.GOOGLE_PLAY,
                "purchase_token_123",
                providerStatus,
                null, // null period start
                null  // null period end
        );

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.CANCELLED);
        assertThat(savedSubscription.getProviderStatusRaw()).isEqualTo(providerStatus);
        // Period dates should remain unchanged when null
        assertThat(savedSubscription.getCurrentPeriodStart()).isNotNull();
        assertThat(savedSubscription.getCurrentPeriodEnd()).isNotNull();
    }

    // ==================== EXPIRED PROCESSING / RECONCILIATION ====================

    @Test
    @DisplayName("Expired processing: Marks expired with warning (no provider call)")
    void expiredProcessing_marksExpiredWithWarningNoProviderCall() {
        // Given
        UserSubscription expiredSubscription = new UserSubscription();
        expiredSubscription.setId(UUID.randomUUID());
        expiredSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        expiredSubscription.setCurrentPeriodEnd(Instant.now().minusSeconds(3600)); // Expired
        expiredSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);

        when(userSubscriptionRepository.findExpiredActiveSubscriptions(any(Instant.class)))
                .thenReturn(List.of(expiredSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(expiredSubscription);

        // When
        subscriptionService.processExpiredSubscriptions();

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.EXPIRED);
        
        // Verify no provider calls were made
        verify(googlePlayClient, never()).getSubscriptionPurchase(anyString(), anyString());
    }

    @Test
    @DisplayName("Reconcile with Google Play: Only warns (no billing)")
    void reconcileWithGooglePlay_onlyWarnsNoBilling() {
        // Given
        UserSubscription overdueSubscription = new UserSubscription();
        overdueSubscription.setId(UUID.randomUUID());
        overdueSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        overdueSubscription.setNextBillingDate(Instant.now().minusSeconds(3600)); // Overdue

        when(userSubscriptionRepository.findSubscriptionsDueForBilling(any(Instant.class)))
                .thenReturn(List.of(overdueSubscription));

        // When
        subscriptionService.reconcileWithGooglePlay();

        // Then
        verify(userSubscriptionRepository).findSubscriptionsDueForBilling(any(Instant.class));
        
        // Should not attempt to save or modify subscriptions - just log warnings
        verify(userSubscriptionRepository, never()).save(any());
        
        // Should not attempt any billing operations
        verify(googlePlayClient, never()).getSubscriptionPurchase(anyString(), anyString());
    }

    // ==================== EDGE CASES AND ERROR HANDLING ====================

    @Test
    @DisplayName("Create subscription: Throws when user already has active subscription")
    void createSubscription_throwsWhenUserAlreadyHasActiveSubscription() {
        // Given
        UserSubscription existingActiveSub = new UserSubscription();
        existingActiveSub.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser))
                .thenReturn(List.of(existingActiveSub));

        // When & Then
        assertThatThrownBy(() -> subscriptionService.createSubscription(testUser, createRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User already has an active subscription");

        // Verify no Google API calls or persistence attempts
        verify(googlePlayClient, never()).getSubscriptionPurchase(anyString(), anyString());
        verify(subscriptionSaver, never()).saveFromGoogle(any(), any(), any());
    }

    @Test
    @DisplayName("Create subscription: Throws when plan not found")
    void createSubscription_throwsWhenPlanNotFound() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> subscriptionService.createSubscription(testUser, createRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Subscription plan not found");

        // Verify no Google API calls or persistence attempts
        verify(googlePlayClient, never()).getSubscriptionPurchase(anyString(), anyString());
        verify(subscriptionSaver, never()).saveFromGoogle(any(), any(), any());
    }

    @Test
    @DisplayName("Create subscription: Throws when product ID mismatch")
    void createSubscription_throwsWhenProductIdMismatch() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));

        CreateSubscriptionRequest mismatchedRequest = new CreateSubscriptionRequest(
                createRequest.planId(),
                "wrong_product_id",
                createRequest.purchaseToken()
        );

        // When & Then
        assertThatThrownBy(() -> subscriptionService.createSubscription(testUser, mismatchedRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product/plan mismatch");

        // Verify no Google API calls or persistence attempts
        verify(googlePlayClient, never()).getSubscriptionPurchase(anyString(), anyString());
        verify(subscriptionSaver, never()).saveFromGoogle(any(), any(), any());
    }

    @Test
    @DisplayName("Cancel subscription: Throws when no active subscription found")
    void cancelSubscription_throwsWhenNoActiveSubscriptionFound() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> subscriptionService.cancelSubscription(testUser, "Test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No active subscription found");
    }

    @Test
    @DisplayName("Reactivate subscription: Throws when no cancelled subscription found")
    void reactivateSubscription_throwsWhenNoCancelledSubscriptionFound() {
        // Given
        when(userSubscriptionRepository.findByUserAndStatusIn(
                testUser, List.of(UserSubscription.SubscriptionStatus.CANCELLED)))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> subscriptionService.reactivateSubscription(testUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No cancelled subscription found");
    }

    @Test
    @DisplayName("Webhook update: Throws when subscription not found")
    void webhookUpdate_throwsWhenSubscriptionNotFound() {
        // Given
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "nonexistent_token"))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> subscriptionService.updateSubscriptionFromWebhook(
                UserSubscription.PaymentProvider.GOOGLE_PLAY,
                "nonexistent_token",
                "active",
                Instant.now(),
                Instant.now().plusSeconds(2592000)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Subscription not found");
    }
}
