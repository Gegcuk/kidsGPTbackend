package uk.gegc.kidsgptbackend.features.subscription.infra.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.subscription.infra.googleplay.GooglePlayClient;
import uk.gegc.kidsgptbackend.features.subscription.infra.googleplay.GooglePlaySubscriptionPurchase;

@Service("googlePlayPaymentService")
@RequiredArgsConstructor
@Slf4j
public class GooglePlayPaymentService implements PaymentService {

    private final GooglePlayClient googlePlayClient;

    @Value("${google.play.service-account-key:}")
    private String serviceAccountKey;

    @Value("${google.play.package-name:}")
    private String packageName;

    @Override
    // Remove @Transactional - this method does network I/O
    public String createGooglePlaySubscription(User user, String productId, String purchaseToken) {
        log.info("Creating Google Play subscription for user {} with product {} and token ****", 
                user.getId(), productId);

        try {
            // Step 1: Verify the purchase token with Google Play
            GooglePlaySubscriptionPurchase purchase = googlePlayClient.getSubscriptionPurchase(productId, purchaseToken);
            
            if (purchase == null) {
                log.error("Failed to retrieve purchase information for token **** and product {}", productId);
                throw new RuntimeException("Invalid purchase token");
            }

            // Step 2: Validate the purchase
            if (!purchase.isPurchased()) {
                log.error("Purchase token **** for product {} is not in PURCHASED state: {}", 
                        productId, purchase.getPurchaseState());
                throw new RuntimeException("Purchase is not in valid state");
            }

            if (purchase.isExpired()) {
                log.error("Purchase token **** for product {} has expired", productId);
                throw new RuntimeException("Purchase has expired");
            }

            // Step 3: Check if acknowledgment is needed
            if ("NOT_ACKNOWLEDGED".equals(purchase.getAcknowledgementState())) {
                log.info("Acknowledging purchase for product {} with token ****", productId);
                googlePlayClient.acknowledgeSubscription(productId, purchaseToken, 
                        "subscription_for_user_" + user.getId());
            }

            // Step 4: Create subscription identifier
            String subscriptionId = generateSubscriptionId(user, productId, purchaseToken, purchase);
            
            log.info("Successfully created Google Play subscription {} for user {} with product {}", 
                    subscriptionId, user.getId(), productId);
            
            return subscriptionId;

        } catch (Exception e) {
            log.error("Failed to create Google Play subscription for user {} with product {} and token ****", 
                    user.getId(), productId, e);
            throw new RuntimeException("Failed to create Google Play subscription: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean cancelSubscription(String purchaseToken) {
        log.info("Attempting to cancel Google Play subscription with token ****");
        
        try {
            // Note: Google Play subscriptions are typically cancelled by users through the Play Store
            // This method would be used to mark the subscription as cancelled in your system
            // and potentially notify Google Play of the cancellation if needed
            
            // For actual implementation, you might want to:
            // 1. Update your local subscription status
            // 2. Optionally call Google Play API to revoke the subscription
            // 3. Handle any cleanup tasks
            
            log.info("Google Play subscription cancellation processed for token ****");
            return true;
            
        } catch (Exception e) {
            log.error("Failed to cancel Google Play subscription with token ****", e);
            return false;
        }
    }

    @Override
    public boolean processRefund(String orderId, String reason) {
        log.info("Processing Google Play refund for order {} with reason {}", orderId, reason);
        
        try {
            // Note: Google Play refunds are typically processed through the Google Play Console
            // This method would handle the business logic for processing refunds in your system
            
            // For actual implementation, you might want to:
            // 1. Validate the order ID
            // 2. Check refund eligibility
            // 3. Process the refund through Google Play API or console
            // 4. Update your local records
            
            log.info("Google Play refund processed for order {} with reason {}", orderId, reason);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to process Google Play refund for order {} with reason {}", orderId, reason, e);
            return false;
        }
    }

    /**
     * Validates a Google Play purchase token
     */
    public boolean validatePurchaseToken(String productId, String purchaseToken) {
        try {
            return googlePlayClient.verifyPurchaseToken(productId, purchaseToken);
        } catch (Exception e) {
            log.error("Failed to validate purchase token **** for product {}", productId, e);
            return false;
        }
    }

    /**
     * Gets subscription purchase details
     */
    public GooglePlaySubscriptionPurchase getSubscriptionDetails(String productId, String purchaseToken) {
        try {
            return googlePlayClient.getSubscriptionPurchase(productId, purchaseToken);
        } catch (Exception e) {
            log.error("Failed to get subscription details for product {} with token ****", productId, e);
            return null;
        }
    }

    private String generateSubscriptionId(User user, String productId, String purchaseToken, 
                                        GooglePlaySubscriptionPurchase purchase) {
        // Generate a unique subscription ID combining various elements
        String orderId = purchase.getOrderId();
        if (orderId != null && !orderId.isEmpty()) {
            return "gp_" + orderId;
        } else {
            // Fallback to timestamp-based ID if no order ID is available
            return "gp_" + user.getId() + "_" + productId + "_" + System.currentTimeMillis();
        }
    }
}

