package uk.gegc.kidsgptbackend.features.family.application;

import uk.gegc.kidsgptbackend.features.user.domain.model.User;

/**
 * Service for counting kids associated with a parent user.
 * This is used for subscription limit enforcement.
 */
public interface KidCountingService {
    
    /**
     * Count the number of kids associated with a parent user
     * @param parentUser the parent user
     * @return the number of kids
     */
    int countKidsForParent(User parentUser);
    
    /**
     * Count the number of active kids (non-deleted) associated with a parent user
     * @param parentUser the parent user
     * @return the number of active kids
     */
    int countActiveKidsForParent(User parentUser);
    
    /**
     * Check if a parent can add more kids based on their subscription
     * @param parentUser the parent user
     * @return true if they can add more kids, false otherwise
     */
    boolean canAddMoreKids(User parentUser);

    /**
     * Effective kid cap for the parent (plan max_kids constrained by global cap).
     */
    int getEffectiveMaxKids(User parentUser);
}
