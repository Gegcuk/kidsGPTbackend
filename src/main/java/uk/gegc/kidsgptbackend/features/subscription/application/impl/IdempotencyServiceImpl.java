package uk.gegc.kidsgptbackend.features.subscription.application.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.WebhookEvent;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.WebhookEventRepository;
import uk.gegc.kidsgptbackend.features.subscription.application.IdempotencyService;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyServiceImpl implements IdempotencyService {

    private final WebhookEventRepository webhookEventRepository;

    @Override
    @Transactional
    public boolean tryAcceptWebhookEvent(WebhookEvent.PaymentProvider provider, String externalEventId, 
                                        String eventType, String payload) {
        // Check if we've already seen this event
        if (webhookEventRepository.existsByPaymentProviderAndExternalEventId(provider, externalEventId)) {
            log.info("Webhook event already processed: {} - {}", provider, externalEventId);
            return false;
        }

        // Create new webhook event record
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setPaymentProvider(provider);
        webhookEvent.setExternalEventId(externalEventId);
        webhookEvent.setEventType(eventType);
        webhookEvent.setPayload(payload);
        webhookEvent.setProcessed(false);
        webhookEvent.setCreatedAt(Instant.now());

        try {
            webhookEventRepository.save(webhookEvent);
            log.info("Accepted new webhook event: {} - {}", provider, externalEventId);
            return true;
        } catch (Exception e) {
            // This could be a race condition where another instance processed it
            log.warn("Failed to save webhook event, likely already processed: {} - {}", provider, externalEventId);
            return false;
        }
    }

    @Override
    @Transactional
    public void markWebhookEventProcessed(WebhookEvent.PaymentProvider provider, String externalEventId) {
        webhookEventRepository.findByPaymentProviderAndExternalEventId(provider, externalEventId)
                .ifPresent(event -> {
                    event.setProcessed(true);
                    event.setProcessedAt(Instant.now());
                    webhookEventRepository.save(event);
                    log.info("Marked webhook event as processed: {} - {}", provider, externalEventId);
                });
    }

    @Override
    @Transactional
    public void markWebhookEventFailed(WebhookEvent.PaymentProvider provider, String externalEventId, String error) {
        webhookEventRepository.findByPaymentProviderAndExternalEventId(provider, externalEventId)
                .ifPresent(event -> {
                    event.setProcessingError(error);
                    webhookEventRepository.save(event);
                    log.error("Marked webhook event as failed: {} - {} - {}", provider, externalEventId, error);
                });
    }
}
