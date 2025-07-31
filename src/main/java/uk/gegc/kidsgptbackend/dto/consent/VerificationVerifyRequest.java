package uk.gegc.kidsgptbackend.dto.consent;

import jakarta.validation.constraints.NotBlank;

public record VerificationVerifyRequest(
    @NotBlank(message = "Verification ID is required")
    String verificationId,
    
    @NotBlank(message = "Verification code is required")
    String verificationCode,
    
    String ipAddress,
    
    String userAgent
) {} 