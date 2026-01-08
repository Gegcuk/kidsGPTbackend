package uk.gegc.kidsgptbackend.features.subscription.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Create or verify a Google Play-backed subscription")
public record CreateSubscriptionRequest(
        @Schema(description = "Internal subscription plan ID")
        @NotNull(message = "Plan ID is required")
        UUID planId,
        
        @Schema(description = "Google Play product ID")
        @NotBlank(message = "Google product ID is required")
        String googleProductId,
        
        @Schema(description = "Google Play purchase token")
        @NotBlank(message = "Purchase token is required")
        String purchaseToken
) {}
