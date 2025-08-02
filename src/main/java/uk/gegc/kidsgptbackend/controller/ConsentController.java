package uk.gegc.kidsgptbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.consent.ConsentGrantRequest;
import uk.gegc.kidsgptbackend.dto.consent.ConsentHistoryResponse;
import uk.gegc.kidsgptbackend.dto.consent.ConsentStatusResponse;
import uk.gegc.kidsgptbackend.dto.consent.ConsentWithdrawRequest;
import uk.gegc.kidsgptbackend.service.consent.ConsentService;

import java.util.UUID;

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
        
        // Return consent ID in header for easy correlation
        return ResponseEntity.ok()
                .header("X-Consent-Id", response.consentId().toString())
                .body(response);
    }

    /**
     * Withdraw consent for a user
     * POST /api/v1/consent/withdraw
     */
    @PostMapping("/withdraw")
    public ResponseEntity<ConsentStatusResponse> withdrawConsent(@Valid @RequestBody ConsentWithdrawRequest request) {
        log.info("Withdrawing consent for user: {}", request.userId());
        ConsentStatusResponse response = consentService.withdrawConsent(request);
        
        // Return consent ID in header for easy correlation (parity with /grant)
        return ResponseEntity.ok()
                .header("X-Consent-Id", response.consentId().toString())
                .body(response);
    }

    /**
     * Get consent history for a user (with optional pagination)
     * GET /api/v1/consent/history/{userId}?page=0&size=20
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<ConsentHistoryResponse.PaginatedConsentHistoryResponse> getConsentHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving consent history for user: {} (page: {}, size: {})", userId, page, size);
        
        // Authorization: Only allow users to access their own consent history
        String currentUserId = getCurrentUserId();
        if (!currentUserId.equals(userId)) {
            log.warn("User {} attempted to access consent history for user {}", currentUserId, userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You can only view your own consent history");
        }
        
        ConsentHistoryResponse.PaginatedConsentHistoryResponse response = consentService.getConsentHistory(userId, page, size);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get the current authenticated user ID
     * @return The current user ID as a string
     * @throws ResponseStatusException if no authenticated user is found
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        
        String principal = authentication.getName();
        if (principal == null || principal.equals("anonymousUser")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid authentication required");
        }
        
        // Assuming the principal is the user ID (UUID string)
        try {
            UUID.fromString(principal); // Validate UUID format
            return principal;
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID format in authentication principal: {}", principal);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid user authentication");
        }
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