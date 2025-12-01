package uk.gegc.kidsgptbackend.features.subscription.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.WebhookEvent;
import uk.gegc.kidsgptbackend.features.subscription.application.IdempotencyService;
import uk.gegc.kidsgptbackend.features.subscription.application.WebhookProcessingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookController Tests")
class WebhookControllerTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private WebhookProcessingService webhookProcessingService;

    @InjectMocks
    private uk.gegc.kidsgptbackend.features.subscription.api.WebhookController webhookController;

    private String testPayload;
    private String testEventId;
    private String testEventType;

    @BeforeEach
    void setUp() {
        testEventId = "test_message_id_123";
        testEventType = "SUBSCRIPTION_RENEWED";
        testPayload = """
            {
                "message": {
                    "messageId": "test_message_id_123",
                    "data": "eyJzdWJzY3JpcHRpb25Ob3RpZmljYXRpb24iOiB7InZlcnNpb24iOiAiMS4wIiwibm90aWZpY2F0aW9uVHlwZSI6IDIsInB1cmNoYXNlVG9rZW4iOiAidGVzdF9wdXJjaGFzZV90b2tlbiIsInN1YnNjcmlwdGlvbklkIjogInBsdXNfbW9udGhseSJ9fQ=="
                }
            }
            """;
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - returns 200 when signature is invalid")
    void handleGooglePlayWebhook_returns200WhenSignatureIsInvalid() {
        // Given
        String invalidAuthorization = "Invalid token";
        when(webhookProcessingService.verifyGooglePlaySignature(invalidAuthorization, testPayload))
                .thenReturn(false);

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(invalidAuthorization, testPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookProcessingService, never()).extractGooglePlayEventId(anyString());
        verify(webhookProcessingService, never()).extractGooglePlayEventType(anyString());
        verify(idempotencyService, never()).tryAcceptWebhookEvent(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - returns 200 when authorization is null")
    void handleGooglePlayWebhook_returns200WhenAuthorizationIsNull() {
        // Given
        when(webhookProcessingService.verifyGooglePlaySignature(null, testPayload))
                .thenReturn(false);

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(null, testPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookProcessingService, never()).extractGooglePlayEventId(anyString());
        verify(webhookProcessingService, never()).extractGooglePlayEventType(anyString());
        verify(idempotencyService, never()).tryAcceptWebhookEvent(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - returns 200 when eventId is null")
    void handleGooglePlayWebhook_returns200WhenEventIdIsNull() {
        // Given
        String validAuthorization = "Bearer valid_token";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, testPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(testPayload))
                .thenReturn(null);
        when(webhookProcessingService.extractGooglePlayEventType(testPayload))
                .thenReturn(testEventType);

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(validAuthorization, testPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(idempotencyService, never()).tryAcceptWebhookEvent(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - returns 200 when eventType is null")
    void handleGooglePlayWebhook_returns200WhenEventTypeIsNull() {
        // Given
        String validAuthorization = "Bearer valid_token";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, testPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(testPayload))
                .thenReturn(testEventId);
        when(webhookProcessingService.extractGooglePlayEventType(testPayload))
                .thenReturn(null);

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(validAuthorization, testPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(idempotencyService, never()).tryAcceptWebhookEvent(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - returns 200 when event already processed")
    void handleGooglePlayWebhook_returns200WhenEventAlreadyProcessed() {
        // Given
        String validAuthorization = "Bearer valid_token";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, testPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(testPayload))
                .thenReturn(testEventId);
        when(webhookProcessingService.extractGooglePlayEventType(testPayload))
                .thenReturn(testEventType);
        when(idempotencyService.tryAcceptWebhookEvent(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId), eq(testEventType), eq(testPayload)))
                .thenReturn(false);

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(validAuthorization, testPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookProcessingService, never()).processGooglePlayWebhook(anyString(), anyString(), anyString());
        verify(idempotencyService, never()).markWebhookEventProcessed(any(), anyString());
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - processes event successfully and returns 200")
    void handleGooglePlayWebhook_processesEventSuccessfullyAndReturns200() {
        // Given
        String validAuthorization = "Bearer valid_token";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, testPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(testPayload))
                .thenReturn(testEventId);
        when(webhookProcessingService.extractGooglePlayEventType(testPayload))
                .thenReturn(testEventType);
        when(idempotencyService.tryAcceptWebhookEvent(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId), eq(testEventType), eq(testPayload)))
                .thenReturn(true);

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(validAuthorization, testPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookProcessingService).processGooglePlayWebhook(testEventId, testEventType, testPayload);
        verify(idempotencyService).markWebhookEventProcessed(WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId);
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - returns 200 when processing throws exception")
    void handleGooglePlayWebhook_returns200WhenProcessingThrowsException() {
        // Given
        String validAuthorization = "Bearer valid_token";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, testPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(testPayload))
                .thenReturn(testEventId);
        when(webhookProcessingService.extractGooglePlayEventType(testPayload))
                .thenReturn(testEventType);
        when(idempotencyService.tryAcceptWebhookEvent(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId), eq(testEventType), eq(testPayload)))
                .thenReturn(true);
        doThrow(new RuntimeException("Processing failed"))
                .when(webhookProcessingService).processGooglePlayWebhook(testEventId, testEventType, testPayload);

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(validAuthorization, testPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(idempotencyService, never()).markWebhookEventProcessed(any(), anyString());
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - returns 200 when extraction throws exception")
    void handleGooglePlayWebhook_returns200WhenExtractionThrowsException() {
        // Given
        String validAuthorization = "Bearer valid_token";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, testPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(testPayload))
                .thenThrow(new RuntimeException("Extraction failed"));

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(validAuthorization, testPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookProcessingService, never()).extractGooglePlayEventType(anyString());
        verify(idempotencyService, never()).tryAcceptWebhookEvent(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - returns 200 when idempotency service throws exception")
    void handleGooglePlayWebhook_returns200WhenIdempotencyServiceThrowsException() {
        // Given
        String validAuthorization = "Bearer valid_token";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, testPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(testPayload))
                .thenReturn(testEventId);
        when(webhookProcessingService.extractGooglePlayEventType(testPayload))
                .thenReturn(testEventType);
        when(idempotencyService.tryAcceptWebhookEvent(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId), eq(testEventType), eq(testPayload)))
                .thenThrow(new RuntimeException("Idempotency service failed"));

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(validAuthorization, testPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookProcessingService, never()).processGooglePlayWebhook(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - returns 200 when markWebhookEventProcessed throws exception")
    void handleGooglePlayWebhook_returns200WhenMarkWebhookEventProcessedThrowsException() {
        // Given
        String validAuthorization = "Bearer valid_token";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, testPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(testPayload))
                .thenReturn(testEventId);
        when(webhookProcessingService.extractGooglePlayEventType(testPayload))
                .thenReturn(testEventType);
        when(idempotencyService.tryAcceptWebhookEvent(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId), eq(testEventType), eq(testPayload)))
                .thenReturn(true);
        doThrow(new RuntimeException("Mark processed failed"))
                .when(idempotencyService).markWebhookEventProcessed(WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId);

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(validAuthorization, testPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - calls services in correct order")
    void handleGooglePlayWebhook_callsServicesInCorrectOrder() {
        // Given
        String validAuthorization = "Bearer valid_token";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, testPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(testPayload))
                .thenReturn(testEventId);
        when(webhookProcessingService.extractGooglePlayEventType(testPayload))
                .thenReturn(testEventType);
        when(idempotencyService.tryAcceptWebhookEvent(
                eq(WebhookEvent.PaymentProvider.GOOGLE_PLAY), eq(testEventId), eq(testEventType), eq(testPayload)))
                .thenReturn(true);

        // When
        webhookController.handleGooglePlayWebhook(validAuthorization, testPayload);

        // Then
        var inOrder = inOrder(webhookProcessingService, idempotencyService);
        inOrder.verify(webhookProcessingService).verifyGooglePlaySignature(validAuthorization, testPayload);
        inOrder.verify(webhookProcessingService).extractGooglePlayEventId(testPayload);
        inOrder.verify(webhookProcessingService).extractGooglePlayEventType(testPayload);
        inOrder.verify(idempotencyService).tryAcceptWebhookEvent(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId, testEventType, testPayload);
        inOrder.verify(webhookProcessingService).processGooglePlayWebhook(testEventId, testEventType, testPayload);
        inOrder.verify(idempotencyService).markWebhookEventProcessed(WebhookEvent.PaymentProvider.GOOGLE_PLAY, testEventId);
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - handles empty payload")
    void handleGooglePlayWebhook_handlesEmptyPayload() {
        // Given
        String validAuthorization = "Bearer valid_token";
        String emptyPayload = "";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, emptyPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(emptyPayload))
                .thenReturn(null);
        when(webhookProcessingService.extractGooglePlayEventType(emptyPayload))
                .thenReturn(null);

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(validAuthorization, emptyPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(idempotencyService, never()).tryAcceptWebhookEvent(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handleGooglePlayWebhook - handles malformed payload")
    void handleGooglePlayWebhook_handlesMalformedPayload() {
        // Given
        String validAuthorization = "Bearer valid_token";
        String malformedPayload = "invalid json";
        when(webhookProcessingService.verifyGooglePlaySignature(validAuthorization, malformedPayload))
                .thenReturn(true);
        when(webhookProcessingService.extractGooglePlayEventId(malformedPayload))
                .thenThrow(new RuntimeException("Invalid JSON"));

        // When
        ResponseEntity<Void> response = webhookController.handleGooglePlayWebhook(validAuthorization, malformedPayload);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookProcessingService, never()).extractGooglePlayEventType(anyString());
        verify(idempotencyService, never()).tryAcceptWebhookEvent(any(), anyString(), anyString(), anyString());
    }
}
