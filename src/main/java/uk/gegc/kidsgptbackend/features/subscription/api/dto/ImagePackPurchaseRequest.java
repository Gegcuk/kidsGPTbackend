package uk.gegc.kidsgptbackend.features.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request to purchase a one-time image credit pack for a kid")
public record ImagePackPurchaseRequest(
        @Schema(description = "Google Play product ID for the image pack")
        @NotBlank
        String productId,
        @Schema(description = "Google Play purchase token")
        @NotBlank
        String purchaseToken,
        @Schema(description = "Kid user ID to apply credits to")
        @NotNull
        UUID kidUserId
) {
}
