package uk.gegc.kidsgptbackend.service.consent;

import uk.gegc.kidsgptbackend.dto.consent.ConsentGrantRequest;
import uk.gegc.kidsgptbackend.dto.consent.ConsentHistoryResponse;
import uk.gegc.kidsgptbackend.dto.consent.ConsentStatusResponse;
import uk.gegc.kidsgptbackend.dto.consent.ConsentWithdrawRequest;

public interface ConsentService {
    
    /**
     * Grant consent for a user
     * @param request The consent grant request
     * @return Consent status response
     */
    ConsentStatusResponse grantConsent(ConsentGrantRequest request);
    
    /**
     * Withdraw consent for a user
     * @param request The consent withdraw request
     * @return Consent status response
     */
    ConsentStatusResponse withdrawConsent(ConsentWithdrawRequest request);
    
    /**
     * Get consent history for a user
     * @param userId The user ID
     * @return Consent history response
     */
    ConsentHistoryResponse getConsentHistory(String userId);
    
    /**
     * Get consent status for a verification ID
     * @param verificationId The verification ID
     * @return Consent status response
     */
    ConsentStatusResponse getConsentStatus(String verificationId);
} 