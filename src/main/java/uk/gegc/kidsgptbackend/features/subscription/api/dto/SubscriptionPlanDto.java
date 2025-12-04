package uk.gegc.kidsgptbackend.features.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Subscription plan definition")
public record SubscriptionPlanDto(
        @Schema(description = "Plan identifier")
        UUID id,
        @Schema(description = "Plan display name")
        String name,
        @Schema(description = "Marketing description")
        String description,
        @Schema(description = "Price per billing cycle")
        BigDecimal price,
        @Schema(description = "Currency code (ISO 4217)")
        String currency,
        @Schema(description = "Billing cadence")
        SubscriptionPlan.BillingCycle billingCycle,
        @Schema(description = "Whether the plan is currently active")
        boolean isActive,
        @Schema(description = "Maximum kids allowed for this plan")
        Integer maxKids,
        @Schema(description = "Feature payload JSON")
        String features,
        @Schema(description = "Google Play product identifier")
        String googlePlayProductId,
        @Schema(description = "Creation timestamp")
        Instant createdAt,
        @Schema(description = "Update timestamp")
        Instant updatedAt
) {
}
