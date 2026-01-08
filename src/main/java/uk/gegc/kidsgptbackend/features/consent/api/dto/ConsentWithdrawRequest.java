package uk.gegc.kidsgptbackend.features.consent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;

@Schema(description = "Payload to withdraw consent")
public record ConsentWithdrawRequest(
    @Schema(description = "User whose consent is withdrawn")
    @NotBlank(message = "User ID is required")
    String userId,
    
    @Schema(description = "Consent type to withdraw")
    @NotNull(message = "Consent type is required")
    ConsentType consentType,
    
    @Schema(description = "Version of the consent being withdrawn")
    @NotBlank(message = "Consent version is required")
    String consentVersion,
    
    @Schema(description = "Optional reason for withdrawal")
    String reason,
    
    @Schema(description = "Request IP address")
    String ipAddress,
    
    @Schema(description = "User agent string")
    String userAgent
) {}
