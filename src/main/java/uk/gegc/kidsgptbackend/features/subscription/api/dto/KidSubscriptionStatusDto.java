package uk.gegc.kidsgptbackend.features.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Subscription status for an individual kid profile")
public record KidSubscriptionStatusDto(
        @Schema(description = "Kid profile identifier")
        UUID kidId,
        @Schema(description = "Kid user identifier")
        UUID kidUserId,
        @Schema(description = "Kid nickname")
        String nickname,
        @Schema(description = "Whether the kid has an active subscription")
        boolean hasActiveSubscription,
        @Schema(description = "Active plan name if subscribed")
        String planName,
        @Schema(description = "Current period end for the kid subscription")
        Instant currentPeriodEnd,
        @Schema(description = "Remaining free daily messages for this kid")
        Integer dailyFreeMessagesRemaining
) {
}
