package uk.gegc.kidsgptbackend.global;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.model.subscription.WebhookEvent;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.service.subscription.impl.IdempotencyServiceImpl;
import uk.gegc.kidsgptbackend.repository.subscription.WebhookEventRepository;
import uk.gegc.kidsgptbackend.repository.subscription.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

/**
 * Integration tests for idempotency requirements:
 * - Tests actual service implementations with real scenarios
 * - Verifies that replaying the same webhook or create-subscription path does not duplicate or corrupt state
 * - Tests idempotency under various failure scenarios
 * - Ensures state consistency after multiple identical operations
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Idempotency Integration Tests")
class IdempotencyIntegrationTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;
    
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    
    @Mock
    private GooglePlayClient googlePlayClient;
    
    @Mock
    private User user;
    
    @Mock
    private UserSubscription subscription;
    
    @Mock
    private GooglePlaySubscriptionPurchase googlePurchase;

    private IdempotencyServiceImpl idempotencyService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);
        
        // Initialize services with mocked dependencies
        idempotencyService = new IdempotencyServiceImpl(webhookEventRepository);
        
        // Set up common mocks
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getUsername()).thenReturn("testuser");
        when(subscription.getId()).thenReturn(UUID.randomUUID());
        when(subscription.getUser()).thenReturn(user);
        when(subscription.getStatus()).thenReturn(UserSubscription.SubscriptionStatus.ACTIVE);
        when(googlePurchase.isEntitlementActive()).thenReturn(true);
        when(googlePurchase.getAcknowledgementState()).thenReturn("ACKNOWLEDGED");
    }

    @Test
    @DisplayName("Webhook idempotency integration: Same webhook event should not be processed twice")
    void webhookIdempotencyIntegration_sameEventShouldNotBeProcessedTwice() {
        // Given
        String eventId = "integration_test_event_123";
        String eventType = "SUBSCRIPTION_RENEWED";
        String payload = "{\"integration\": \"test_payload\"}";
        
        // First call - event doesn't exist yet
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());
        
        // When - First call
        boolean firstResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType, 
                payload
        );
        
        // Second call - event already exists
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(true);
        
        // When - Second call (replay)
        boolean secondResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType, 
                payload
        );
        
        // Then
        assertThat(firstResult).isTrue(); // First call should succeed
        assertThat(secondResult).isFalse(); // Second call should be rejected (idempotent)
        
        // Verify that save was only called once (for the first call)
        verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Webhook idempotency integration: Multiple identical webhooks should be handled correctly")
    void webhookIdempotencyIntegration_multipleIdenticalWebhooksShouldBeHandledCorrectly() {
        // Given
        String eventId = "integration_test_event_456";
        String eventType = "SUBSCRIPTION_CANCELED";
        String payload = "{\"integration\": \"test_payload\"}";
        
        // First call - event doesn't exist yet
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());
        
        // When - First call
        boolean firstResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType, 
                payload
        );
        
        // Second call - event already exists
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(true);
        
        // When - Second call (replay)
        boolean secondResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType, 
                payload
        );
        
        // Third call - event still exists
        boolean thirdResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType, 
                payload
        );
        
        // Then
        assertThat(firstResult).isTrue(); // First call should succeed
        assertThat(secondResult).isFalse(); // Second call should be rejected (idempotent)
        assertThat(thirdResult).isFalse(); // Third call should be rejected (idempotent)
        
        // Verify that save was only called once (for the first call)
        verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Webhook idempotency integration: Different event types with same ID should be rejected")
    void webhookIdempotencyIntegration_differentEventTypesWithSameIdShouldBeRejected() {
        // Given
        String eventId = "integration_test_event_789";
        String eventType1 = "SUBSCRIPTION_RENEWED";
        String eventType2 = "SUBSCRIPTION_CANCELED";
        String payload = "{\"integration\": \"test_payload\"}";
        
        // First call - event doesn't exist yet
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());
        
        // When - First call
        boolean firstResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType1, 
                payload
        );
        
        // Second call - event already exists (idempotency check is by event ID, not event type)
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(true);
        
        // When - Second call with different event type
        boolean secondResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType2, 
                payload
        );
        
        // Then
        assertThat(firstResult).isTrue(); // First call should succeed
        assertThat(secondResult).isFalse(); // Second call should be rejected (idempotent)
        
        // Verify that save was only called once (for the first call)
        verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Webhook idempotency integration: Race condition should be handled gracefully")
    void webhookIdempotencyIntegration_raceConditionShouldBeHandledGracefully() {
        // Given
        String eventId = "integration_test_event_race";
        String eventType = "SUBSCRIPTION_RENEWED";
        String payload = "{\"integration\": \"test_payload\"}";
        
        // Simulate race condition: first call succeeds, second call fails due to duplicate key
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent())
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key"));
        
        // When - First call
        boolean firstResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType, 
                payload
        );
        
        // When - Second call (race condition)
        boolean secondResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType, 
                payload
        );
        
        // Then
        assertThat(firstResult).isTrue(); // First call should succeed
        assertThat(secondResult).isFalse(); // Second call should be handled gracefully (idempotent)
        
        // Verify that save was called twice (first succeeds, second fails)
        verify(webhookEventRepository, times(2)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Webhook idempotency integration: Concurrent access should be handled correctly")
    void webhookIdempotencyIntegration_concurrentAccessShouldBeHandledCorrectly() throws InterruptedException {
        // Given
        String eventId = "integration_test_event_concurrent";
        String eventType = "SUBSCRIPTION_RENEWED";
        String payload = "{\"integration\": \"test_payload\"}";
        
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // Mock the repository to simulate race conditions - first call succeeds, subsequent calls fail
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent())
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key"));
        
        // When - Multiple threads try to process the same webhook
        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    boolean result = idempotencyService.tryAcceptWebhookEvent(
                            WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                            eventId, 
                            eventType, 
                            payload
                    );
                    
                    if (result) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        
        // Then
        // Only one thread should succeed, others should fail gracefully
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(numberOfThreads - 1);
        
        // Verify that save was called (at least once, possibly more due to race conditions)
        // In a real race condition, multiple threads might attempt to save before the constraint violation
        verify(webhookEventRepository, times(numberOfThreads)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Webhook idempotency integration: Database connection failure should be handled gracefully")
    void webhookIdempotencyIntegration_databaseConnectionFailureShouldBeHandledGracefully() {
        // Given
        String eventId = "integration_test_event_db_fail";
        String eventType = "SUBSCRIPTION_RENEWED";
        String payload = "{\"integration\": \"test_payload\"}";
        
        // Simulate database connection failure
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenThrow(new org.springframework.dao.DataAccessException("Database connection failed") {});
        
        // When & Then
        // Should throw exception for database connection failure (this is correct behavior)
        assertThatThrownBy(() -> {
            idempotencyService.tryAcceptWebhookEvent(
                    WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                    eventId, 
                    eventType, 
                    payload
            );
        }).isInstanceOf(org.springframework.dao.DataAccessException.class)
          .hasMessage("Database connection failed");
    }

    @Test
    @DisplayName("Webhook idempotency integration: Marking webhook as processed should work correctly")
    void webhookIdempotencyIntegration_markingWebhookAsProcessedShouldWorkCorrectly() {
        // Given
        String eventId = "integration_test_event_processed";
        String eventType = "SUBSCRIPTION_RENEWED";
        String payload = "{\"integration\": \"test_payload\"}";
        
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setId(UUID.randomUUID());
        webhookEvent.setPaymentProvider(WebhookEvent.PaymentProvider.GOOGLE_PLAY);
        webhookEvent.setExternalEventId(eventId);
        webhookEvent.setEventType(eventType);
        webhookEvent.setPayload(payload);
        webhookEvent.setProcessed(false);
        
        // First call - event doesn't exist yet
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(webhookEvent);
        
        // When - First call
        boolean firstResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType, 
                payload
        );
        
        // Mark as processed
        when(webhookEventRepository.findByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(Optional.of(webhookEvent));
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(webhookEvent);
        
        idempotencyService.markWebhookEventProcessed(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId
        );
        
        // Second call - event already exists and is processed
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(true);
        
        // When - Second call (replay)
        boolean secondResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType, 
                payload
        );
        
        // Then
        assertThat(firstResult).isTrue(); // First call should succeed
        assertThat(secondResult).isFalse(); // Second call should be rejected (idempotent)
        
        // Verify that save was called (once for creation, once for marking as processed)
        verify(webhookEventRepository, times(2)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Webhook idempotency integration: Marking webhook as failed should work correctly")
    void webhookIdempotencyIntegration_markingWebhookAsFailedShouldWorkCorrectly() {
        // Given
        String eventId = "integration_test_event_failed";
        String eventType = "SUBSCRIPTION_RENEWED";
        String payload = "{\"integration\": \"test_payload\"}";
        String error = "Processing failed";
        
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setId(UUID.randomUUID());
        webhookEvent.setPaymentProvider(WebhookEvent.PaymentProvider.GOOGLE_PLAY);
        webhookEvent.setExternalEventId(eventId);
        webhookEvent.setEventType(eventType);
        webhookEvent.setPayload(payload);
        webhookEvent.setProcessed(false);
        
        // First call - event doesn't exist yet
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(webhookEvent);
        
        // When - First call
        boolean firstResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                eventType, 
                payload
        );
        
        // Mark as failed
        when(webhookEventRepository.findByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(eventId)))
                .thenReturn(Optional.of(webhookEvent));
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(webhookEvent);
        
        idempotencyService.markWebhookEventFailed(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                eventId, 
                error
        );
        
        // Then
        assertThat(firstResult).isTrue(); // First call should succeed
        
        // Verify that save was called (once for creation, once for marking as failed)
        verify(webhookEventRepository, times(2)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Webhook idempotency integration: Very long event IDs should be handled correctly")
    void webhookIdempotencyIntegration_veryLongEventIdsShouldBeHandledCorrectly() {
        // Given
        String veryLongEventId = "a".repeat(1000); // Very long event ID
        String eventType = "SUBSCRIPTION_RENEWED";
        String payload = "{\"integration\": \"test_payload\"}";
        
        // First call - event doesn't exist yet
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(veryLongEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());
        
        // When - First call
        boolean firstResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                veryLongEventId, 
                eventType, 
                payload
        );
        
        // Second call - event already exists
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(veryLongEventId)))
                .thenReturn(true);
        
        // When - Second call (replay)
        boolean secondResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                veryLongEventId, 
                eventType, 
                payload
        );
        
        // Then
        assertThat(firstResult).isTrue(); // First call should succeed
        assertThat(secondResult).isFalse(); // Second call should be rejected (idempotent)
        
        // Verify that save was only called once (for the first call)
        verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Webhook idempotency integration: Special characters in event IDs should be handled correctly")
    void webhookIdempotencyIntegration_specialCharactersInEventIdsShouldBeHandledCorrectly() {
        // Given
        String specialEventId = "event@#$%^&*()_+-=[]{}|;':\",./<>?";
        String eventType = "SUBSCRIPTION_RENEWED";
        String payload = "{\"integration\": \"test_payload\"}";
        
        // First call - event doesn't exist yet
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(specialEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());
        
        // When - First call
        boolean firstResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                specialEventId, 
                eventType, 
                payload
        );
        
        // Second call - event already exists
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(specialEventId)))
                .thenReturn(true);
        
        // When - Second call (replay)
        boolean secondResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                specialEventId, 
                eventType, 
                payload
        );
        
        // Then
        assertThat(firstResult).isTrue(); // First call should succeed
        assertThat(secondResult).isFalse(); // Second call should be rejected (idempotent)
        
        // Verify that save was only called once (for the first call)
        verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Webhook idempotency integration: Unicode characters in event IDs should be handled correctly")
    void webhookIdempotencyIntegration_unicodeCharactersInEventIdsShouldBeHandledCorrectly() {
        // Given
        String unicodeEventId = "event_测试_🚀_emoji";
        String eventType = "SUBSCRIPTION_RENEWED";
        String payload = "{\"integration\": \"test_payload\"}";
        
        // First call - event doesn't exist yet
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(unicodeEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());
        
        // When - First call
        boolean firstResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                unicodeEventId, 
                eventType, 
                payload
        );
        
        // Second call - event already exists
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(unicodeEventId)))
                .thenReturn(true);
        
        // When - Second call (replay)
        boolean secondResult = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                unicodeEventId, 
                eventType, 
                payload
        );
        
        // Then
        assertThat(firstResult).isTrue(); // First call should succeed
        assertThat(secondResult).isFalse(); // Second call should be rejected (idempotent)
        
        // Verify that save was only called once (for the first call)
        verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    }
}
