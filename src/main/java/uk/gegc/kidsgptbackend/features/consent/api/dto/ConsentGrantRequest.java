package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis;

import java.util.List;
import java.util.UUID;

@Schema(description = "Payload to grant parental consent")
public record ConsentGrantRequest(
    @Schema(description = "User receiving consent")
    @NotNull(message = "User ID is required")
    UUID userId,
    
    @Schema(description = "Type of consent being granted")
    @NotNull(message = "Consent type is required")
    ConsentType consentType,
    
    @Schema(description = "Version of the consent document")
    @NotBlank(message = "Consent version is required")
    String consentVersion,
    
    @Schema(description = "URL to the consent policy")
    @NotBlank(message = "Policy URL is required")
    String policyUrl,
    
    @Schema(description = "Hash of the consent document content")
    @NotBlank(message = "Content hash is required")
    String contentHash,
    
    @Schema(description = "Verification request ID, if used")
    UUID verificationId,
    
    @Schema(description = "Jurisdiction (e.g., US, EU)")
    @NotBlank(message = "Jurisdiction is required")
    String jurisdiction,
    
    @Schema(description = "Region/subdivision")
    String region,
    
    @Schema(description = "Locale of the document")
    String locale,
    
    @Schema(description = "Source of consent (app/web/etc.)")
    @NotNull(message = "Source is required")
    ConsentSource source,
    
    @Schema(description = "Associated kid IDs")
    List<UUID> kids,
    
    @Schema(description = "Request IP address")
    String ipAddress,
    
    @Schema(description = "User agent string")
    String userAgent,
    
    @Schema(description = "Lawful basis for processing")
    @NotNull(message = "Lawful basis is required")
    LawfulBasis lawfulBasis
) {}
