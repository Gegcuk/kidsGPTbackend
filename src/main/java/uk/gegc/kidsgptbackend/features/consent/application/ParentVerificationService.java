package uk.gegc.kidsgptbackend.features.consent.application;

import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationInitiateRequest;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationInitiationResult;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationStatusResponse;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationVerifyRequest;

import java.util.UUID;

public interface ParentVerificationService {
    
    /**
     * Initiate parent verification process
     * @param request The verification initiation request
     * @return Verification initiation result with status and creation flag
     */
    VerificationInitiationResult initiateVerification(VerificationInitiateRequest request);
    
    /**
     * Verify parent with verification code
     * @param request The verification request
     * @return Verification status response
     */
    VerificationStatusResponse verifyParent(VerificationVerifyRequest request);
    
    /**
     * Get verification status
     * @param verificationId The verification ID
     * @return Verification status response
     */
    VerificationStatusResponse getVerificationStatus(UUID verificationId);
} 