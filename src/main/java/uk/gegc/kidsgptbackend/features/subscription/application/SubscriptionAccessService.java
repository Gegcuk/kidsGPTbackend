package uk.gegc.kidsgptbackend.features.subscription.application;

import uk.gegc.kidsgptbackend.features.user.domain.model.User;

import java.util.UUID;

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
     * Add additional usage credits for a feature (e.g., purchased image packs).
     */
    void addUsageCredits(User user, String feature, int additionalCredits);

    /**
     * Reset usage counters (typically called monthly)
     */
    void resetUsageCounters(User user);

    /**
     * Get remaining daily free AI messages for a specific subject (e.g. child profile).
     * This method uses a fixed daily window in UTC and a hard limit of 5 messages per day.
     */
    int getRemainingDailyFreeMessagesForSubject(User user, UUID subjectId);

    /**
     * Increment daily free AI message usage for a specific subject (e.g. child profile).
     * Call this once for each successful AI response that should consume a free message.
     */
    void incrementDailyFreeMessagesForSubject(User user, UUID subjectId);
}
