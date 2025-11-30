package uk.gegc.kidsgptbackend.service.subscription;

import uk.gegc.kidsgptbackend.dto.subscription.*;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SubscriptionService {

    /**
     * Get all available subscription plans
     */
    List<SubscriptionPlanDto> getAvailablePlans();

    /**
     * Get subscription plan by ID
     */
    SubscriptionPlanDto getPlanById(UUID planId);

    /**
     * Create a new subscription for a user (returns entity, not DTO)
     */
    UserSubscription createSubscription(User user, CreateSubscriptionRequest request);

    /**
     * Get user's current subscription status
     */
    SubscriptionStatusDto getUserSubscriptionStatus(User user);

    /**
     * Get user's subscription history
     */
    List<UserSubscriptionDto> getUserSubscriptionHistory(User user);

    /**
     * Cancel a user's subscription
     */
    UserSubscriptionDto cancelSubscription(User user, String reason);

    /**
     * Reactivate a cancelled subscription
     */
    UserSubscriptionDto reactivateSubscription(User user);

    /**
     * Update subscription status from payment provider webhook
     */
    void updateSubscriptionFromWebhook(UserSubscription.PaymentProvider paymentProvider, 
                                     String externalSubscriptionId, 
                                     String status,
                                     Instant currentPeriodStart,
                                     Instant currentPeriodEnd);

    /**
     * Check if user has active subscription
     */
    boolean hasActiveSubscription(User user);

    /**
     * Check if user can add more kids based on their subscription
     */
    boolean canAddMoreKids(User user);

    /**
     * Get the number of kids a user can have based on their subscription
     */
    Integer getMaxKidsForUser(User user);

    /**
     * Process expired subscriptions (called by scheduled task)
     */
    void processExpiredSubscriptions();

    /**
     * Process subscriptions due for billing (called by scheduled task)
     */
    void reconcileWithGooglePlay();
}
