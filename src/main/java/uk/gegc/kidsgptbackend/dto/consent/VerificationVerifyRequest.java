package uk.gegc.kidsgptbackend.dto.consent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

@Schema(description = "Request to verify parent with verification code", example = "{\"verificationId\": \"550e8400-e29b-41d4-a716-446655440001\", \"verificationCode\": \"123456\"}")
public record VerificationVerifyRequest(
    @NotNull(message = "Verification ID is required")
    @Schema(description = "Verification UUID", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID verificationId,
    
    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be 6 digits")
    @Schema(description = "6-digit verification code sent via email/SMS", example = "123456")
    String verificationCode
) {} 