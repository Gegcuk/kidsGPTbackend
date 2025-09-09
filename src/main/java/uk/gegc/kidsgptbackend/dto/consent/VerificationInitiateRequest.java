package uk.gegc.kidsgptbackend.dto.consent;

import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uk.gegc.kidsgptbackend.model.consent.VerificationMethod;
import uk.gegc.kidsgptbackend.validation.ValidVerificationInitiateRequest;

import java.util.UUID;

@ValidVerificationInitiateRequest
@Schema(description = "Request to initiate parent verification", example = "{\"parentId\": \"550e8400-e29b-41d4-a716-446655440000\", \"verificationMethod\": \"EMAIL\", \"contactInfo\": \"parent@example.com\"}")
public record VerificationInitiateRequest(
    @NotNull(message = "Parent ID is required")
    @Schema(description = "Parent's UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID parentId,
    
    @NotNull(message = "Verification method is required")
    @Schema(description = "Verification method (EMAIL or SMS)", example = "EMAIL")
    VerificationMethod verificationMethod,
    
    @NotBlank(message = "Contact information is required")
    @Schema(description = "Email address for EMAIL method, E.164 phone for SMS method", 
            example = "parent@example.com")
    String contactInfo
) {} 