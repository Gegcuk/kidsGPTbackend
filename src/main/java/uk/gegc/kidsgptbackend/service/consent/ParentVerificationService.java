package uk.gegc.kidsgptbackend.service.consent;

import uk.gegc.kidsgptbackend.dto.consent.VerificationInitiateRequest;
import uk.gegc.kidsgptbackend.dto.consent.VerificationInitiationResult;
import uk.gegc.kidsgptbackend.dto.consent.VerificationStatusResponse;
import uk.gegc.kidsgptbackend.dto.consent.VerificationVerifyRequest;

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