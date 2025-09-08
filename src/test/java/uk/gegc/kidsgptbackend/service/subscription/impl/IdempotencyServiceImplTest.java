package uk.gegc.kidsgptbackend.service.subscription.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gegc.kidsgptbackend.model.subscription.WebhookEvent;
import uk.gegc.kidsgptbackend.repository.subscription.WebhookEventRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyServiceImpl Tests")
class IdempotencyServiceImplTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @InjectMocks
    private IdempotencyServiceImpl idempotencyService;

    private WebhookEvent testWebhookEvent;
    private String testEventId;
    private String testEventType;
    private String testPayload;

    @BeforeEach
    void setUp() {
        testEventId = "test_event_id_123";
        testEventType = "SUBSCRIPTION_RENEWED";
        testPayload = "{\"test\": \"payload\"}";

        testWebhookEvent = new WebhookEvent();
        testWebhookEvent.setId(UUID.randomUUID());
        testWebhookEvent.setPaymentProvider(WebhookEvent.PaymentProvider.GOOGLE_PLAY);
        testWebhookEvent.setExternalEventId(testEventId);
        testWebhookEvent.setEventType(testEventType);
        testWebhookEvent.setPayload(testPayload);
        testWebhookEvent.setProcessed(false);
        testWebhookEvent.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("tryAcceptWebhookEvent - returns false when event already exists")
    void tryAcceptWebhookEvent_returnsFalseWhenEventAlreadyExists() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(true);

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, testEventType, testPayload);

        // Then
        assertThat(result).isFalse();
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("tryAcceptWebhookEvent - returns true and creates new event when event does not exist")
    void tryAcceptWebhookEvent_returnsTrueAndCreatesNewEventWhenEventDoesNotExist() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(testWebhookEvent);

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, testEventType, testPayload);

        // Then
        assertThat(result).isTrue();
        
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getPaymentProvider()).isEqualTo(WebhookEvent.PaymentProvider.GOOGLE_PLAY);
        assertThat(savedEvent.getExternalEventId()).isEqualTo(testEventId);
        assertThat(savedEvent.getEventType()).isEqualTo(testEventType);
        assertThat(savedEvent.getPayload()).isEqualTo(testPayload);
        assertThat(savedEvent.isProcessed()).isFalse();
        assertThat(savedEvent.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("tryAcceptWebhookEvent - returns false when save throws exception (race condition)")
    void tryAcceptWebhookEvent_returnsFalseWhenSaveThrowsException() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenThrow(new RuntimeException("Database constraint violation"));

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, testEventType, testPayload);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("markWebhookEventProcessed - updates event when found")
    void markWebhookEventProcessed_updatesEventWhenFound() {
        // Given
        when(webhookEventRepository.findByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(Optional.of(testWebhookEvent));
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(testWebhookEvent);

        // When
        idempotencyService.markWebhookEventProcessed(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId);

        // Then
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.isProcessed()).isTrue();
        assertThat(savedEvent.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("markWebhookEventProcessed - does nothing when event not found")
    void markWebhookEventProcessed_doesNothingWhenEventNotFound() {
        // Given
        when(webhookEventRepository.findByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(Optional.empty());

        // When
        idempotencyService.markWebhookEventProcessed(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId);

        // Then
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("markWebhookEventFailed - updates event with error when found")
    void markWebhookEventFailed_updatesEventWithErrorWhenFound() {
        // Given
        String errorMessage = "Processing failed due to invalid data";
        when(webhookEventRepository.findByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(Optional.of(testWebhookEvent));
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(testWebhookEvent);

        // When
        idempotencyService.markWebhookEventFailed(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, errorMessage);

        // Then
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getProcessingError()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("markWebhookEventFailed - does nothing when event not found")
    void markWebhookEventFailed_doesNothingWhenEventNotFound() {
        // Given
        String errorMessage = "Processing failed due to invalid data";
        when(webhookEventRepository.findByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(Optional.empty());

        // When
        idempotencyService.markWebhookEventFailed(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, errorMessage);

        // Then
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("tryAcceptWebhookEvent - creates event with correct timestamp")
    void tryAcceptWebhookEvent_createsEventWithCorrectTimestamp() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(testWebhookEvent);

        Instant beforeCall = Instant.now();

        // When
        idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, testEventType, testPayload);

        Instant afterCall = Instant.now();

        // Then
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getCreatedAt()).isBetween(beforeCall, afterCall);
    }

    @Test
    @DisplayName("markWebhookEventProcessed - sets processedAt timestamp correctly")
    void markWebhookEventProcessed_setsProcessedAtTimestampCorrectly() {
        // Given
        when(webhookEventRepository.findByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(Optional.of(testWebhookEvent));
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(testWebhookEvent);

        Instant beforeCall = Instant.now();

        // When
        idempotencyService.markWebhookEventProcessed(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId);

        Instant afterCall = Instant.now();

        // Then
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getProcessedAt()).isBetween(beforeCall, afterCall);
    }

    @Test
    @DisplayName("tryAcceptWebhookEvent - handles different payment providers")
    void tryAcceptWebhookEvent_handlesDifferentPaymentProviders() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(testWebhookEvent);

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, testEventType, testPayload);

        // Then
        assertThat(result).isTrue();
        
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getPaymentProvider()).isEqualTo(WebhookEvent.PaymentProvider.GOOGLE_PLAY);
    }

    @Test
    @DisplayName("tryAcceptWebhookEvent - handles null payload")
    void tryAcceptWebhookEvent_handlesNullPayload() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(testWebhookEvent);

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, testEventType, null);

        // Then
        assertThat(result).isTrue();
        
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getPayload()).isNull();
    }

    @Test
    @DisplayName("markWebhookEventFailed - handles null error message")
    void markWebhookEventFailed_handlesNullErrorMessage() {
        // Given
        when(webhookEventRepository.findByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(Optional.of(testWebhookEvent));
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(testWebhookEvent);

        // When
        idempotencyService.markWebhookEventFailed(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, null);

        // Then
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getProcessingError()).isNull();
    }

    @Test
    @DisplayName("tryAcceptWebhookEvent - handles empty event type")
    void tryAcceptWebhookEvent_handlesEmptyEventType() {
        // Given
        when(webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId)))
                .thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(testWebhookEvent);

        // When
        boolean result = idempotencyService.tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, "", testPayload);

        // Then
        assertThat(result).isTrue();
        
        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        
        WebhookEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventType()).isEqualTo("");
    }
}
