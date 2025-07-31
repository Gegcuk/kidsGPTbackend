package uk.gegc.kidsgptbackend.dto.consent;

import uk.gegc.kidsgptbackend.model.consent.ConsentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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