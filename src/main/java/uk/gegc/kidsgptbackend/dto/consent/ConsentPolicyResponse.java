package uk.gegc.kidsgptbackend.dto.consent;

import uk.gegc.kidsgptbackend.model.consent.ConsentType;

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