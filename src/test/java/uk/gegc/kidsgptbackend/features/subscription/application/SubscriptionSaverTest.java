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
import uk.gegc.kidsgptbackend.features.subscription.application.impl.SubscriptionSaver;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionPlanRepository;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.subscription.infra.googleplay.GooglePlaySubscriptionPurchase;

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
@DisplayName("SubscriptionSaver Tests")
class SubscriptionSaverTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @InjectMocks
    private SubscriptionSaver subscriptionSaver;

    private User testUser;
    private SubscriptionPlan premiumPlan;
    private CreateSubscriptionRequest createRequest;
    private GooglePlaySubscriptionPurchase googlePurchase;
    private UserSubscription existingSubscription;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Create premium plan
        premiumPlan = new SubscriptionPlan();
        premiumPlan.setId(UUID.randomUUID());
        premiumPlan.setName("Premium Monthly");
        premiumPlan.setDescription("Unlimited everything");
        premiumPlan.setPrice(new BigDecimal("9.99"));
        premiumPlan.setCurrency("GBP");
        premiumPlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        premiumPlan.setMaxKids(50);
        premiumPlan.setFeatures("{\"chat_limit\": -1, \"image_generation\": 100}");
        premiumPlan.setGooglePlayProductId("premium_monthly");
        premiumPlan.setActive(true);

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
        googlePurchase.setAcknowledgementState("ACKNOWLEDGED");

        // Create existing subscription for idempotency tests
        existingSubscription = new UserSubscription();
        existingSubscription.setId(UUID.randomUUID());
        existingSubscription.setUser(testUser);
        existingSubscription.setSubscriptionPlan(premiumPlan);
        existingSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        existingSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        existingSubscription.setExternalSubscriptionId("purchase_token_123");
        existingSubscription.setCurrentPeriodStart(Instant.now().minusSeconds(86400));
        existingSubscription.setCurrentPeriodEnd(Instant.now().plusSeconds(2592000));
        existingSubscription.setAutoRenew(true);
        existingSubscription.setStartDate(Instant.now().minusSeconds(86400));
    }

    // ==================== PLAN VALIDATION ====================

    @Test
    @DisplayName("Plan validation: Throws on missing plan")
    void planValidation_throwsOnMissingPlan() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Subscription plan not found");

        // Verify no subscription was created
        verify(userSubscriptionRepository, never()).save(any(UserSubscription.class));
    }

    @Test
    @DisplayName("Plan validation: Throws on product/plan mismatch")
    void planValidation_throwsOnProductPlanMismatch() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));

        CreateSubscriptionRequest mismatchedRequest = new CreateSubscriptionRequest(
                createRequest.planId(),
                "wrong_product_id", // Mismatch
                createRequest.purchaseToken()
        );

        // When & Then
        assertThatThrownBy(() -> subscriptionSaver.saveFromGoogle(testUser, mismatchedRequest, googlePurchase))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product/plan mismatch");

        // Verify no subscription was created
        verify(userSubscriptionRepository, never()).save(any(UserSubscription.class));
    }

    @Test
    @DisplayName("Plan validation: Succeeds when plan exists and product matches")
    void planValidation_succeedsWhenPlanExistsAndProductMatches() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSubscriptionPlan()).isEqualTo(premiumPlan);
        verify(subscriptionPlanRepository).findById(createRequest.planId());
    }

    // ==================== ACTIVE SUBSCRIPTION RACE ====================

    @Test
    @DisplayName("Active sub race: Honors findActiveSubscriptionsWithLock - throws when user has active subscription")
    void activeSubRace_honorsFindActiveSubscriptionsWithLockThrowsWhenUserHasActiveSubscription() {
        // Given
        UserSubscription activeSub = new UserSubscription();
        activeSub.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser))
                .thenReturn(List.of(activeSub));

        // When & Then
        assertThatThrownBy(() -> subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User already has an active subscription");

        // Verify the lock was acquired
        verify(userSubscriptionRepository).findActiveSubscriptionsWithLock(testUser);
        
        // Verify no subscription was created
        verify(userSubscriptionRepository, never()).save(any(UserSubscription.class));
    }

    @Test
    @DisplayName("Active sub race: Allows creation when no active subscriptions found")
    void activeSubRace_allowsCreationWhenNoActiveSubscriptionsFound() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        assertThat(result).isNotNull();
        verify(userSubscriptionRepository).findActiveSubscriptionsWithLock(testUser);
        verify(userSubscriptionRepository).save(any(UserSubscription.class));
    }

    @Test
    @DisplayName("Active sub race: Concurrent calls don't create duplicates due to lock")
    void activeSubRace_concurrentCallsDontCreateDuplicatesDueToLock() {
        // Given - First call finds no active subscriptions, second call finds the one created by first
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser))
                .thenReturn(List.of()) // First call
                .thenReturn(List.of(existingSubscription)); // Second call would find the created subscription
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When - First call succeeds
        UserSubscription result1 = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);
        assertThat(result1).isNotNull();

        // When - Second call should fail due to lock
        assertThatThrownBy(() -> subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User already has an active subscription");

        // Verify lock was called twice
        verify(userSubscriptionRepository, times(2)).findActiveSubscriptionsWithLock(testUser);
    }

    // ==================== MAPPING ====================

    @Test
    @DisplayName("Mapping: Maps provider fields to entity and tolerates null start/end")
    void mapping_mapsProviderFieldsToEntityAndToleratesNullStartEnd() {
        // Given - Google purchase with null timestamps
        GooglePlaySubscriptionPurchase purchaseWithNulls = new GooglePlaySubscriptionPurchase();
        purchaseWithNulls.setPurchaseToken("purchase_token_123");
        purchaseWithNulls.setProductId("premium_monthly");
        // Note: We can't set null for long fields directly, so we'll test with 0 values
        purchaseWithNulls.setStartTimeMillis(0L); // Simulate null start
        purchaseWithNulls.setExpiryTimeMillis(0L); // Simulate null end
        purchaseWithNulls.setAutoRenewing(false);
        purchaseWithNulls.setPurchaseState("PURCHASED");

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, purchaseWithNulls);

        // Then
        assertThat(result).isNotNull();
        
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        // Verify basic mapping
        assertThat(savedSubscription.getUser()).isEqualTo(testUser);
        assertThat(savedSubscription.getSubscriptionPlan()).isEqualTo(premiumPlan);
        assertThat(savedSubscription.getPaymentProvider()).isEqualTo(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        assertThat(savedSubscription.getExternalSubscriptionId()).isEqualTo("purchase_token_123");
        assertThat(savedSubscription.isAutoRenew()).isFalse();
        assertThat(savedSubscription.getProviderStatusRaw()).isEqualTo("PURCHASED");
        assertThat(savedSubscription.getStartDate()).isNotNull();
        
        // Verify 0 timestamps are handled gracefully (converted to epoch start)
        assertThat(savedSubscription.getCurrentPeriodStart()).isEqualTo(Instant.ofEpochMilli(0L));
        assertThat(savedSubscription.getCurrentPeriodEnd()).isEqualTo(Instant.ofEpochMilli(0L));
        assertThat(savedSubscription.getNextBillingDate()).isEqualTo(Instant.ofEpochMilli(0L));
    }

    @Test
    @DisplayName("Mapping: Sets currentPeriodEnd and nextBillingDate consistently when end present")
    void mapping_setsCurrentPeriodEndAndNextBillingDateConsistentlyWhenEndPresent() {
        // Given
        Instant startTime = Instant.now().minusSeconds(86400);
        Instant endTime = Instant.now().plusSeconds(2592000);
        
        googlePurchase.setStartTimeMillis(startTime.toEpochMilli());
        googlePurchase.setExpiryTimeMillis(endTime.toEpochMilli());

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        // Verify period dates are set correctly (with millisecond precision)
        assertThat(savedSubscription.getCurrentPeriodStart().toEpochMilli()).isEqualTo(startTime.toEpochMilli());
        assertThat(savedSubscription.getCurrentPeriodEnd().toEpochMilli()).isEqualTo(endTime.toEpochMilli());
        assertThat(savedSubscription.getNextBillingDate().toEpochMilli()).isEqualTo(endTime.toEpochMilli()); // Should be same as end
    }

    @Test
    @DisplayName("Mapping: Handles partial null timestamps gracefully")
    void mapping_handlesPartialNullTimestampsGracefully() {
        // Given - Only start time is 0 (simulating null)
        Instant endTime = Instant.now().plusSeconds(2592000);
        googlePurchase.setStartTimeMillis(0L); // Simulate null start
        googlePurchase.setExpiryTimeMillis(endTime.toEpochMilli()); // Valid end

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        // Verify partial null handling (0 timestamp converted to epoch start)
        assertThat(savedSubscription.getCurrentPeriodStart().toEpochMilli()).isEqualTo(0L);
        assertThat(savedSubscription.getCurrentPeriodEnd().toEpochMilli()).isEqualTo(endTime.toEpochMilli());
        assertThat(savedSubscription.getNextBillingDate().toEpochMilli()).isEqualTo(endTime.toEpochMilli());
    }

    // ==================== STATUS MAPPING ====================

    @Test
    @DisplayName("Status mapping: PURCHASED and !expired ⇒ ACTIVE")
    void statusMapping_purchasedAndNotExpiredMapsToActive() {
        // Given
        googlePurchase.setPurchaseState("PURCHASED");
        googlePurchase.setExpiryTimeMillis(Instant.now().plusSeconds(3600).toEpochMilli()); // Not expired

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Status mapping: canceled ⇒ CANCELLED")
    void statusMapping_canceledMapsToCancelled() {
        // Given
        googlePurchase.setPurchaseState("CANCELED");

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.CANCELLED);
    }

    @Test
    @DisplayName("Status mapping: expired ⇒ EXPIRED")
    void statusMapping_expiredMapsToExpired() {
        // Given
        googlePurchase.setPurchaseState("PURCHASED");
        googlePurchase.setExpiryTimeMillis(Instant.now().minusSeconds(3600).toEpochMilli()); // Expired

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("Status mapping: other states ⇒ INCOMPLETE")
    void statusMapping_otherStatesMapsToIncomplete() {
        // Given
        googlePurchase.setPurchaseState("PENDING");

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.INCOMPLETE);
    }

    // ==================== IDEMPOTENCY ====================

    @Test
    @DisplayName("Idempotency: Same purchase token updates existing subscription vs creating new")
    void idempotency_samePurchaseTokenUpdatesExistingSubscriptionVsCreatingNew() {
        // Given - Existing subscription with same purchase token
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.of(existingSubscription)); // Existing subscription found
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        assertThat(result).isEqualTo(existingSubscription);
        
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        // Verify it's the same subscription (not a new one)
        assertThat(savedSubscription).isEqualTo(existingSubscription);
        
        // Verify the existing subscription was updated with new data
        assertThat(savedSubscription.getUser()).isEqualTo(testUser);
        assertThat(savedSubscription.getSubscriptionPlan()).isEqualTo(premiumPlan);
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Idempotency: Different purchase token creates new subscription")
    void idempotency_differentPurchaseTokenCreatesNewSubscription() {
        // Given - Different purchase token
        CreateSubscriptionRequest differentRequest = new CreateSubscriptionRequest(
                createRequest.planId(),
                createRequest.googleProductId(),
                "different_purchase_token"
        );

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "different_purchase_token"))
                .thenReturn(Optional.empty()); // No existing subscription found
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, differentRequest, googlePurchase);

        // Then
        assertThat(result).isNotNull();
        
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        // Verify it's a new subscription
        assertThat(savedSubscription.getExternalSubscriptionId()).isEqualTo("different_purchase_token");
        assertThat(savedSubscription.getUser()).isEqualTo(testUser);
        assertThat(savedSubscription.getSubscriptionPlan()).isEqualTo(premiumPlan);
    }

    @Test
    @DisplayName("Idempotency: Multiple calls with same purchase token are idempotent")
    void idempotency_multipleCallsWithSamePurchaseTokenAreIdempotent() {
        // Given
        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty()) // First call - no existing
                .thenReturn(Optional.of(existingSubscription)); // Second call - existing found
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When - First call
        UserSubscription result1 = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);
        assertThat(result1).isNotNull();

        // When - Second call with same data
        UserSubscription result2 = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);
        assertThat(result2).isNotNull();

        // Then - Both calls should succeed and return the same subscription
        assertThat(result1).isEqualTo(result2);
        
        // Verify save was called twice (first creates, second updates)
        verify(userSubscriptionRepository, times(2)).save(any(UserSubscription.class));
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("Edge case: Handles null autoRenewing gracefully")
    void edgeCase_handlesNullAutoRenewingGracefully() {
        // Given
        googlePurchase.setAutoRenewing(null);

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        // Should default to false when null
        assertThat(savedSubscription.isAutoRenew()).isFalse();
    }

    @Test
    @DisplayName("Edge case: Handles null purchase state gracefully")
    void edgeCase_handlesNullPurchaseStateGracefully() {
        // Given
        googlePurchase.setPurchaseState(null);

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        // Should map to INCOMPLETE when purchase state is null
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.INCOMPLETE);
        assertThat(savedSubscription.getProviderStatusRaw()).isNull();
    }

    @Test
    @DisplayName("Edge case: Handles very large timestamp values")
    void edgeCase_handlesVeryLargeTimestampValues() {
        // Given - Very large timestamp values
        long veryLargeStart = Long.MAX_VALUE - 1000;
        long veryLargeEnd = Long.MAX_VALUE - 500;
        
        googlePurchase.setStartTimeMillis(veryLargeStart);
        googlePurchase.setExpiryTimeMillis(veryLargeEnd);

        when(userSubscriptionRepository.findActiveSubscriptionsWithLock(testUser)).thenReturn(List.of());
        when(subscriptionPlanRepository.findById(createRequest.planId())).thenReturn(Optional.of(premiumPlan));
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                UserSubscription.PaymentProvider.GOOGLE_PLAY, "purchase_token_123"))
                .thenReturn(Optional.empty());
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenReturn(existingSubscription);

        // When
        UserSubscription result = subscriptionSaver.saveFromGoogle(testUser, createRequest, googlePurchase);

        // Then
        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(captor.capture());
        UserSubscription savedSubscription = captor.getValue();
        
        // Should handle large timestamps without throwing exceptions
        assertThat(savedSubscription.getCurrentPeriodStart()).isNotNull();
        assertThat(savedSubscription.getCurrentPeriodEnd()).isNotNull();
        assertThat(savedSubscription.getNextBillingDate()).isNotNull();
    }
}
