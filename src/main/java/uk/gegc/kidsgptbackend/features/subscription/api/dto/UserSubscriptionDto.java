package uk.gegc.kidsgptbackend.features.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Subscription instance for a user")
public record UserSubscriptionDto(
        @Schema(description = "Subscription identifier")
        UUID id,
        @Schema(description = "Owner user ID")
        UUID userId,
        @Schema(description = "Plan ID")
        UUID planId,
        @Schema(description = "Plan display name")
        String planName,
        @Schema(description = "Current subscription status")
        UserSubscription.SubscriptionStatus status,
        @Schema(description = "Subscription start date")
        Instant startDate,
        @Schema(description = "Subscription end date (if cancelled or expired)")
        Instant endDate,
        @Schema(description = "Next billing/renewal date")
        Instant nextBillingDate,
        @Schema(description = "Cancellation timestamp")
        Instant cancelledAt,
        @Schema(description = "Cancellation reason (if provided)")
        String cancellationReason,
        @Schema(description = "Payment provider")
        UserSubscription.PaymentProvider paymentProvider,
        @Schema(description = "Provider-specific subscription ID")
        String externalSubscriptionId,
        @Schema(description = "Trial end date")
        Instant trialEndDate,
        @Schema(description = "Whether subscription is in trial")
        boolean isTrial,
        @Schema(description = "Whether subscription auto-renews")
        boolean autoRenew,
        @Schema(description = "Creation timestamp")
        Instant createdAt,
        @Schema(description = "Update timestamp")
        Instant updatedAt
) {
}
