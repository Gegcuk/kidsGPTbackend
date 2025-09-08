package uk.gegc.kidsgptbackend.dto.subscription;

import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionPlanDto(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        SubscriptionPlan.BillingCycle billingCycle,
        boolean isActive,
        Integer maxKids,
        String features,
        String googlePlayProductId,
        Instant createdAt,
        Instant updatedAt
) {
}
