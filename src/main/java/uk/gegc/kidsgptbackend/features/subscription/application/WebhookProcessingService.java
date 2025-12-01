package uk.gegc.kidsgptbackend.features.subscription.application;

public interface WebhookProcessingService {

    // Google Play webhook processing
    boolean verifyGooglePlaySignature(String authorization, String payload);
    String extractGooglePlayEventId(String payload);
    String extractGooglePlayEventType(String payload);
    void processGooglePlayWebhook(String eventId, String eventType, String payload);
}
