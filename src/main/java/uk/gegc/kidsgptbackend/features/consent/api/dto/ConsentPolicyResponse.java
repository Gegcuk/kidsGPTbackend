package uk.gegc.kidsgptbackend.features.consent.api.dto;

import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;

import java.time.LocalDate;
import java.util.List;

public record ConsentPolicyResponse(
    List<PolicyInfo> policies
) {
    public record PolicyInfo(
        String policyId,
        ConsentType policyType,
        String version,
        LocalDate effectiveDate,
        String contentHash,
        String policyUrl,
        String locale,
        boolean isActive
    ) {}
} 