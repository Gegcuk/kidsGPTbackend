package uk.gegc.kidsgptbackend.features.subscription.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSubscriptionRequest(
        @NotNull(message = "Plan ID is required")
        UUID planId,
        
        @NotBlank(message = "Google product ID is required")
        String googleProductId,
        
        @NotBlank(message = "Purchase token is required")
        String purchaseToken
) {}