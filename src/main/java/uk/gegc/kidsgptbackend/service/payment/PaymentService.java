package uk.gegc.kidsgptbackend.service.payment;

import uk.gegc.kidsgptbackend.features.user.domain.model.User;

public interface PaymentService {

    /**
     * Create a subscription with Google Play Billing
     */
    String createGooglePlaySubscription(User user, String productId, String purchaseToken);

    /**
     * Cancel a subscription
     */
    boolean cancelSubscription(String purchaseToken);

    /**
     * Process refund
     */
    boolean processRefund(String orderId, String reason);
}
