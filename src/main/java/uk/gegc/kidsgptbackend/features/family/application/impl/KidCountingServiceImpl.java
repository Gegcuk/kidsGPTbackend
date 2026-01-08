package uk.gegc.kidsgptbackend.features.family.application.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gegc.kidsgptbackend.features.family.domain.model.Parent;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.family.domain.repository.KidRepository;
import uk.gegc.kidsgptbackend.features.family.domain.repository.ParentRepository;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.family.application.KidCountingService;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KidCountingServiceImpl implements KidCountingService {

    private static final int GLOBAL_MAX_KIDS_PER_PARENT = 5;
    private static final int FREE_TIER_MAX_KIDS = GLOBAL_MAX_KIDS_PER_PARENT;
    private final KidRepository kidRepository;
    private final ParentRepository parentRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final Clock clock;
    
    @Override
    public int countKidsForParent(User parentUser) {
        log.debug("Counting kids for parent user: {}", parentUser.getUsername());
        
        // Verify user is a parent
        if (!isParentUser(parentUser)) {
            log.warn("User {} is not a parent, returning 0 kids", parentUser.getUsername());
            return 0;
        }
        
        try {
            // Find parent profile - prefer userId lookup, fallback to email
            Optional<Parent> parentOpt = parentRepository.findByUserId(parentUser.getId());
            if (parentOpt.isEmpty()) {
                log.debug("Parent profile not found by userId for user: {}, trying email lookup", parentUser.getUsername());
                parentOpt = parentRepository.findByEmail(parentUser.getEmail());
            }
            
            if (parentOpt.isEmpty()) {
                log.warn("Parent profile not found for user: {} (tried both userId and email)", parentUser.getUsername());
                return 0;
            }
            
            Parent parent = parentOpt.get();
            int kidCount = kidRepository.countByParentId(parent.getId());
            
            log.debug("Found {} kids for parent: {}", kidCount, parentUser.getUsername());
            return kidCount;
            
        } catch (Exception e) {
            log.error("Error counting kids for parent: {}", parentUser.getUsername(), e);
            return 0;
        }
    }
    
    @Override
    public int countActiveKidsForParent(User parentUser) {
        // For now, we don't have soft delete functionality, so active kids = all kids
        // This method is here for future extensibility when soft delete is implemented
        return countKidsForParent(parentUser);
    }
    
    @Override
    public boolean canAddMoreKids(User parentUser) {
        log.debug("Checking if parent {} can add more kids", parentUser.getUsername());
        
        // Get current kid count
        int currentKidsCount = countKidsForParent(parentUser);
        
        // Get subscription limits
        int maxKidsAllowed = getEffectiveMaxKids(parentUser);
        
        boolean canAdd = currentKidsCount < maxKidsAllowed;
        log.debug("Parent {} has {}/{} kids, can add more: {}", 
                parentUser.getUsername(), currentKidsCount, maxKidsAllowed, canAdd);
        
        return canAdd;
    }

    @Override
    public int getEffectiveMaxKids(User parentUser) {
        return getMaxKidsForUser(parentUser);
    }
    
    private boolean isParentUser(User user) {
        return user.getRoles() != null &&
               user.getRoles().stream()
                .anyMatch(role -> RoleName.ROLE_PARENT.name().equals(role.getRole()));
    }
    
    private int getMaxKidsForUser(User parentUser) {
        try {
            // Find active subscription
            Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                    .findActiveSubscriptionByUser(parentUser);

            int baseLimit = FREE_TIER_MAX_KIDS;

            if (subscriptionOpt.isPresent()) {
                UserSubscription subscription = subscriptionOpt.get();

                // Check if subscription is still valid (not expired)
                if (subscription.getCurrentPeriodEnd() != null &&
                    subscription.getCurrentPeriodEnd().isAfter(Instant.now(clock)) &&
                    subscription.getSubscriptionPlan() != null &&
                    subscription.getSubscriptionPlan().getMaxKids() != null &&
                    subscription.getSubscriptionPlan().getMaxKids() > 0) {

                    baseLimit = subscription.getSubscriptionPlan().getMaxKids();
                    log.debug("User {} has active subscription with plan maxKids={}",
                            parentUser.getUsername(), baseLimit);
                }
            }

            int effectiveLimit = Math.max(1, Math.min(baseLimit, GLOBAL_MAX_KIDS_PER_PARENT));

            if (subscriptionOpt.isEmpty()) {
                log.debug("User {} has no active subscription, free tier allows {} kids (global cap {})",
                        parentUser.getUsername(), FREE_TIER_MAX_KIDS, GLOBAL_MAX_KIDS_PER_PARENT);
            } else {
                log.debug("User {} effective max kids after applying global cap {} is {}",
                        parentUser.getUsername(), GLOBAL_MAX_KIDS_PER_PARENT, effectiveLimit);
            }

            return effectiveLimit;

        } catch (Exception e) {
            log.error("Error getting max kids for user: {}", parentUser.getUsername(), e);
            // Default to free tier limit on error
            return FREE_TIER_MAX_KIDS;
        }
    }
}
