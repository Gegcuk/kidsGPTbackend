package uk.gegc.kidsgptbackend.features.subscription.application;

import uk.gegc.kidsgptbackend.features.subscription.domain.model.WebhookEvent;

public interface IdempotencyService {

    /**
     * Try to accept a webhook event for processing.
     * Returns true if this is the first time seeing this event, false if already processed.
     */
    boolean tryAcceptWebhookEvent(WebhookEvent.PaymentProvider provider, String externalEventId, 
                                 String eventType, String payload);

    /**
     * Mark a webhook event as successfully processed
     */
    void markWebhookEventProcessed(WebhookEvent.PaymentProvider provider, String externalEventId);

    /**
     * Mark a webhook event as failed with error
     */
    void markWebhookEventFailed(WebhookEvent.PaymentProvider provider, String externalEventId, String error);
}
