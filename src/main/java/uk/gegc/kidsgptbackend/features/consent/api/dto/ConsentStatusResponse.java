package uk.gegc.kidsgptbackend.features.consent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Current consent status for a user")
public record ConsentStatusResponse(
    @Schema(description = "Latest consent entry per type")
    List<ConsentStatusByType> latestByType,
    @Schema(description = "Whether the user must re-consent")
    boolean reconsentNeeded,
    @Schema(description = "Most recent consent record ID")
    UUID consentId
) {
    @Schema(description = "Consent status for a specific type")
    public record ConsentStatusByType(
        @Schema(description = "Consent type")
        ConsentType type,
        @Schema(description = "Version provided")
        String version,
        @Schema(description = "Status of the consent")
        ConsentStatus status,
        @Schema(description = "Timestamp of the event")
        LocalDateTime timestamp,
        @Schema(description = "Policy URL")
        String policyUrl
    ) {}
}
