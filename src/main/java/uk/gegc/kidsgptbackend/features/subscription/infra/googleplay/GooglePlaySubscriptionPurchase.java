package uk.gegc.kidsgptbackend.features.subscription.infra.googleplay;

import lombok.Data;

@Data
public class GooglePlaySubscriptionPurchase {
    
    private String purchaseToken;
    private String productId;
    private long startTimeMillis;
    private long expiryTimeMillis;
    private Boolean autoRenewing;
    private String purchaseState; // "PURCHASED", "CANCELED", etc.
    private String acknowledgementState;
    private String kind;
    private String regionCode;
    private String subscriptionId;
    private String linkedPurchaseToken;
    private String purchaseType;
    private String priceAmountMicros;
    private String priceCurrencyCode;
    private String countryCode;
    private String developerPayload;
    private String orderId;
    private String packageName;
    
    // Helper methods
    public boolean isEntitlementActive() {
        return "PURCHASED".equals(purchaseState) && 
               (autoRenewing == null || autoRenewing) &&
               System.currentTimeMillis() < expiryTimeMillis;
    }
    
    public boolean isPurchased() {
        return "PURCHASED".equals(purchaseState);
    }
    
    public boolean isCanceled() {
        return "CANCELED".equals(purchaseState);
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryTimeMillis;
    }
}

