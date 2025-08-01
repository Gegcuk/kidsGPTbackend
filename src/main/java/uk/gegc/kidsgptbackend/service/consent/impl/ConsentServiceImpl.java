package uk.gegc.kidsgptbackend.service.consent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.consent.*;
import uk.gegc.kidsgptbackend.model.consent.*;
import uk.gegc.kidsgptbackend.repository.consent.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.service.consent.ConsentService;
import uk.gegc.kidsgptbackend.util.RequestContextUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsentServiceImpl implements ConsentService {
    
    private final ConsentLedgerRepository consentLedgerRepository;
    private final ConsentChildCoverageRepository consentChildCoverageRepository;
    
    @Value("${app.consent.hmac.secret-ref:default-consent-hmac-secret-key}")
    private String hmacSecret;
    
    @Value("${app.consent.default-retention-years:7}")
    private int defaultRetentionYears;
    
    @Override
    @Transactional
    public ConsentStatusResponse grantConsent(ConsentGrantRequest request) {
        log.info("Granting consent for user: {}", request.userId());
        
        // ---- Idempotency: short-circuit if already granted for this (user,type,version)
        Optional<ConsentLedger> existing = consentLedgerRepository
            .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                request.userId(), request.consentType(), ConsentStatus.GRANTED);
        if (existing.isPresent() && existing.get().getConsentVersion().equals(request.consentVersion())) {
            log.info("Consent already granted for user {} type {} v{}", request.userId(), request.consentType(), request.consentVersion());
            return new ConsentStatusResponse(buildLatestConsentStatus(request.userId()), false);
        }

        // ---- Conditional validation
        if (request.consentType() == ConsentType.PARENTAL_CONSENT && request.verificationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verificationId is required for PARENTAL_CONSENT");
        }
        if ((request.consentType() == ConsentType.TERMS_OF_SERVICE || request.consentType() == ConsentType.PRIVACY_POLICY)
                && (request.kids() != null && !request.kids().isEmpty())) {
            log.debug("Ignoring kids[] for {}", request.consentType());
        }
        if (!isAllowedPolicyHost(request.policyUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid policyUrl host");
        }

        // ---- Single time source (UTC) used everywhere
        Instant nowUtc = Instant.now();
        LocalDateTime nowLocal = LocalDateTime.ofInstant(nowUtc, ZoneOffset.UTC);

        // ---- Retention (minimal): keep default for now (add jurisdiction logic later)
        LocalDateTime retentionExpiresAt = nowLocal.plusYears(defaultRetentionYears);

        // ---- Generate consentId once and reuse across receipt + row
        UUID consentId = UUID.randomUUID();

        // ---- Server-derived IP/UA override client body for security
        String ip = RequestContextUtil.getServerCapturedIp();
        String ua = RequestContextUtil.getServerCapturedUserAgent();
        
        // Log the override for audit purposes
        if (!ip.equals(request.ipAddress()) || !ua.equals(request.userAgent())) {
            log.info("Overriding client-provided IP/UA with server-captured values - Client IP: {} -> Server IP: {}, Client UA: {} -> Server UA: {}", 
                    request.ipAddress(), ip, request.userAgent(), ua);
        }

        // ---- Normalize fields for consistency before signing
        String jurisdiction = request.jurisdiction() == null ? null : request.jurisdiction().trim().toUpperCase(Locale.ROOT);
        String region = request.region() == null ? null : request.region().trim().toUpperCase(Locale.ROOT);
        String locale = request.locale() == null ? null : request.locale().trim();

        // ---- Build canonical receipt JSON (deterministic ordering)
        String receiptJson = buildCanonicalReceiptJson(consentId, request, jurisdiction, region, locale, ip, ua, nowUtc);

        // ---- HMAC over canonical JSON
        byte[] recordSignature = generateHmacSignature(receiptJson);

        // ---- Persist ledger row
        ConsentLedger consentLedger = ConsentLedger.builder()
                .consentId(consentId) // ensure entity allows manual ID set
                .userId(request.userId())
                .consentType(request.consentType())
                .consentVersion(request.consentVersion())
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl(request.policyUrl())
                .contentHash(request.contentHash())
                .jurisdiction(jurisdiction)
                .region(region)
                .locale(locale)
                .lawfulBasis(request.lawfulBasis())
                .source(request.source())
                .ipAddress(ip)
                .userAgent(ua)
                .consentTimestamp(nowLocal)
                .parentVerificationId(request.verificationId())
                .retentionExpiresAt(retentionExpiresAt)
                .receiptJson(receiptJson)
                .recordSignature(recordSignature)
                .build();

        ConsentLedger savedConsent = consentLedgerRepository.save(consentLedger);
        log.info("Saved consent ledger entry with ID: {}", savedConsent.getConsentId());

        // ---- Child coverage: only for parent/processing consents
        if (request.consentType() == ConsentType.PARENTAL_CONSENT || request.consentType() == ConsentType.DATA_PROCESSING) {
            List<ConsentChildCoverage> childCoverages = request.kids().stream()
                .map(kidId -> ConsentChildCoverage.builder()
                        .consentId(savedConsent.getConsentId())
                        .kidId(kidId)
                        .build())
                .collect(Collectors.toList());
            consentChildCoverageRepository.saveAll(childCoverages);
            log.info("Created {} child coverage records for consent: {}", childCoverages.size(), savedConsent.getConsentId());
        }

        // ---- Build response (reconsentNeeded is false for this type immediately after grant)
        List<ConsentStatusResponse.ConsentStatusByType> latestByType = buildLatestConsentStatus(request.userId());
        boolean reconsentNeeded = false; // for this consentType just granted

        return new ConsentStatusResponse(latestByType, reconsentNeeded);
    }
    
    @Override
    public ConsentStatusResponse withdrawConsent(ConsentWithdrawRequest request) {
        log.info("Stub implementation: Withdrawing consent for user: {}", request.userId());
        // TODO: Implement actual consent withdrawal logic
        // - Insert new WITHDRAWN record into consent_ledger
        // - Update any active grants to EXPIRED status
        // - Generate withdrawal receipt
        
        return new ConsentStatusResponse(
            null, // latestByType - to be implemented
            false // reconsentNeeded - to be implemented
        );
    }
    
    @Override
    public ConsentHistoryResponse getConsentHistory(String userId) {
        log.info("Stub implementation: Getting consent history for user: {}", userId);
        // TODO: Implement actual consent history retrieval
        // - Query consent_ledger table for user
        // - Include consent_child_coverage information
        // - Format response with proper pagination
        
        return new ConsentHistoryResponse(
            userId,
            null // entries - to be implemented
        );
    }
    
    @Override
    public ConsentStatusResponse getConsentStatus(String verificationId) {
        log.info("Stub implementation: Getting consent status for verification: {}", verificationId);
        // TODO: Implement actual consent status retrieval
        // - Query consent_ledger by verification_id
        // - Check if reconsent is needed based on policy updates
        // - Return current status and reconsent flag
        
        return new ConsentStatusResponse(
            null, // latestByType - to be implemented
            false // reconsentNeeded - to be implemented
        );
    }

    /** Allowlist the policy host (min guard) */
    private boolean isAllowedPolicyHost(String policyUrl) {
        try {
            URI uri = URI.create(policyUrl);
            String host = uri.getHost();
            return host != null && (host.endsWith("kidsgpt.club") || host.equals("localhost"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Deterministic JSON with stable key order for signing */
    private String buildCanonicalReceiptJson(
            UUID consentId,
            ConsentGrantRequest req,
            String jurisdiction, String region, String locale,
            String ip, String ua,
            Instant nowUtc) {
        try {
            Map<String, Object> m = new TreeMap<>(); // TreeMap => sorted keys
            m.put("consent_id", consentId.toString());
            m.put("parent_uuid", req.userId().toString());
            m.put("kids", req.kids().stream().map(UUID::toString).collect(Collectors.toList()));
            m.put("jurisdiction", jurisdiction);
            m.put("region", region);
            m.put("method", req.verificationId() != null ? "verification_linked" : "n/a");
            m.put("consent_type", req.consentType().name());
            m.put("consent_version", req.consentVersion());
            m.put("policy_url", req.policyUrl());
            m.put("content_hash", req.contentHash());
            m.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(nowUtc));
            m.put("ip", ip);
            m.put("ua", ua);
            m.put("lawful_basis", req.lawfulBasis().name());
            m.put("source", req.source().name());

            // Jackson with canonical ordering
            ObjectMapper om = new ObjectMapper();
            om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return om.writeValueAsString(m);
        } catch (Exception e) {
            log.error("Failed to build canonical receipt JSON", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build receipt");
        }
    }
    
    private byte[] generateHmacSignature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error generating HMAC signature", e);
            throw new RuntimeException("Failed to generate consent signature", e);
        }
    }
    
    private List<ConsentStatusResponse.ConsentStatusByType> buildLatestConsentStatus(UUID userId) {
        // Get latest consent for each type
        List<ConsentLedger> latestConsents = new ArrayList<>();
        
        for (ConsentType type : ConsentType.values()) {
            consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                    userId, type, ConsentStatus.GRANTED)
                    .ifPresent(latestConsents::add);
        }
        
        return latestConsents.stream()
                .map(consent -> new ConsentStatusResponse.ConsentStatusByType(
                        consent.getConsentType(),
                        consent.getConsentVersion(),
                        consent.getConsentStatus(),
                        consent.getConsentTimestamp(),
                        consent.getPolicyUrl()))
                .collect(Collectors.toList());
    }
    
    private boolean checkReconsentNeeded(UUID userId, ConsentType consentType, String currentVersion) {
        // Check if there's a newer version of the policy that requires reconsent
        // This is a simplified implementation - in practice, you'd check against active policies
        Optional<ConsentLedger> latestGrant = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        userId, consentType, ConsentStatus.GRANTED);
        
        if (latestGrant.isPresent()) {
            String grantedVersion = latestGrant.get().getConsentVersion();
            // Simple version comparison - in practice, you'd use semantic versioning
            return grantedVersion != null && !grantedVersion.equals(currentVersion);
        }
        
        return false;
    }
} 