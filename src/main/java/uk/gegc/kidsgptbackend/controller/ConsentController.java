package uk.gegc.kidsgptbackend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gegc.kidsgptbackend.dto.consent.*;
import uk.gegc.kidsgptbackend.service.consent.ConsentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/consent")
@RequiredArgsConstructor
@Slf4j
public class ConsentController {

    private final ConsentService consentService;

    /**
     * Grant consent for a user
     * POST /api/v1/consent/grant
     */
    @PostMapping("/grant")
    public ResponseEntity<ConsentStatusResponse> grantConsent(@Valid @RequestBody ConsentGrantRequest request) {
        log.info("Granting consent for user: {}", request.userId());
        ConsentStatusResponse response = consentService.grantConsent(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Withdraw consent for a user
     * POST /api/v1/consent/withdraw
     */
    @PostMapping("/withdraw")
    public ResponseEntity<ConsentStatusResponse> withdrawConsent(@Valid @RequestBody ConsentWithdrawRequest request) {
        log.info("Withdrawing consent for user: {}", request.userId());
        ConsentStatusResponse response = consentService.withdrawConsent(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get consent history for a user
     * GET /api/v1/consent/history/{userId}
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<ConsentHistoryResponse> getConsentHistory(@PathVariable String userId) {
        log.info("Retrieving consent history for user: {}", userId);
        ConsentHistoryResponse response = consentService.getConsentHistory(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get consent status for a verification ID
     * GET /api/v1/consent/status/{verificationId}
     */
    @GetMapping("/status/{verificationId}")
    public ResponseEntity<ConsentStatusResponse> getConsentStatus(@PathVariable String verificationId) {
        log.info("Retrieving consent status for verification: {}", verificationId);
        ConsentStatusResponse response = consentService.getConsentStatus(verificationId);
        return ResponseEntity.ok(response);
    }
} 