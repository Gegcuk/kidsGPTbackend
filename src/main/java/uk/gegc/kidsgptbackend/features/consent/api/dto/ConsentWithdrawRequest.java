package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;

public record ConsentWithdrawRequest(
    @NotBlank(message = "User ID is required")
    String userId,
    
    @NotNull(message = "Consent type is required")
    ConsentType consentType,
    
    @NotBlank(message = "Consent version is required")
    String consentVersion,
    
    String reason,
    
    String ipAddress,
    
    String userAgent
) {} 