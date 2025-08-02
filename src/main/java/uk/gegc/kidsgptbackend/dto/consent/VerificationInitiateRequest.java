package uk.gegc.kidsgptbackend.dto.consent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uk.gegc.kidsgptbackend.model.consent.VerificationMethod;

public record VerificationInitiateRequest(
    @NotBlank(message = "Parent ID is required")
    String parentId,
    
    @NotNull(message = "Verification method is required")
    VerificationMethod verificationMethod,
    
    @NotBlank(message = "Contact information is required")
    String contactInfo,
    
    String ipAddress,
    
    String userAgent
) {} 