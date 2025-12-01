package uk.gegc.kidsgptbackend.features.subscription.infra.googleplay;

public interface GooglePlayClient {

    /**
     * Get subscription purchase details from Google Play
     */
    GooglePlaySubscriptionPurchase getSubscriptionPurchase(String productId, String purchaseToken);

    /**
     * Verify if a purchase token is valid
     */
    boolean verifyPurchaseToken(String productId, String purchaseToken);

    /**
     * Acknowledge a subscription purchase
     * Must be called after verifying a new purchase to prevent Google from refunding it
     */
    void acknowledgeSubscription(String productId, String purchaseToken, String developerPayload);
}

