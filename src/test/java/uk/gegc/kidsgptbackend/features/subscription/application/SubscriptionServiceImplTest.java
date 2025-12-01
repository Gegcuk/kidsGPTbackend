package uk.gegc.kidsgptbackend.features.subscription.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gegc.kidsgptbackend.features.subscription.api.dto.CreateSubscriptionRequest;
import uk.gegc.kidsgptbackend.features.subscription.api.dto.SubscriptionPlanDto;
import uk.gegc.kidsgptbackend.features.subscription.api.dto.SubscriptionStatusDto;
import uk.gegc.kidsgptbackend.features.subscription.api.dto.UserSubscriptionDto;
import uk.gegc.kidsgptbackend.features.subscription.application.impl.SubscriptionServiceImpl;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionPlanRepository;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;
import uk.gegc.kidsgptbackend.features.family.application.KidCountingService;
import uk.gegc.kidsgptbackend.features.subscription.application.impl.SubscriptionAcknowledger;
import uk.gegc.kidsgptbackend.features.subscription.application.impl.SubscriptionSaver;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionServiceImpl Unit Tests")
class SubscriptionServiceImplTest extends uk.gegc.kidsgptbackend.test.BaseUnitTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private UserRepository userRepository;

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
    private SubscriptionPlan plusMonthlyPlan;
    private CreateSubscriptionRequest createRequest;
    private GooglePlaySubscriptionPurchase googlePurchase;
    private UserSubscription existingSubscription;

    @BeforeEach
    protected void setUp() {
        // Create test user
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Create subscription plan
        plusMonthlyPlan = new SubscriptionPlan();
        plusMonthlyPlan.setId(UUID.randomUUID());
        plusMonthlyPlan.setName("Plus Monthly");
        plusMonthlyPlan.setDescription("Unlimited messaging");
        plusMonthlyPlan.setPrice(new BigDecimal("4.99"));
        plusMonthlyPlan.setCurrency("GBP");
        plusMonthlyPlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        plusMonthlyPlan.setMaxKids(10);
        plusMonthlyPlan.setFeatures("{\"chat_limit\": -1}");
        plusMonthlyPlan.setGooglePlayProductId("plus_monthly");
        plusMonthlyPlan.setActive(true);

        // Create request
        createRequest = new CreateSubscriptionRequest(
                plusMonthlyPlan.getId(),
                "plus_monthly",
                "purchase_token_123"
        );

        // Create Google Play purchase response
        googlePurchase = new GooglePlaySubscriptionPurchase();
        googlePurchase.setPurchaseToken("purchase_token_123");
        googlePurchase.setProductId("plus_monthly");
        googlePurchase.setStartTimeMillis(Instant.now().minusSeconds(86400).toEpochMilli()); // 1 day ago
        googlePurchase.setExpiryTimeMillis(Instant.now().plusSeconds(2592000).toEpochMilli()); // 30 days from now
        googlePurchase.setAutoRenewing(true);
        googlePurchase.setPurchaseState("PURCHASED");
        googlePurchase.setAcknowledgementState("NOT_ACKNOWLEDGED");

        // Create existing subscription for upsert tests
        existingSubscription = new UserSubscription();
        existingSubscription.setId(UUID.randomUUID());
        existingSubscription.setUser(testUser);
        existingSubscription.setSubscriptionPlan(plusMonthlyPlan);
        existingSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        existingSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        existingSubscription.setExternalSubscriptionId("purchase_token_123");
        existingSubscription.setCurrentPeriodStart(Instant.now().minusSeconds(86400));
        existingSubscription.setCurrentPeriodEnd(Instant.now().plusSeconds(2592000));
        existingSubscription.setAutoRenew(true);
        existingSubscription.setStartDate(Instant.now().minusSeconds(86400));
    }

    @Test
    @DisplayName("createSubscription - happy path creates new subscription")
    void createSubscription_happyPath_createsNewSubscription() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(plusMonthlyPlan));
        when(googlePlayClient.getSubscriptionPurchase(createRequest.googleProductId(), createRequest.purchaseToken()))
                .thenReturn(googlePurchase);
        when(subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase)).thenReturn(existingSubscription);
        doNothing().when(subscriptionAcknowledger).acknowledge("plus_monthly", "purchase_token_123");

        // When
        UserSubscription result = subscriptionService.createSubscription(testUser, createRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getSubscriptionPlan()).isEqualTo(plusMonthlyPlan);
        assertThat(result.getPaymentProvider()).isEqualTo(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        assertThat(result.getExternalSubscriptionId()).isEqualTo("purchase_token_123");
        assertThat(result.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(result.isAutoRenew()).isTrue();

        // Verify Google Play client calls
        verify(googlePlayClient).getSubscriptionPurchase("plus_monthly", "purchase_token_123");
        verify(subscriptionAcknowledger).acknowledge("plus_monthly", "purchase_token_123");

        // Verify repository calls
        verify(userSubscriptionRepository).findActiveSubscriptionsWithLock(testUser);
        verify(subscriptionPlanRepository).findById(createRequest.planId());
        verify(subscriptionSaver).saveFromGoogle(testUser, createRequest, googlePurchase);
    }

    @Test
    @DisplayName("createSubscription - rejects when user already has active subscription")
    void createSubscription_rejectsWhenUserAlreadyHasActiveSubscription() {
        // Given
        UserSubscription activeSub = new UserSubscription();
        activeSub.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser))
                .thenReturn(List.of(activeSub));

        // When & Then
        assertThatThrownBy(() -> subscriptionService.createSubscription(testUser, createRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User already has an active subscription");

        verify(userSubscriptionRepository).findActiveSubscriptionsWithLock(testUser);
        verifyNoInteractions(googlePlayClient);
        verifyNoInteractions(subscriptionPlanRepository);
    }

    @Test
    @DisplayName("createSubscription - rejects when plan not found")
    void createSubscription_rejectsWhenPlanNotFound() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> subscriptionService.createSubscription(testUser, createRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Subscription plan not found");

        verify(userSubscriptionRepository).findActiveSubscriptionsWithLock(testUser);
        verify(subscriptionPlanRepository).findById(createRequest.planId());
        verifyNoInteractions(googlePlayClient);
    }

    @Test
    @DisplayName("createSubscription - rejects when product ID mismatch")
    void createSubscription_rejectsWhenProductIdMismatch() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(plusMonthlyPlan));

        CreateSubscriptionRequest mismatchedRequest = new CreateSubscriptionRequest(
                createRequest.planId(),
                "wrong_product_id",
                createRequest.purchaseToken()
        );

        // When & Then
        assertThatThrownBy(() -> subscriptionService.createSubscription(testUser, mismatchedRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product/plan mismatch");

        verify(userSubscriptionRepository).findActiveSubscriptionsWithLock(testUser);
        verify(subscriptionPlanRepository).findById(createRequest.planId());
        verifyNoInteractions(googlePlayClient);
    }

    @Test
    @DisplayName("createSubscription - rejects when Google returns inactive entitlement")
    void createSubscription_rejectsWhenGoogleReturnsInactiveEntitlement() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(plusMonthlyPlan));

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

        verify(googlePlayClient).getSubscriptionPurchase("plus_monthly", "purchase_token_123");
        verifyNoMoreInteractions(googlePlayClient);
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSubscription - continues when acknowledge fails but logs warning")
    void createSubscription_continuesWhenAcknowledgeFailsButLogsWarning() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(plusMonthlyPlan));
        when(googlePlayClient.getSubscriptionPurchase(createRequest.googleProductId(), createRequest.purchaseToken()))
                .thenReturn(googlePurchase);
        doThrow(new RuntimeException("Google API error")).when(subscriptionAcknowledger)
                .acknowledge(anyString(), anyString());
        when(subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase)).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionService.createSubscription(testUser, createRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);

        // Verify acknowledge was attempted
        verify(subscriptionAcknowledger).acknowledge("plus_monthly", "purchase_token_123");
        verify(subscriptionSaver).saveFromGoogle(testUser, createRequest, googlePurchase);
    }

    @Test
    @DisplayName("createSubscription - upserts existing subscription by purchase token")
    void createSubscription_upsertsExistingSubscriptionByPurchaseToken() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(plusMonthlyPlan));
        when(googlePlayClient.getSubscriptionPurchase(createRequest.googleProductId(), createRequest.purchaseToken()))
                .thenReturn(googlePurchase);
        when(subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase)).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionService.createSubscription(testUser, createRequest);

        // Then
        assertThat(result).isEqualTo(existingSubscription);

        // Verify the existing subscription was updated, not a new one created
        verify(subscriptionSaver).saveFromGoogle(testUser, createRequest, googlePurchase);
    }

    @Test
    @DisplayName("mapGooglePlayStatus - maps PURCHASED and not expired to ACTIVE")
    void mapGooglePlayStatus_mapsPurchasedAndNotExpiredToActive() {
        // Given
        GooglePlaySubscriptionPurchase purchase = new GooglePlaySubscriptionPurchase();
        purchase.setPurchaseState("PURCHASED");
        purchase.setExpiryTimeMillis(Instant.now().plusSeconds(3600).toEpochMilli()); // Not expired

        // When
        UserSubscription.SubscriptionStatus result = subscriptionService.mapGooglePlayStatus(purchase);

        // Then
        assertThat(result).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("mapGooglePlayStatus - maps CANCELED to CANCELLED")
    void mapGooglePlayStatus_mapsCanceledToCancelled() {
        // Given
        GooglePlaySubscriptionPurchase purchase = new GooglePlaySubscriptionPurchase();
        purchase.setPurchaseState("CANCELED");

        // When
        UserSubscription.SubscriptionStatus result = subscriptionService.mapGooglePlayStatus(purchase);

        // Then
        assertThat(result).isEqualTo(UserSubscription.SubscriptionStatus.CANCELLED);
    }

    @Test
    @DisplayName("mapGooglePlayStatus - maps expired time to EXPIRED")
    void mapGooglePlayStatus_mapsExpiredTimeToExpired() {
        // Given
        GooglePlaySubscriptionPurchase purchase = new GooglePlaySubscriptionPurchase();
        purchase.setPurchaseState("PURCHASED");
        purchase.setExpiryTimeMillis(Instant.now().minusSeconds(3600).toEpochMilli()); // Expired

        // When
        UserSubscription.SubscriptionStatus result = subscriptionService.mapGooglePlayStatus(purchase);

        // Then
        assertThat(result).isEqualTo(UserSubscription.SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("mapGooglePlayStatus - maps other states to INCOMPLETE")
    void mapGooglePlayStatus_mapsOtherStatesToIncomplete() {
        // Given
        GooglePlaySubscriptionPurchase purchase = new GooglePlaySubscriptionPurchase();
        purchase.setPurchaseState("PENDING");
        purchase.setExpiryTimeMillis(Instant.now().plusSeconds(3600).toEpochMilli()); // Not expired

        // When
        UserSubscription.SubscriptionStatus result = subscriptionService.mapGooglePlayStatus(purchase);

        // Then
        assertThat(result).isEqualTo(UserSubscription.SubscriptionStatus.INCOMPLETE);
    }

    @Test
    @DisplayName("hasActiveSubscription - returns true when user has active subscription")
    void hasActiveSubscription_returnsTrueWhenUserHasActiveSubscription() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(existingSubscription));

        // When
        boolean result = subscriptionService.hasActiveSubscription(testUser);

        // Then
        assertThat(result).isTrue();
        verify(userSubscriptionRepository).findActiveSubscriptionByUser(testUser);
    }

    @Test
    @DisplayName("hasActiveSubscription - returns false when user has no active subscription")
    void hasActiveSubscription_returnsFalseWhenUserHasNoActiveSubscription() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.empty());

        // When
        boolean result = subscriptionService.hasActiveSubscription(testUser);

        // Then
        assertThat(result).isFalse();
        verify(userSubscriptionRepository).findActiveSubscriptionByUser(testUser);
    }

    @Test
    @DisplayName("getUserSubscriptionStatus - returns free defaults when no subscription")
    void getUserSubscriptionStatus_returnsFreeDefaultsWhenNoSubscription() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.empty());
        when(kidCountingService.countKidsForParent(testUser)).thenReturn(0);
        when(kidCountingService.canAddMoreKids(testUser)).thenReturn(true);

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
        assertThat(result.maxKids()).isEqualTo(1); // Free tier
        assertThat(result.currentKidsCount()).isEqualTo(0);
        assertThat(result.canAddMoreKids()).isTrue();
    }

    @Test
    @DisplayName("getUserSubscriptionStatus - returns correct fields when has subscription")
    void getUserSubscriptionStatus_returnsCorrectFieldsWhenHasSubscription() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(existingSubscription));
        when(kidCountingService.countKidsForParent(testUser)).thenReturn(0);
        when(kidCountingService.canAddMoreKids(testUser)).thenReturn(true);

        // When
        SubscriptionStatusDto result = subscriptionService.getUserSubscriptionStatus(testUser);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(testUser.getId());
        assertThat(result.hasActiveSubscription()).isTrue();
        assertThat(result.subscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(result.planName()).isEqualTo("Plus Monthly");
        assertThat(result.currentPeriodEnd()).isEqualTo(existingSubscription.getCurrentPeriodEnd());
        assertThat(result.isTrial()).isFalse();
        assertThat(result.trialEndDate()).isNull();
        assertThat(result.maxKids()).isEqualTo(10);
        assertThat(result.currentKidsCount()).isEqualTo(0); // TODO: Implement kid counting
        assertThat(result.canAddMoreKids()).isTrue(); // 0 < 10
    }

    @Test
    @DisplayName("cancelSubscription - sets cancel flags and saves subscription")
    void cancelSubscription_setsCancelFlagsAndSavesSubscription() {
        // Given
        String reason = "User requested cancellation";
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(existingSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscriptionDto result = subscriptionService.cancelSubscription(testUser, reason);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        assertThat(savedSubscription.isCancelAtPeriodEnd()).isTrue();
        assertThat(savedSubscription.getCancellationReason()).isEqualTo(reason);
        assertThat(savedSubscription.isAutoRenew()).isFalse();
    }

    @Test
    @DisplayName("cancelSubscription - sets status to CANCELLED when no future period end")
    void cancelSubscription_setsStatusToCancelledWhenNoFuturePeriodEnd() {
        // Given
        existingSubscription.setCurrentPeriodEnd(Instant.now().minusSeconds(3600)); // Expired
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(existingSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscriptionDto result = subscriptionService.cancelSubscription(testUser, "Expired");

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.CANCELLED);
        assertThat(savedSubscription.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("cancelSubscription - throws when no active subscription found")
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
    @DisplayName("reactivateSubscription - clears cancel flags for cancelled subscription")
    void reactivateSubscription_clearsCancelFlagsForCancelledSubscription() {
        // Given
        existingSubscription.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
        existingSubscription.setCancelAtPeriodEnd(true);
        existingSubscription.setCancellationReason("User cancelled");
        existingSubscription.setAutoRenew(false);

        when(userSubscriptionRepository.findByUserAndStatusIn(
                testUser, List.of(UserSubscription.SubscriptionStatus.CANCELLED)))
                .thenReturn(Optional.of(existingSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

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

    @Test
    @DisplayName("reactivateSubscription - throws when no cancelled subscription found")
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
    @DisplayName("processExpiredSubscriptions - marks expired subscriptions as EXPIRED")
    void processExpiredSubscriptions_marksExpiredSubscriptionsAsExpired() {
        // Given
        UserSubscription expiredSubscription = new UserSubscription();
        expiredSubscription.setId(UUID.randomUUID());
        expiredSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        expiredSubscription.setCurrentPeriodEnd(Instant.now().minusSeconds(3600)); // Expired

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
    }

    @Test
    @DisplayName("reconcileWithGooglePlay - logs warnings for overdue billing")
    void reconcileWithGooglePlay_logsWarningsForOverdueBilling() {
        // Given
        UserSubscription overdueSubscription = new UserSubscription();
        overdueSubscription.setId(UUID.randomUUID());
        overdueSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);

        when(userSubscriptionRepository.findSubscriptionsDueForBilling(any(Instant.class)))
                .thenReturn(List.of(overdueSubscription));

        // When
        subscriptionService.reconcileWithGooglePlay();

        // Then
        verify(userSubscriptionRepository).findSubscriptionsDueForBilling(any(Instant.class));
        // Should not attempt to save or modify subscriptions - just log warnings
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAvailablePlans - returns plans ordered by price ascending")
    void getAvailablePlans_returnsPlansOrderedByPriceAscending() {
        // Given
        SubscriptionPlan freePlan = new SubscriptionPlan();
        freePlan.setId(UUID.randomUUID());
        freePlan.setName("Free");
        freePlan.setPrice(BigDecimal.ZERO);
        freePlan.setCurrency("GBP");
        freePlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        freePlan.setMaxKids(1);
        freePlan.setActive(true);

        when(subscriptionPlanRepository.findByIsActiveTrueOrderByPriceAsc())
                .thenReturn(List.of(freePlan, plusMonthlyPlan));

        // When
        List<SubscriptionPlanDto> result = subscriptionService.getAvailablePlans();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Free");
        assertThat(result.get(0).price()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.get(1).name()).isEqualTo("Plus Monthly");
        assertThat(result.get(1).price()).isEqualByComparingTo(new BigDecimal("4.99"));
    }

    @Test
    @DisplayName("getPlanById - returns plan when found")
    void getPlanById_returnsPlanWhenFound() {
        // Given
        when(subscriptionPlanRepository.findById(plusMonthlyPlan.getId()))
                .thenReturn(Optional.of(plusMonthlyPlan));

        // When
        SubscriptionPlanDto result = subscriptionService.getPlanById(plusMonthlyPlan.getId());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(plusMonthlyPlan.getId());
        assertThat(result.name()).isEqualTo("Plus Monthly");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("4.99"));
    }

    @Test
    @DisplayName("getPlanById - throws when plan not found")
    void getPlanById_throwsWhenPlanNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(subscriptionPlanRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> subscriptionService.getPlanById(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Subscription plan not found");
    }

    @Test
    @DisplayName("mapPaymentProviderStatus - maps various provider statuses correctly")
    void mapPaymentProviderStatus_mapsVariousProviderStatusesCorrectly() {
        // Test various status mappings
        assertThat(subscriptionService.mapPaymentProviderStatus("active"))
                .isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(subscriptionService.mapPaymentProviderStatus("paid"))
                .isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(subscriptionService.mapPaymentProviderStatus("cancelled"))
                .isEqualTo(UserSubscription.SubscriptionStatus.CANCELLED);
        assertThat(subscriptionService.mapPaymentProviderStatus("canceled"))
                .isEqualTo(UserSubscription.SubscriptionStatus.CANCELLED);
        assertThat(subscriptionService.mapPaymentProviderStatus("past_due"))
                .isEqualTo(UserSubscription.SubscriptionStatus.PAST_DUE);
        assertThat(subscriptionService.mapPaymentProviderStatus("pastdue"))
                .isEqualTo(UserSubscription.SubscriptionStatus.PAST_DUE);
        assertThat(subscriptionService.mapPaymentProviderStatus("unpaid"))
                .isEqualTo(UserSubscription.SubscriptionStatus.UNPAID);
        assertThat(subscriptionService.mapPaymentProviderStatus("incomplete"))
                .isEqualTo(UserSubscription.SubscriptionStatus.INCOMPLETE);
        assertThat(subscriptionService.mapPaymentProviderStatus("incomplete_expired"))
                .isEqualTo(UserSubscription.SubscriptionStatus.INCOMPLETE_EXPIRED);
        assertThat(subscriptionService.mapPaymentProviderStatus("unknown_status"))
                .isEqualTo(UserSubscription.SubscriptionStatus.INCOMPLETE); // Default
    }

    @Test
    @DisplayName("updateSubscriptionFromWebhook - updates subscription with provider data")
    void updateSubscriptionFromWebhook_updatesSubscriptionWithProviderData() {
        // Given
        Instant newPeriodStart = Instant.now();
        Instant newPeriodEnd = Instant.now().plusSeconds(2592000);
        String providerStatus = "active";

        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.of(existingSubscription));
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

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
    @DisplayName("updateSubscriptionFromWebhook - throws when subscription not found")
    void updateSubscriptionFromWebhook_throwsWhenSubscriptionNotFound() {
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

    @Test
    @DisplayName("getMaxKidsForUser - returns 1 for free tier when no active subscription")
    void getMaxKidsForUser_returns1ForFreeTierWhenNoActiveSubscription() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.empty());

        // When
        Integer result = subscriptionService.getMaxKidsForUser(testUser);

        // Then
        assertThat(result).isEqualTo(1);
        verify(userSubscriptionRepository).findActiveSubscriptionByUser(testUser);
    }

    @Test
    @DisplayName("getMaxKidsForUser - returns plan maxKids when active subscription exists")
    void getMaxKidsForUser_returnsPlanMaxKidsWhenActiveSubscriptionExists() {
        // Given
        UserSubscription activeSubscription = new UserSubscription();
        activeSubscription.setUser(testUser);
        activeSubscription.setSubscriptionPlan(plusMonthlyPlan);
        activeSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(activeSubscription));

        // When
        Integer result = subscriptionService.getMaxKidsForUser(testUser);

        // Then
        assertThat(result).isEqualTo(10); // plusMonthlyPlan has maxKids = 10
        verify(userSubscriptionRepository).findActiveSubscriptionByUser(testUser);
    }

    @Test
    @DisplayName("getMaxKidsForUser - returns -1 for unlimited plan")
    void getMaxKidsForUser_returnsNegativeOneForUnlimitedPlan() {
        // Given
        SubscriptionPlan unlimitedPlan = new SubscriptionPlan();
        unlimitedPlan.setId(UUID.randomUUID());
        unlimitedPlan.setMaxKids(-1); // Unlimited
        
        UserSubscription activeSubscription = new UserSubscription();
        activeSubscription.setUser(testUser);
        activeSubscription.setSubscriptionPlan(unlimitedPlan);
        activeSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(activeSubscription));

        // When
        Integer result = subscriptionService.getMaxKidsForUser(testUser);

        // Then
        assertThat(result).isEqualTo(-1);
        verify(userSubscriptionRepository).findActiveSubscriptionByUser(testUser);
    }

    @Test
    @DisplayName("getUserSubscriptionHistory - returns empty list when no subscriptions")
    void getUserSubscriptionHistory_returnsEmptyListWhenNoSubscriptions() {
        // Given
        when(userSubscriptionRepository.findByUserAndStatusInOrderByCreatedAtDesc(
                eq(testUser), any()))
                .thenReturn(List.of());

        // When
        List<UserSubscriptionDto> result = subscriptionService.getUserSubscriptionHistory(testUser);

        // Then
        assertThat(result).isEmpty();
        verify(userSubscriptionRepository).findByUserAndStatusInOrderByCreatedAtDesc(
                eq(testUser), any());
    }

    @Test
    @DisplayName("getUserSubscriptionHistory - returns all subscriptions ordered by createdAt desc")
    void getUserSubscriptionHistory_returnsAllSubscriptionsOrderedByCreatedAtDesc() {
        // Given
        UserSubscription subscription1 = new UserSubscription();
        subscription1.setId(UUID.randomUUID());
        subscription1.setUser(testUser);
        subscription1.setSubscriptionPlan(plusMonthlyPlan);
        subscription1.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        subscription1.setStartDate(Instant.now().minusSeconds(86400));
        subscription1.setCreatedAt(Instant.now().minusSeconds(86400));
        
        UserSubscription subscription2 = new UserSubscription();
        subscription2.setId(UUID.randomUUID());
        subscription2.setUser(testUser);
        subscription2.setSubscriptionPlan(plusMonthlyPlan);
        subscription2.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
        subscription2.setStartDate(Instant.now().minusSeconds(172800));
        subscription2.setCreatedAt(Instant.now().minusSeconds(172800));
        
        when(userSubscriptionRepository.findByUserAndStatusInOrderByCreatedAtDesc(
                eq(testUser), any()))
                .thenReturn(List.of(subscription1, subscription2));

        // When
        List<UserSubscriptionDto> result = subscriptionService.getUserSubscriptionHistory(testUser);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(subscription1.getId());
        assertThat(result.get(0).status()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(result.get(1).id()).isEqualTo(subscription2.getId());
        assertThat(result.get(1).status()).isEqualTo(UserSubscription.SubscriptionStatus.CANCELLED);
        verify(userSubscriptionRepository).findByUserAndStatusInOrderByCreatedAtDesc(
                eq(testUser), any());
    }

    @Test
    @DisplayName("canAddMoreKids - delegates to KidCountingService and returns true")
    void canAddMoreKids_delegatesToKidCountingServiceAndReturnsTrue() {
        // Given
        when(kidCountingService.canAddMoreKids(testUser)).thenReturn(true);

        // When
        boolean result = subscriptionService.canAddMoreKids(testUser);

        // Then
        assertThat(result).isTrue();
        verify(kidCountingService).canAddMoreKids(testUser);
        verifyNoInteractions(userSubscriptionRepository);
    }

    @Test
    @DisplayName("canAddMoreKids - delegates to KidCountingService and returns false")
    void canAddMoreKids_delegatesToKidCountingServiceAndReturnsFalse() {
        // Given
        when(kidCountingService.canAddMoreKids(testUser)).thenReturn(false);

        // When
        boolean result = subscriptionService.canAddMoreKids(testUser);

        // Then
        assertThat(result).isFalse();
        verify(kidCountingService).canAddMoreKids(testUser);
        verifyNoInteractions(userSubscriptionRepository);
    }
}
