package uk.gegc.kidsgptbackend.service.subscription.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.subscription.CreateSubscriptionRequest;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.repository.subscription.SubscriptionPlanRepository;
import uk.gegc.kidsgptbackend.repository.subscription.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionSaver {
    
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    
    @Transactional
    public UserSubscription saveFromGoogle(User user, CreateSubscriptionRequest request, GooglePlaySubscriptionPurchase googlePurchase) {
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
        
        // Guard against nullable timestamps
        Long startMs = googlePurchase.getStartTimeMillis();
        Long endMs = googlePurchase.getExpiryTimeMillis();
        
        if (startMs != null) {
            subscription.setCurrentPeriodStart(Instant.ofEpochMilli(startMs));
        }
        if (endMs != null) {
            Instant end = Instant.ofEpochMilli(endMs);
            subscription.setCurrentPeriodEnd(end);
            subscription.setNextBillingDate(end);
        }
        
        subscription.setAutoRenew(Boolean.TRUE.equals(googlePurchase.getAutoRenewing()));
        subscription.setStartDate(Instant.now());
        subscription.setProviderStatusRaw(googlePurchase.getPurchaseState());

        UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);
        log.info("Created/updated subscription {} for user {} from Google Play purchase", 
                savedSubscription.getId(), user.getId());

        return savedSubscription;
    }
    
    private UserSubscription.SubscriptionStatus mapGooglePlayStatus(GooglePlaySubscriptionPurchase purchase) {
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
}
