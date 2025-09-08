package uk.gegc.kidsgptbackend.service.subscription.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.subscription.*;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.subscription.SubscriptionPlanRepository;
import uk.gegc.kidsgptbackend.repository.subscription.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;
import uk.gegc.kidsgptbackend.service.subscription.SubscriptionService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserRepository userRepository;
    private final GooglePlayClient googlePlayClient;

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> getAvailablePlans() {
        return subscriptionPlanRepository.findByIsActiveTrueOrderByPriceAsc()
                .stream()
                .map(this::mapToPlanDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanDto getPlanById(UUID planId) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found"));
        return mapToPlanDto(plan);
    }

    @Override
    public UserSubscription createSubscription(User user, CreateSubscriptionRequest request) {
        // Check if user already has an active subscription (with SELECT FOR UPDATE to prevent race conditions)
        List<UserSubscription> activeSubscriptions = userSubscriptionRepository.findActiveSubscriptionsWithLock(user);
        if (!activeSubscriptions.isEmpty()) {
            throw new IllegalStateException("User already has an active subscription");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.planId())
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found"));
        
        // Verify product ID matches plan
        if (!java.util.Objects.equals(plan.getGooglePlayProductId(), request.googleProductId())) {
            throw new IllegalArgumentException("Product/plan mismatch");
        }

        // Verify purchase with Google Play
        GooglePlaySubscriptionPurchase googlePurchase = googlePlayClient.getSubscriptionPurchase(
                request.googleProductId(), request.purchaseToken());
        
        if (!googlePurchase.isEntitlementActive()) {
            throw new IllegalStateException("Purchase not active");
        }

        // Acknowledge the purchase with Google Play before persisting
        boolean acknowledged = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                googlePlayClient.acknowledgeSubscription(request.googleProductId(), request.purchaseToken(), null);
                acknowledged = true;
                break;
            } catch (Exception e) {
                log.warn("Failed to acknowledge subscription (attempt {}/3): {}", attempt, e.getMessage());
                if (attempt == 3) {
                    log.error("Failed to acknowledge subscription after 3 attempts - continuing but marking for retry", e);
                } else {
                    try {
                        Thread.sleep(1000 * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Check if subscription already exists for this purchase token
        UserSubscription subscription = userSubscriptionRepository
                .findByPaymentProviderAndExternalSubscriptionId(
                        UserSubscription.PaymentProvider.GOOGLE_PLAY, request.purchaseToken())
                .orElse(new UserSubscription());

        subscription.setUser(user);
        subscription.setSubscriptionPlan(plan);
        subscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        subscription.setExternalSubscriptionId(request.purchaseToken()); // Store purchaseToken, not subscriptionId
        subscription.setStatus(mapGooglePlayStatus(googlePurchase));
        subscription.setCurrentPeriodStart(Instant.ofEpochMilli(googlePurchase.getStartTimeMillis()));
        subscription.setCurrentPeriodEnd(Instant.ofEpochMilli(googlePurchase.getExpiryTimeMillis()));
        subscription.setAutoRenew(Boolean.TRUE.equals(googlePurchase.getAutoRenewing()));
        subscription.setStartDate(Instant.now());
        subscription.setProviderStatusRaw(googlePurchase.getPurchaseState());

        UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);
        log.info("Created/updated subscription {} for user {} from Google Play purchase", 
                savedSubscription.getId(), user.getId());

        return savedSubscription;
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionStatusDto getUserSubscriptionStatus(User user) {
        UserSubscription activeSubscription = userSubscriptionRepository.findActiveSubscriptionByUser(user)
                .orElse(null);

        if (activeSubscription == null) {
            return new SubscriptionStatusDto(
                    user.getId(),
                    false,
                    null,
                    null,
                    null,
                    false,
                    null,
                    1, // Default free tier
                    0, // Will be calculated based on actual kids
                    true
            );
        }

        // Count current kids (you'll need to implement this based on your kid entity)
        int currentKidsCount = 0; // TODO: Implement kid counting logic

        return new SubscriptionStatusDto(
                user.getId(),
                true,
                activeSubscription.getStatus().name(),
                activeSubscription.getSubscriptionPlan().getName(),
                activeSubscription.getCurrentPeriodEnd(),
                activeSubscription.isTrial(),
                activeSubscription.getTrialEndDate(),
                activeSubscription.getSubscriptionPlan().getMaxKids(),
                currentKidsCount,
                currentKidsCount < activeSubscription.getSubscriptionPlan().getMaxKids()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSubscriptionDto> getUserSubscriptionHistory(User user) {
        return userSubscriptionRepository.findByUserAndStatusInOrderByCreatedAtDesc(
                user, List.of(UserSubscription.SubscriptionStatus.values()))
                .stream()
                .map(this::mapToUserSubscriptionDto)
                .collect(Collectors.toList());
    }

    @Override
    public UserSubscriptionDto cancelSubscription(User user, String reason) {
        UserSubscription subscription = userSubscriptionRepository.findActiveSubscriptionByUser(user)
                .orElseThrow(() -> new IllegalStateException("No active subscription found"));

        // For most providers, we should cancel at period end, not immediately
        subscription.setCancelAtPeriodEnd(true);
        subscription.setCancellationReason(reason);
        subscription.setAutoRenew(false);
        
        // Only set to cancelled immediately if no current period end
        if (subscription.getCurrentPeriodEnd() == null || 
            subscription.getCurrentPeriodEnd().isBefore(Instant.now())) {
            subscription.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
            subscription.setCancelledAt(Instant.now());
        }

        UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);
        log.info("Marked subscription {} for cancellation - reason: {}", savedSubscription.getId(), reason);

        return mapToUserSubscriptionDto(savedSubscription);
    }

    @Override
    public UserSubscriptionDto reactivateSubscription(User user) {
        UserSubscription subscription = userSubscriptionRepository.findByUserAndStatusIn(
                user, List.of(UserSubscription.SubscriptionStatus.CANCELLED))
                .orElseThrow(() -> new IllegalStateException("No cancelled subscription found"));

        // Clear cancellation flags - actual reactivation will be confirmed by webhook
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancellationReason(null);
        subscription.setAutoRenew(true);
        
        // Don't change status to ACTIVE immediately - wait for provider confirmation
        // The payment provider will send a webhook when reactivation is confirmed

        UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);
        log.info("Requested reactivation for subscription {} - waiting for provider confirmation", 
                savedSubscription.getId());

        return mapToUserSubscriptionDto(savedSubscription);
    }

    @Override
    public void updateSubscriptionFromWebhook(UserSubscription.PaymentProvider paymentProvider, 
                                            String externalSubscriptionId, 
                                            String status, 
                                            Instant currentPeriodStart,
                                            Instant currentPeriodEnd) {
        UserSubscription subscription = userSubscriptionRepository
                .findByPaymentProviderAndExternalSubscriptionId(paymentProvider, externalSubscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        // Update subscription status based on payment provider status
        UserSubscription.SubscriptionStatus newStatus = mapPaymentProviderStatus(status);
        subscription.setStatus(newStatus);
        subscription.setProviderStatusRaw(status);

        // Update period dates from provider (source of truth)
        if (currentPeriodStart != null) {
            subscription.setCurrentPeriodStart(currentPeriodStart);
        }
        if (currentPeriodEnd != null) {
            subscription.setCurrentPeriodEnd(currentPeriodEnd);
            subscription.setNextBillingDate(currentPeriodEnd);
        }

        userSubscriptionRepository.save(subscription);
        log.info("Updated subscription {} status to {} from {} webhook", 
                subscription.getId(), newStatus, paymentProvider);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(User user) {
        return userSubscriptionRepository.findActiveSubscriptionByUser(user).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAddMoreKids(User user) {
        UserSubscription activeSubscription = userSubscriptionRepository.findActiveSubscriptionByUser(user)
                .orElse(null);

        if (activeSubscription == null) {
            return true; // Free tier allows 1 kid
        }

        // TODO: Implement actual kid counting logic
        int currentKidsCount = 0;
        return currentKidsCount < activeSubscription.getSubscriptionPlan().getMaxKids();
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getMaxKidsForUser(User user) {
        UserSubscription activeSubscription = userSubscriptionRepository.findActiveSubscriptionByUser(user)
                .orElse(null);

        if (activeSubscription == null) {
            return 1; // Free tier
        }

        return activeSubscription.getSubscriptionPlan().getMaxKids();
    }

    @Override
    public void processExpiredSubscriptions() {
        // This is a safety net - providers should send webhooks, but we reconcile just in case
        List<UserSubscription> potentiallyExpired = userSubscriptionRepository
                .findExpiredActiveSubscriptions(Instant.now());

        for (UserSubscription subscription : potentiallyExpired) {
            // Only mark as expired if we haven't heard from provider recently
            // In production, you'd query the provider API to confirm status
            log.warn("Subscription {} appears expired - should verify with provider {}", 
                    subscription.getId(), subscription.getPaymentProvider());
            
            // For now, mark as expired (in production, query provider first)
            subscription.setStatus(UserSubscription.SubscriptionStatus.EXPIRED);
            userSubscriptionRepository.save(subscription);
        }
    }

    @Override
    public void reconcileWithGooglePlay() {
        // This is a safety net to detect webhook lag - providers handle billing
        List<UserSubscription> billingDueSubscriptions = userSubscriptionRepository
                .findSubscriptionsDueForBilling(Instant.now());

        for (UserSubscription subscription : billingDueSubscriptions) {
            // Alert if billing is overdue - don't attempt billing ourselves
            log.warn("Subscription {} billing appears overdue - webhook may be lagged. Provider: {}", 
                    subscription.getId(), subscription.getPaymentProvider());
            
            // In production: send alert to monitoring system, don't attempt to charge
        }
    }

    // Helper methods - DEPRECATED: Don't calculate dates locally, use provider data
    @Deprecated
    private Instant calculateEndDate(SubscriptionPlan.BillingCycle billingCycle) {
        // This should not be used - providers are source of truth for billing dates
        return switch (billingCycle) {
            case MONTHLY -> Instant.now().plusSeconds(30L * 24 * 60 * 60); // 30 days
            case YEARLY -> Instant.now().plusSeconds(365L * 24 * 60 * 60); // 365 days
        };
    }
    
    UserSubscription.SubscriptionStatus mapGooglePlayStatus(GooglePlaySubscriptionPurchase purchase) {
        if (purchase.isPurchased() && !purchase.isExpired()) {
            return UserSubscription.SubscriptionStatus.ACTIVE;
        } else if (purchase.isCanceled()) {
            return UserSubscription.SubscriptionStatus.CANCELLED;
        } else if (purchase.isExpired()) {
            return UserSubscription.SubscriptionStatus.EXPIRED;
        } else {
            return UserSubscription.SubscriptionStatus.INCOMPLETE;
        }
    }

    UserSubscription.SubscriptionStatus mapPaymentProviderStatus(String providerStatus) {
        return switch (providerStatus.toLowerCase()) {
            case "active", "paid" -> UserSubscription.SubscriptionStatus.ACTIVE;
            case "cancelled", "canceled" -> UserSubscription.SubscriptionStatus.CANCELLED;
            case "past_due", "pastdue" -> UserSubscription.SubscriptionStatus.PAST_DUE;
            case "unpaid" -> UserSubscription.SubscriptionStatus.UNPAID;
            case "incomplete" -> UserSubscription.SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> UserSubscription.SubscriptionStatus.INCOMPLETE_EXPIRED;
            default -> UserSubscription.SubscriptionStatus.ACTIVE;
        };
    }

    private SubscriptionPlanDto mapToPlanDto(SubscriptionPlan plan) {
        return new SubscriptionPlanDto(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.getBillingCycle(),
                plan.isActive(),
                plan.getMaxKids(),
                plan.getFeatures(),
                plan.getGooglePlayProductId(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private UserSubscriptionDto mapToUserSubscriptionDto(UserSubscription subscription) {
        return new UserSubscriptionDto(
                subscription.getId(),
                subscription.getUser().getId(),
                subscription.getSubscriptionPlan().getId(),
                subscription.getSubscriptionPlan().getName(),
                subscription.getStatus(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                subscription.getNextBillingDate(),
                subscription.getCancelledAt(),
                subscription.getCancellationReason(),
                subscription.getPaymentProvider(),
                subscription.getExternalSubscriptionId(),
                subscription.getTrialEndDate(),
                subscription.isTrial(),
                subscription.isAutoRenew(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
