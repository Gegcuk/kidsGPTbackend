package uk.gegc.kidsgptbackend.features.subscription.api.dto;

import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;

import java.time.Instant;
import java.util.UUID;

public record UserSubscriptionDto(
        UUID id,
        UUID userId,
        UUID planId,
        String planName,
        UserSubscription.SubscriptionStatus status,
        Instant startDate,
        Instant endDate,
        Instant nextBillingDate,
        Instant cancelledAt,
        String cancellationReason,
        UserSubscription.PaymentProvider paymentProvider,
        String externalSubscriptionId,
        Instant trialEndDate,
        boolean isTrial,
        boolean autoRenew,
        Instant createdAt,
        Instant updatedAt
) {
}
