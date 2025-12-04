package uk.gegc.kidsgptbackend.features.consent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Available consent policies")
public record ConsentPolicyResponse(
    @Schema(description = "Policies returned")
    List<PolicyInfo> policies
) {
    @Schema(description = "Single consent policy metadata")
    public record PolicyInfo(
        @Schema(description = "Policy identifier")
        String policyId,
        @Schema(description = "Type of consent covered")
        ConsentType policyType,
        @Schema(description = "Version label")
        String version,
        @Schema(description = "Effective date")
        LocalDate effectiveDate,
        @Schema(description = "Hash of the policy content")
        String contentHash,
        @Schema(description = "URL to the policy document")
        String policyUrl,
        @Schema(description = "Locale of the policy")
        String locale,
        @Schema(description = "Active flag")
        boolean isActive
    ) {}
}
