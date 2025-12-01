package uk.gegc.kidsgptbackend.features.subscription.api.dto;

import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;

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
