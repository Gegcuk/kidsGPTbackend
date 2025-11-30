package uk.gegc.kidsgptbackend.global;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gegc.kidsgptbackend.shared.exception.*;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.service.subscription.impl.IdempotencyServiceImpl;
import uk.gegc.kidsgptbackend.repository.subscription.WebhookEventRepository;
import uk.gegc.kidsgptbackend.model.subscription.WebhookEvent;
import uk.gegc.kidsgptbackend.features.tips.api.dto.DailyTipDto;
import uk.gegc.kidsgptbackend.features.jokes.api.dto.DailyJokeDto;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for service validation and error handling:
 * - Tests actual service implementations with real scenarios
 * - Verifies that services return guarded defaults or throw domain exceptions
 * - Ensures no unexpected exceptions are thrown from public methods
 * - Tests error handling in realistic failure scenarios
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Service Validation and Error Handling Integration Tests")
class ServiceValidationAndErrorHandlingIntegrationTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;
    
    @Mock
    private User user;
    
    @Mock
    private UserSubscription subscription;

    private IdempotencyServiceImpl idempotencyService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);
        
        // Initialize services with mocked dependencies
        idempotencyService = new IdempotencyServiceImpl(webhookEventRepository);
        
        // Set up common mocks
        when(user.getUsername()).thenReturn("testuser");
        when(user.getAge()).thenReturn(10);
        when(subscription.getUser()).thenReturn(user);
        when(subscription.getStatus()).thenReturn(UserSubscription.SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for successful webhook acceptance")
    void idempotencyService_shouldReturnGuardedDefaultsForSuccessfulWebhookAcceptance() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("valid_event_id")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "valid_event_id", 
                "SUBSCRIPTION_RENEWED", 
                "valid_payload"
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success)
        verify(webhookEventRepository).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for duplicate webhook events")
    void idempotencyService_shouldReturnGuardedDefaultsForDuplicateWebhookEvents() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("duplicate_event_id")))
                .thenReturn(true);

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "duplicate_event_id", 
                "SUBSCRIPTION_RENEWED", 
                "duplicate_payload"
        );

        // Then
        assertThat(result).isFalse(); // Should return guarded default (false for duplicate)
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults when database save fails")
    void idempotencyService_shouldReturnGuardedDefaultsWhenDatabaseSaveFails() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("failing_event_id")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "failing_event_id", 
                "SUBSCRIPTION_RENEWED", 
                "failing_payload"
        );

        // Then
        assertThat(result).isFalse(); // Should return guarded default (false for failure)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for null event ID")
    void idempotencyService_shouldReturnGuardedDefaultsForNullEventId() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(null)))
                .thenReturn(false);

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                null, 
                "SUBSCRIPTION_RENEWED", 
                "null_payload"
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, null parameters handled gracefully)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for empty event ID")
    void idempotencyService_shouldReturnGuardedDefaultsForEmptyEventId() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("")))
                .thenReturn(false);

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "", 
                "SUBSCRIPTION_RENEWED", 
                "empty_payload"
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, empty parameters handled gracefully)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for null payload")
    void idempotencyService_shouldReturnGuardedDefaultsForNullPayload() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("valid_event_id")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "valid_event_id", 
                "SUBSCRIPTION_RENEWED", 
                null
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, null payload handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for concurrent access conflicts")
    void idempotencyService_shouldReturnGuardedDefaultsForConcurrentAccessConflicts() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("concurrent_event_id")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key violation"));

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "concurrent_event_id", 
                "SUBSCRIPTION_RENEWED", 
                "concurrent_payload"
        );

        // Then
        assertThat(result).isFalse(); // Should return guarded default (false for conflict)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for database timeout")
    void idempotencyService_shouldReturnGuardedDefaultsForDatabaseTimeout() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("timeout_event_id")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("Database query timeout"));

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "timeout_event_id", 
                "SUBSCRIPTION_RENEWED", 
                "timeout_payload"
        );

        // Then
        assertThat(result).isFalse(); // Should return guarded default (false for timeout)
    }

    @Test
    @DisplayName("IdempotencyService should throw exception for database connection failure")
    void idempotencyService_shouldThrowExceptionForDatabaseConnectionFailure() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("connection_fail_event_id")))
                .thenThrow(new org.springframework.dao.DataAccessException("Database connection failed") {});

        // When & Then
        // Should throw exception for database connection failure (this is correct behavior)
        assertThatThrownBy(() -> {
            idempotencyService.tryAcceptWebhookEvent(
                    WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                    "connection_fail_event_id", 
                    "SUBSCRIPTION_RENEWED", 
                    "connection_fail_payload"
            );
        }).isInstanceOf(org.springframework.dao.DataAccessException.class)
          .hasMessage("Database connection failed");
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for invalid event type")
    void idempotencyService_shouldReturnGuardedDefaultsForInvalidEventType() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("invalid_event_id")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "invalid_event_id", 
                "INVALID_EVENT_TYPE", 
                "invalid_payload"
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, invalid event type handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for very long event ID")
    void idempotencyService_shouldReturnGuardedDefaultsForVeryLongEventId() {
        // Given
        String veryLongEventId = "a".repeat(1000); // Very long event ID
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(veryLongEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                veryLongEventId, 
                "SUBSCRIPTION_RENEWED", 
                "long_id_payload"
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, long ID handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for special characters in event ID")
    void idempotencyService_shouldReturnGuardedDefaultsForSpecialCharactersInEventId() {
        // Given
        String specialEventId = "event@#$%^&*()_+-=[]{}|;':\",./<>?";
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(specialEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                specialEventId, 
                "SUBSCRIPTION_RENEWED", 
                "special_chars_payload"
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, special chars handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for Unicode characters in event ID")
    void idempotencyService_shouldReturnGuardedDefaultsForUnicodeCharactersInEventId() {
        // Given
        String unicodeEventId = "event_测试_🚀_emoji";
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(unicodeEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                unicodeEventId, 
                "SUBSCRIPTION_RENEWED", 
                "unicode_payload"
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, Unicode handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for very large payload")
    void idempotencyService_shouldReturnGuardedDefaultsForVeryLargePayload() {
        // Given
        String veryLargePayload = "x".repeat(10000); // Very large payload
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("large_payload_event_id")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "large_payload_event_id", 
                "SUBSCRIPTION_RENEWED", 
                veryLargePayload
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, large payload handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for malformed JSON payload")
    void idempotencyService_shouldReturnGuardedDefaultsForMalformedJsonPayload() {
        // Given
        String malformedJsonPayload = "{invalid json format";
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("malformed_json_event_id")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "malformed_json_event_id", 
                "SUBSCRIPTION_RENEWED", 
                malformedJsonPayload
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, malformed JSON handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for null provider")
    void idempotencyService_shouldReturnGuardedDefaultsForNullProvider() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(null), eq("null_provider_event_id")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                null, 
                "null_provider_event_id", 
                "SUBSCRIPTION_RENEWED", 
                "null_provider_payload"
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, null provider handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for all null parameters")
    void idempotencyService_shouldReturnGuardedDefaultsForAllNullParameters() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(null), eq(null)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                null, 
                null, 
                null, 
                null
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, all nulls handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for empty string parameters")
    void idempotencyService_shouldReturnGuardedDefaultsForEmptyStringParameters() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "", 
                "", 
                ""
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, empty strings handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for whitespace-only parameters")
    void idempotencyService_shouldReturnGuardedDefaultsForWhitespaceOnlyParameters() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("   ")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "   ", 
                "   ", 
                "   "
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, whitespace handled)
    }

    @Test
    @DisplayName("IdempotencyService should return guarded defaults for mixed valid and invalid parameters")
    void idempotencyService_shouldReturnGuardedDefaultsForMixedValidAndInvalidParameters() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq("valid_event_id")))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenReturn(new WebhookEvent());

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, 
                "valid_event_id", 
                null, 
                "valid_payload"
        );

        // Then
        assertThat(result).isTrue(); // Should return guarded default (true for success, mixed parameters handled)
    }
}
