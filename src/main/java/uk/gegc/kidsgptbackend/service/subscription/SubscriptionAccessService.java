package uk.gegc.kidsgptbackend.service.subscription;

import uk.gegc.kidsgptbackend.features.user.domain.model.User;

public interface SubscriptionAccessService {

    /**
     * Check if user has access to a specific feature
     */
    boolean hasFeatureAccess(User user, String feature);

    /**
     * Check if user can perform an action based on their subscription
     */
    boolean canPerformAction(User user, String action);

    /**
     * Get the remaining usage for a specific feature
     */
    int getRemainingUsage(User user, String feature);

    /**
     * Check if user has reached their usage limit for a feature
     */
    boolean hasReachedUsageLimit(User user, String feature);

    /**
     * Increment usage counter for a feature
     */
    void incrementUsage(User user, String feature);

    /**
     * Reset usage counters (typically called monthly)
     */
    void resetUsageCounters(User user);
}
