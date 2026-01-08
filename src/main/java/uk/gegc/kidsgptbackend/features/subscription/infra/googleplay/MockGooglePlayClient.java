package uk.gegc.kidsgptbackend.features.subscription.infra.googleplay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Mock Google Play client for local/dev testing. Always returns an active purchase.
 */
@Service
@ConditionalOnProperty(value = "app.subscriptions.mock-google-play", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MockGooglePlayClient implements GooglePlayClient {

    private final Clock clock;

    @Value("${app.subscriptions.mock-product-id:test_monthly}")
    private String mockProductId;

    @Value("${app.subscriptions.mock-expiry-days:30}")
    private long mockExpiryDays;

    @Override
    public GooglePlaySubscriptionPurchase getSubscriptionPurchase(String productId, String purchaseToken) {
        log.info("MockGooglePlayClient returning active purchase for product {}", productId);
        return createPurchase(productId, purchaseToken);
    }

    @Override
    public boolean verifyPurchaseToken(String productId, String purchaseToken) {
        log.info("MockGooglePlayClient verifying token for product {}", productId);
        // Always valid in mock mode, optionally warn if not using the expected mock productId
        if (!mockProductId.equals(productId)) {
            log.warn("Mock purchase using unexpected productId {} (expected {})", productId, mockProductId);
        }
        return true;
    }

    @Override
    public void acknowledgeSubscription(String productId, String purchaseToken, String developerPayload) {
        log.info("MockGooglePlayClient acknowledging subscription product {}", productId);
    }

    private GooglePlaySubscriptionPurchase createPurchase(String productId, String purchaseToken) {
        Instant now = Instant.now(clock);
        GooglePlaySubscriptionPurchase purchase = new GooglePlaySubscriptionPurchase();
        purchase.setPurchaseToken(purchaseToken);
        purchase.setProductId(productId);
        purchase.setStartTimeMillis(now.minus(1, ChronoUnit.DAYS).toEpochMilli());
        purchase.setExpiryTimeMillis(now.plus(mockExpiryDays, ChronoUnit.DAYS).toEpochMilli());
        purchase.setAutoRenewing(true);
        purchase.setPurchaseState("PURCHASED");
        purchase.setAcknowledgementState("ACKNOWLEDGED");
        purchase.setOrderId("MOCK-" + Math.abs((productId + ":" + purchaseToken).hashCode()));
        purchase.setPriceCurrencyCode("USD");
        purchase.setPriceAmountMicros("0");
        return purchase;
    }

}
