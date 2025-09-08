package uk.gegc.kidsgptbackend.dto.subscription;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionStatusDto(
        UUID userId,
        boolean hasActiveSubscription,
        String subscriptionStatus,
        String planName,
        Instant currentPeriodEnd,
        boolean isTrial,
        Instant trialEndDate,
        Integer maxKids,
        Integer currentKidsCount,
        boolean canAddMoreKids
) {
}
