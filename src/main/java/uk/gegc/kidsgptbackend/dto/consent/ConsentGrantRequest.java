package uk.gegc.kidsgptbackend.dto.consent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uk.gegc.kidsgptbackend.model.consent.ConsentSource;
import uk.gegc.kidsgptbackend.model.consent.ConsentType;
import uk.gegc.kidsgptbackend.model.consent.LawfulBasis;

import java.util.List;
import java.util.UUID;

public record ConsentGrantRequest(
    @NotNull(message = "User ID is required")
    UUID userId,
    
    @NotNull(message = "Consent type is required")
    ConsentType consentType,
    
    @NotBlank(message = "Consent version is required")
    String consentVersion,
    
    @NotBlank(message = "Policy URL is required")
    String policyUrl,
    
    @NotBlank(message = "Content hash is required")
    String contentHash,
    
    UUID verificationId,
    
    @NotBlank(message = "Jurisdiction is required")
    String jurisdiction,
    
    String region,
    
    String locale,
    
    @NotNull(message = "Source is required")
    ConsentSource source,
    
    List<UUID> kids,
    
    String ipAddress,
    
    String userAgent,
    
    @NotNull(message = "Lawful basis is required")
    LawfulBasis lawfulBasis
) {} 