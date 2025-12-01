package uk.gegc.kidsgptbackend.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.features.subscription.application.SubscriptionService;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    private final SubscriptionService subscriptionService;

    /**
     * Process expired subscriptions every hour
     */
    @Scheduled(fixedRate = 3600000, zone = "UTC") // 1 hour
    public void processExpiredSubscriptions() {
        try {
            log.info("Starting expired subscriptions processing");
            subscriptionService.processExpiredSubscriptions();
            log.info("Completed expired subscriptions processing");
        } catch (Exception e) {
            log.error("Error processing expired subscriptions", e);
        }
    }

    /**
     * Reconcile subscriptions with Google Play - safety net for webhook lag
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "UTC") // Daily at 2 AM UTC
    public void reconcileSubscriptions() {
        try {
            log.info("Starting subscription reconciliation with Google Play");
            subscriptionService.reconcileWithGooglePlay();
            log.info("Completed subscription reconciliation");
        } catch (Exception e) {
            log.error("Error during subscription reconciliation", e);
        }
    }

    /**
     * Clean up expired free usage periods daily at midnight UTC
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "UTC") // Daily at midnight UTC
    public void cleanupExpiredUsagePeriods() {
        try {
            log.info("Starting cleanup of expired usage periods");
            // Clean up old usage records after free periods expire
            // This is handled in the access service
            log.info("Completed cleanup of expired usage periods");
        } catch (Exception e) {
            log.error("Error cleaning up expired usage periods", e);
        }
    }
}
