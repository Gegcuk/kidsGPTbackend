package uk.gegc.kidsgptbackend.features.subscription.application.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionAcknowledger {
    
    private final GooglePlayClient googlePlayClient;
    
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void acknowledge(String productId, String purchaseToken) {
        log.debug("Acknowledging subscription for product {} with token ****", productId);
        googlePlayClient.acknowledgeSubscription(productId, purchaseToken, null);
        log.info("Successfully acknowledged subscription purchase for product {} with token ****", productId);
    }
}
