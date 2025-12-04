package uk.gegc.kidsgptbackend.features.subscription.application.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionUsage;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionUsageRepository;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.family.application.KidCountingService;
import uk.gegc.kidsgptbackend.features.subscription.application.SubscriptionAccessService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SubscriptionAccessServiceImpl implements SubscriptionAccessService {

    private static final String DAILY_FREE_AI_MESSAGES_FEATURE = "daily_free_ai_messages";
    private static final int DAILY_FREE_MESSAGES_LIMIT = 5;

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionUsageRepository subscriptionUsageRepository;
    private final ObjectMapper objectMapper;
    private final KidCountingService kidCountingService;

    @Override
    public boolean hasFeatureAccess(User user, String feature) {
        UserSubscription activeSubscription = getActiveSubscription(user);
        
        if (activeSubscription != null) {
            // Paid subscription - check plan limits
            return getSubscriptionLimit(activeSubscription, feature) == -1 || 
                   getRemainingUsage(user, feature) > 0;
        }

        // Free tier - only chat_limit is allowed and is governed by the per-day counter
        if ("chat_limit".equals(feature)) {
            return getRemainingDailyFreeMessagesForSubject(user, user.getId()) > 0;
        }

        return false;
    }

    @Override
    public boolean canPerformAction(User user, String action) {
        return switch (action) {
            case "chat" -> hasFeatureAccess(user, "chat_limit");
            case "image_generation" -> hasFeatureAccess(user, "image_generation");
            case "story_continuation" -> hasFeatureAccess(user, "story_continuation");
            case "add_kid" -> kidCountingService.canAddMoreKids(user);
            default -> false;
        };
    }

    @Override
    public int getRemainingUsage(User user, String feature) {
        UserSubscription activeSubscription = getActiveSubscription(user);

        // For chat on the free tier, reuse the daily counter
        if (activeSubscription == null && "chat_limit".equals(feature)) {
            return getRemainingDailyFreeMessagesForSubject(user, user.getId());
        }
        
        final int limit;
        final String periodKey;
        final Instant periodStart;
        final Instant periodEnd;
        
        if (activeSubscription == null) {
            // Free tier - 3-day window from user signup
            limit = getFreeTierLimit(feature);
            Instant userCreatedAt = user.getCreatedAt(); // Already Instant now
            periodKey = "FREE_" + user.getId() + "_" + userCreatedAt.getEpochSecond();
            periodStart = userCreatedAt;
            periodEnd = userCreatedAt.plus(3, ChronoUnit.DAYS);
        } else {
            // Paid subscription - use provider period
            limit = getSubscriptionLimit(activeSubscription, feature);
            if (limit == -1) {
                return Integer.MAX_VALUE; // Unlimited
            }
            
            String tempPeriodKey = getProviderPeriodKey(activeSubscription);
            Instant tempPeriodStart = activeSubscription.getCurrentPeriodStart();
            Instant tempPeriodEnd = activeSubscription.getCurrentPeriodEnd();
            
            if (tempPeriodStart == null || tempPeriodEnd == null) {
                // Fallback to monthly if provider data not available
                periodKey = getCurrentMonthKey();
                YearMonth currentMonth = YearMonth.now();
                periodStart = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
                periodEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
            } else {
                periodKey = tempPeriodKey;
                periodStart = tempPeriodStart;
                periodEnd = tempPeriodEnd;
            }
        }

        // Get or create usage record
        try {
            SubscriptionUsage usage = subscriptionUsageRepository
                    .findByUserAndFeatureAndPeriodKey(user, feature, periodKey)
                    .orElseGet(() -> createUsageRecord(user, feature, periodKey, limit, periodStart, periodEnd));

            return usage.getRemainingUsage();
        } catch (Exception e) {
            log.error("Error getting usage for user {} feature {} period {}", user.getId(), feature, periodKey, e);
            return 0; // Safe fallback
        }
    }

    @Override
    public boolean hasReachedUsageLimit(User user, String feature) {
        return getRemainingUsage(user, feature) <= 0;
    }

    @Override
    @Transactional
    public void addUsageCredits(User user, String feature, int additionalCredits) {
        if (additionalCredits <= 0) {
            return;
        }

        UserSubscription activeSubscription = getActiveSubscription(user);
        if (activeSubscription == null) {
            throw new IllegalStateException("Active subscription required to add usage credits");
        }

        final String periodKey;
        final Instant periodStart;
        final Instant periodEnd;
        final int baseLimit = getSubscriptionLimit(activeSubscription, feature);

        String tempPeriodKey = getProviderPeriodKey(activeSubscription);
        Instant tempPeriodStart = activeSubscription.getCurrentPeriodStart();
        Instant tempPeriodEnd = activeSubscription.getCurrentPeriodEnd();

        if (tempPeriodStart == null || tempPeriodEnd == null) {
            // Fallback to monthly
            periodKey = getCurrentMonthKey();
            YearMonth currentMonth = YearMonth.now();
            periodStart = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            periodEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
        } else {
            periodKey = tempPeriodKey;
            periodStart = tempPeriodStart;
            periodEnd = tempPeriodEnd;
        }

        SubscriptionUsage usage = subscriptionUsageRepository
                .findByUserAndFeatureAndPeriodKey(user, feature, periodKey)
                .orElseGet(() -> createUsageRecord(user, feature, periodKey, baseLimit, periodStart, periodEnd));

        if (usage.getLimitCount() == null || usage.getLimitCount() == -1) {
            // Unlimited already; no need to add credits
            return;
        }

        usage.setLimitCount(usage.getLimitCount() + additionalCredits);
        subscriptionUsageRepository.save(usage);

        log.info("Added {} credits for user {} feature {} in period {}", additionalCredits, user.getId(), feature, periodKey);
    }

    @Override
    @Transactional
    public void incrementUsage(User user, String feature) {
        UserSubscription activeSubscription = getActiveSubscription(user);

        // For chat on the free tier, increment the daily counter and return
        if (activeSubscription == null && "chat_limit".equals(feature)) {
            incrementDailyFreeMessagesForSubject(user, user.getId());
            return;
        }

        final String periodKey;
        final Instant periodStart;
        final Instant periodEnd;
        final int limit;
        
        if (activeSubscription == null) {
            // Free tier - 3-day window from user signup
            limit = getFreeTierLimit(feature);
            Instant userCreatedAt = user.getCreatedAt(); // Already Instant now
            periodKey = "FREE_" + user.getId() + "_" + userCreatedAt.getEpochSecond();
            periodStart = userCreatedAt;
            periodEnd = userCreatedAt.plus(3, ChronoUnit.DAYS);
        } else {
            // Paid subscription
            limit = getSubscriptionLimit(activeSubscription, feature);
            String tempPeriodKey = getProviderPeriodKey(activeSubscription);
            Instant tempPeriodStart = activeSubscription.getCurrentPeriodStart();
            Instant tempPeriodEnd = activeSubscription.getCurrentPeriodEnd();
            
            if (tempPeriodStart == null || tempPeriodEnd == null) {
                // Fallback to monthly
                periodKey = getCurrentMonthKey();
                YearMonth currentMonth = YearMonth.now();
                periodStart = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
                periodEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
            } else {
                periodKey = tempPeriodKey;
                periodStart = tempPeriodStart;
                periodEnd = tempPeriodEnd;
            }
        }

        // Try atomic increment first
        int updated = subscriptionUsageRepository.incrementUsage(user, feature, periodKey, Instant.now());
        
        if (updated == 0) {
            // Usage record doesn't exist, create it
            SubscriptionUsage usage = createUsageRecord(user, feature, periodKey, limit, periodStart, periodEnd);
            usage.setUsedCount(1);
            subscriptionUsageRepository.save(usage);
        }
        
        log.debug("Incremented usage for user {} feature {} in period {}", 
                user.getId(), feature, periodKey);
    }

    @Override
    @Transactional
    public void resetUsageCounters(User user) {
        // This method is less relevant with persistent storage
        // Usage resets happen automatically when periods expire
        // But we can clean up old usage records
        
        // Delete expired usage records for this user
        subscriptionUsageRepository.findExpiredUsagePeriods(Instant.now())
                .stream()
                .filter(usage -> usage.getUser().equals(user))
                .forEach(subscriptionUsageRepository::delete);
        
        log.info("Cleaned up expired usage records for user {}", user.getId());
    }

    /**
     * Daily free messages (per subject, e.g. per child) backed by SubscriptionUsage.
     * This does not consider paid subscriptions; callers decide when to use it.
     */
    @Transactional
    public int getRemainingDailyFreeMessagesForSubject(User user, UUID subjectId) {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);

        String periodKey = buildDailyFreeMessagesPeriodKey(subjectId, todayUtc);
        Instant periodStart = todayUtc.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant periodEnd = todayUtc.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);

        try {
            SubscriptionUsage usage = subscriptionUsageRepository
                    .findByUserAndFeatureAndPeriodKey(user, DAILY_FREE_AI_MESSAGES_FEATURE, periodKey)
                    .orElseGet(() ->
                            createUsageRecord(user,
                                    DAILY_FREE_AI_MESSAGES_FEATURE,
                                    periodKey,
                                    DAILY_FREE_MESSAGES_LIMIT,
                                    periodStart,
                                    periodEnd));

            return usage.getRemainingUsage();
        } catch (Exception e) {
            log.error("Error getting daily free messages usage for user {} subject {} period {}",
                    user.getId(), subjectId, periodKey, e);
            return 0;
        }
    }

    @Transactional
    public void incrementDailyFreeMessagesForSubject(User user, UUID subjectId) {
        Instant now = Instant.now();
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);

        String periodKey = buildDailyFreeMessagesPeriodKey(subjectId, todayUtc);
        Instant periodStart = todayUtc.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant periodEnd = todayUtc.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);

        int updated = subscriptionUsageRepository.incrementUsage(
                user,
                DAILY_FREE_AI_MESSAGES_FEATURE,
                periodKey,
                now
        );

        if (updated == 0) {
            SubscriptionUsage usage = createUsageRecord(
                    user,
                    DAILY_FREE_AI_MESSAGES_FEATURE,
                    periodKey,
                    DAILY_FREE_MESSAGES_LIMIT,
                    periodStart,
                    periodEnd
            );
            usage.setUsedCount(1);
            subscriptionUsageRepository.save(usage);
        }

        log.debug("Incremented daily free messages usage for user {} subject {} in period {}",
                user.getId(), subjectId, periodKey);
    }

    // Helper methods
    private UserSubscription getActiveSubscription(User user) {
        Optional<UserSubscription> subscription = userSubscriptionRepository.findActiveSubscriptionByUser(user);
        return subscription.orElse(null);
    }


    private int getFreeTierLimit(String feature) {
        return switch (feature) {
            case "chat_limit" -> 5; // 5 free messages per day, tracked separately
            default -> 0;
        };
    }

    private String getCurrentMonthKey() {
        return YearMonth.now().toString(); // e.g., "2025-01"
    }
    
    private String getProviderPeriodKey(UserSubscription subscription) {
        // Use provider period if available, otherwise fall back to monthly
        if (subscription.getCurrentPeriodStart() != null) {
            return subscription.getPaymentProvider() + "_" + subscription.getExternalSubscriptionId() + "_" + 
                   subscription.getCurrentPeriodStart().getEpochSecond();
        }
        return getCurrentMonthKey();
    }
    
    private int getSubscriptionLimit(UserSubscription subscription, String feature) {
        try {
            if (subscription.getSubscriptionPlan() == null) {
                log.warn("Subscription {} has null plan", subscription.getId());
                return defaultLimitForFeature(feature);
            }
            
            String featuresJson = subscription.getSubscriptionPlan().getFeatures();
            if (featuresJson == null || featuresJson.trim().isEmpty()) {
                log.warn("Subscription {} has null or empty features JSON", subscription.getId());
                return defaultLimitForFeature(feature);
            }
            
            JsonNode features = objectMapper.readTree(featuresJson);
            JsonNode featureNode = features.get(feature);
            
            if (featureNode == null || !featureNode.isNumber()) {
                return defaultLimitForFeature(feature);
            }
            
            return featureNode.asInt();
        } catch (JsonProcessingException e) {
            log.error("Error parsing features JSON for subscription {}", subscription.getId(), e);
            return defaultLimitForFeature(feature);
        }
    }

    private int defaultLimitForFeature(String feature) {
        if ("image_generation".equals(feature)) {
            return 2; // Default monthly allowance for subscribed users
        }
        return 0;
    }
    
    private SubscriptionUsage createUsageRecord(User user, String feature, String periodKey, 
                                               int limit, Instant periodStart, Instant periodEnd) {
        SubscriptionUsage usage = new SubscriptionUsage();
        usage.setUser(user);
        usage.setFeature(feature);
        usage.setPeriodKey(periodKey);
        usage.setLimitCount(limit);
        usage.setUsedCount(0);
        usage.setPeriodStart(periodStart);
        usage.setPeriodEnd(periodEnd);
        
        return subscriptionUsageRepository.save(usage);
    }

    private String buildDailyFreeMessagesPeriodKey(UUID subjectId, LocalDate dateUtc) {
        return "DAILY_FREE_" + subjectId + "_" + dateUtc;
    }

}
