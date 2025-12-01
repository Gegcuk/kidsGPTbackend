package uk.gegc.kidsgptbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.WebhookEvent;
import uk.gegc.kidsgptbackend.features.subscription.application.IdempotencyService;
import uk.gegc.kidsgptbackend.features.subscription.application.WebhookProcessingService;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Webhooks", description = "Webhook endpoints for payment providers")
public class WebhookController {

    private final IdempotencyService idempotencyService;
    private final WebhookProcessingService webhookProcessingService;

    @PostMapping("/google-play")
    @Operation(summary = "Google Play webhook", description = "Handle Google Play Real-Time Developer Notifications")
    public ResponseEntity<Void> handleGooglePlayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String payload) {
        
        try {
            log.info("Received Google Play webhook");
            
            // TODO: Verify JWT signature from Google Pub/Sub
            // This should verify the JWT token from Google Cloud Pub/Sub
            if (!webhookProcessingService.verifyGooglePlaySignature(authorization, payload)) {
                log.warn("Invalid Google Play webhook signature");
                return ResponseEntity.ok().build(); // Return 200 to avoid retries
            }

            // Extract event details from Pub/Sub message
            String eventId = webhookProcessingService.extractGooglePlayEventId(payload);
            String eventType = webhookProcessingService.extractGooglePlayEventType(payload);
            
            if (eventId == null || eventType == null) {
                log.warn("Invalid Google Play webhook payload structure");
                return ResponseEntity.ok().build();
            }

            // Try to accept the event (idempotency check)
            if (!idempotencyService.tryAcceptWebhookEvent(
                    WebhookEvent.PaymentProvider.GOOGLE_PLAY, eventId, eventType, payload)) {
                log.info("Google Play webhook event already processed: {}", eventId);
                return ResponseEntity.ok().build();
            }

            // Process the webhook
            webhookProcessingService.processGooglePlayWebhook(eventId, eventType, payload);
            
            // Mark as processed
            idempotencyService.markWebhookEventProcessed(
                    WebhookEvent.PaymentProvider.GOOGLE_PLAY, eventId);
            
            log.info("Successfully processed Google Play webhook: {}", eventId);
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("Error processing Google Play webhook", e);
            // Still return 200 to avoid retries for unrecoverable errors
            return ResponseEntity.ok().build();
        }
    }

}
