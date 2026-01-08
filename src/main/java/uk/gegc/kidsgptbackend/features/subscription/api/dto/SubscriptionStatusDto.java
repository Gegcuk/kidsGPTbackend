package uk.gegc.kidsgptbackend.features.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Snapshot of the user's subscription status and kid limits")
public record SubscriptionStatusDto(
        @Schema(description = "User identifier")
        UUID userId,
        @Schema(description = "True when a subscription is active")
        boolean hasActiveSubscription,
        @Schema(description = "Human-readable status or enum name")
        String subscriptionStatus,
        @Schema(description = "Active plan name")
        String planName,
        @Schema(description = "Current period end for billing/limits")
        Instant currentPeriodEnd,
        @Schema(description = "Whether the subscription is in trial")
        boolean isTrial,
        @Schema(description = "Trial end date if applicable")
        Instant trialEndDate,
        @Schema(description = "Maximum kids allowed under current plan")
        Integer maxKids,
        @Schema(description = "Current number of kids linked")
        Integer currentKidsCount,
        @Schema(description = "Whether more kids can be added right now")
        boolean canAddMoreKids
) {
}
