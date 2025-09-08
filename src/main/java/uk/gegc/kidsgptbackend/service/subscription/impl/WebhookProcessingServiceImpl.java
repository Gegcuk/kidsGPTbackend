package uk.gegc.kidsgptbackend.service.subscription.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.repository.subscription.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;
import uk.gegc.kidsgptbackend.service.subscription.WebhookProcessingService;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookProcessingServiceImpl implements WebhookProcessingService {

    private final ObjectMapper objectMapper;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final GooglePlayClient googlePlayClient;

    @Value("${google.play.webhook.audience:}")
    private String googlePlayAudience;

    // Google Play webhook processing
    @Override
    public boolean verifyGooglePlaySignature(String authorization, String payload) {
        // TODO: Implement Google Play JWT signature verification
        // This should verify the JWT token from Google Cloud Pub/Sub
        // The JWT should be verified against Google's public keys
        log.info("Verifying Google Play webhook signature");
        
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        
        // For now, just check if we have a bearer token
        // In production, you would verify the JWT signature
        return true;
    }

    @Override
    public String extractGooglePlayEventId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode message = root.get("message");
            if (message != null) {
                return message.get("messageId").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting Google Play event ID", e);
            return null;
        }
    }

    @Override
    public String extractGooglePlayEventType(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode message = root.get("message");
            if (message != null && message.has("data")) {
                String data = message.get("data").asText();
                byte[] decodedData = Base64.getDecoder().decode(data);
                JsonNode dataNode = objectMapper.readTree(decodedData);
                
                // Check for subscription notification
                if (dataNode.has("subscriptionNotification")) {
                    JsonNode notification = dataNode.get("subscriptionNotification");
                    int notificationType = notification.get("notificationType").asInt();
                    
                    return switch (notificationType) {
                        case 1 -> "SUBSCRIPTION_RECOVERED";
                        case 2 -> "SUBSCRIPTION_RENEWED";
                        case 3 -> "SUBSCRIPTION_CANCELED";
                        case 4 -> "SUBSCRIPTION_PURCHASED";
                        case 5 -> "SUBSCRIPTION_ON_HOLD";
                        case 6 -> "SUBSCRIPTION_IN_GRACE_PERIOD";
                        case 7 -> "SUBSCRIPTION_RESTARTED";
                        case 8 -> "SUBSCRIPTION_PRICE_CHANGE_CONFIRMED";
                        case 9 -> "SUBSCRIPTION_DEFERRED";
                        case 10 -> "SUBSCRIPTION_PAUSED";
                        case 11 -> "SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED";
                        case 12 -> "SUBSCRIPTION_REVOKED";
                        case 13 -> "SUBSCRIPTION_EXPIRED";
                        default -> "UNKNOWN_NOTIFICATION_TYPE_" + notificationType;
                    };
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting Google Play event type", e);
            return null;
        }
    }

    @Override
    @Transactional
    public void processGooglePlayWebhook(String eventId, String eventType, String payload) {
        try {
            log.info("Processing Google Play webhook: {} - {}", eventId, eventType);
            
            JsonNode root = objectMapper.readTree(payload);
            JsonNode message = root.get("message");
            if (message != null && message.has("data")) {
                String data = message.get("data").asText();
                byte[] decodedData = Base64.getDecoder().decode(data);
                JsonNode dataNode = objectMapper.readTree(decodedData);
                
                if (dataNode.has("subscriptionNotification")) {
                    processGooglePlaySubscriptionNotification(dataNode.get("subscriptionNotification"), eventType);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Google Play webhook: {}", eventId, e);
            throw new RuntimeException("Failed to process Google Play webhook", e);
        }
    }

    private void processGooglePlaySubscriptionNotification(JsonNode notification, String eventType) {
        String productId = notification.get("subscriptionId").asText(); // This is actually the product ID
        String purchaseToken = notification.get("purchaseToken").asText();
        
        // Find subscription by purchaseToken (the unique identifier)
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByPaymentProviderAndExternalSubscriptionId(
                        UserSubscription.PaymentProvider.GOOGLE_PLAY, purchaseToken);
        
        if (subscriptionOpt.isEmpty()) {
            log.warn("Subscription not found for Google Play purchase token: {}", purchaseToken);
            return;
        }
        
        UserSubscription subscription = subscriptionOpt.get();
        
        // Fetch authoritative state from Google Play API
        try {
            GooglePlaySubscriptionPurchase googlePurchase = googlePlayClient.getSubscriptionPurchase(
                    productId, purchaseToken);
            
            // Update subscription with fresh data from Google
            subscription.setStatus(mapGooglePlayStatus(googlePurchase));
            subscription.setCurrentPeriodStart(Instant.ofEpochMilli(googlePurchase.getStartTimeMillis()));
            subscription.setCurrentPeriodEnd(Instant.ofEpochMilli(googlePurchase.getExpiryTimeMillis()));
            subscription.setAutoRenew(Boolean.TRUE.equals(googlePurchase.getAutoRenewing()));
            subscription.setProviderStatusRaw(googlePurchase.getPurchaseState());
            
            // Handle specific event types
            switch (eventType) {
                case "SUBSCRIPTION_CANCELED" -> {
                    subscription.setCancelledAt(Instant.now());
                }
                case "SUBSCRIPTION_IN_GRACE_PERIOD" -> {
                    subscription.setGracePeriodEnd(Instant.now().plusSeconds(3 * 24 * 60 * 60)); // 3 days
                }
                case "SUBSCRIPTION_PAUSED" -> {
                    subscription.setPausedAt(Instant.now());
                }
                case "SUBSCRIPTION_REVOKED" -> {
                    subscription.setCancelledAt(Instant.now());
                }
            }
            
            userSubscriptionRepository.save(subscription);
            log.info("Updated subscription {} for Google Play event: {} with fresh API data", 
                    subscription.getId(), eventType);
            
        } catch (Exception e) {
            log.error("Failed to fetch Google Play subscription data for token: {}", purchaseToken, e);
            // Still update the raw event type for debugging
            subscription.setProviderStatusRaw(eventType);
            userSubscriptionRepository.save(subscription);
        }
    }
    
    private UserSubscription.SubscriptionStatus mapGooglePlayStatus(GooglePlaySubscriptionPurchase purchase) {
        if (purchase.isPurchased() && !purchase.isExpired()) {
            return UserSubscription.SubscriptionStatus.ACTIVE;
        } else if (purchase.isCanceled()) {
            return UserSubscription.SubscriptionStatus.CANCELLED;
        } else if (purchase.isExpired()) {
            return UserSubscription.SubscriptionStatus.EXPIRED;
        } else {
            return UserSubscription.SubscriptionStatus.INCOMPLETE;
        }
    }

}
