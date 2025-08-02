package uk.gegc.kidsgptbackend.service.consent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.consent.ConsentGrantRequest;
import uk.gegc.kidsgptbackend.dto.consent.ConsentHistoryResponse;
import uk.gegc.kidsgptbackend.dto.consent.ConsentStatusResponse;
import uk.gegc.kidsgptbackend.dto.consent.ConsentWithdrawRequest;
import uk.gegc.kidsgptbackend.model.consent.ConsentChildCoverage;
import uk.gegc.kidsgptbackend.model.consent.ConsentLedger;
import uk.gegc.kidsgptbackend.model.consent.ConsentStatus;
import uk.gegc.kidsgptbackend.model.consent.ConsentType;
import uk.gegc.kidsgptbackend.repository.consent.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.repository.consent.ParentVerificationRepository;
import uk.gegc.kidsgptbackend.service.consent.ConsentService;
import uk.gegc.kidsgptbackend.util.RequestContextUtil;
import uk.gegc.kidsgptbackend.validation.SimpleConstraintViolation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLIntegrityConstraintViolationException;
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
    private final ParentVerificationRepository parentVerificationRepository;
    
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
            return new ConsentStatusResponse(buildLatestConsentStatus(request.userId()), false, existing.get().getConsentId());
        }

        // ---- Conditional validation
        if (request.consentType() == ConsentType.PARENTAL_CONSENT && request.verificationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verificationId is required for PARENTAL_CONSENT");
        }
        if ((request.consentType() == ConsentType.TERMS_OF_SERVICE || request.consentType() == ConsentType.PRIVACY_POLICY)
                && (request.kids() != null && !request.kids().isEmpty())) {
            log.debug("Ignoring kids[] for {}", request.consentType());
        }
        if (!request.policyUrl().startsWith("https://") || !isAllowedPolicyHost(request.policyUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid policyUrl: must be HTTPS and from allowed host");
        }

        // ---- Single time source (UTC) used everywhere
        Instant nowUtc = Instant.now();
        LocalDateTime nowLocal = LocalDateTime.ofInstant(nowUtc, ZoneOffset.UTC);

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

        // ---- Kids list null-safety for receipt and coverage
        List<UUID> kids = Optional.ofNullable(request.kids()).orElseGet(Collections::emptyList);
        
        // ---- Enforce when needed
        if ((request.consentType() == ConsentType.PARENTAL_CONSENT || request.consentType() == ConsentType.DATA_PROCESSING)
                && kids.isEmpty()) {
            throw new ConstraintViolationException(
                    "kids are required",
                    Set.of(new SimpleConstraintViolation("kids are required for " + request.consentType()))
            );        }
        
        // ---- Ignore (and drop) kids for non-child consents to avoid leaking child IDs
        if (request.consentType() == ConsentType.TERMS_OF_SERVICE || request.consentType() == ConsentType.PRIVACY_POLICY) {
            if (!kids.isEmpty()) {
                log.warn("Dropping kids[] for {} to avoid persisting child IDs in non-child consent", request.consentType());
            }
            kids = Collections.emptyList(); // <— ensures receipt_json has no kids and no coverage rows are created
        }
        
        // ---- Dedup and sort kids for canonical order (stable signatures)
        kids = kids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();

        // ---- Normalize fields for consistency before signing
        String jurisdiction = request.jurisdiction() == null ? null : request.jurisdiction().trim().toUpperCase(Locale.ROOT);
        String region = request.region() == null ? null : request.region().trim().toUpperCase(Locale.ROOT);
        String locale = normalizeLocale(request.locale());

        // ---- Retention calculation based on consent type and jurisdiction (after normalization)
        int retentionYears = calculateRetentionYears(request.consentType(), jurisdiction);
        LocalDateTime retentionExpiresAt = nowLocal.plusYears(retentionYears);
        log.info("Calculated retention: {} years for consent type {} jurisdiction {} (default: {})", 
                retentionYears, request.consentType(), jurisdiction, defaultRetentionYears);

        // ---- Resolve verification method for receipt
        String verificationMethod = resolveVerificationMethod(request.verificationId());

        // ---- Build canonical receipt JSON (deterministic ordering)
        String receiptJson = buildCanonicalReceiptJson(consentId, request, jurisdiction, region, locale, ip, ua, nowUtc, kids, verificationMethod);

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

        ConsentLedger savedConsent;
        try {
            // Force DB hit now so we catch constraint violations here, not at tx commit
            savedConsent = consentLedgerRepository.saveAndFlush(consentLedger);
            log.info("Saved consent ledger entry with ID: {}", savedConsent.getConsentId());
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKey(e)) {
                log.warn("Duplicate grant raced; returning current status. user={}, type={}, v={}",
                         request.userId(), request.consentType(), request.consentVersion());
                // Fetch by exact version to avoid returning newer version if published between requests
                UUID existingConsentId = consentLedgerRepository
                    .findActiveGrantByUserTypeAndVersion(request.userId(), request.consentType(), request.consentVersion())
                    .map(ConsentLedger::getConsentId)
                    .orElse(null);
                return new ConsentStatusResponse(buildLatestConsentStatus(request.userId()), false, existingConsentId);
            }
            throw e; // not a duplicate — bubble up
        }

        // ---- Child coverage: only for parent/processing consents
        if (request.consentType() == ConsentType.PARENTAL_CONSENT || request.consentType() == ConsentType.DATA_PROCESSING) {
            List<ConsentChildCoverage> childCoverages = kids.stream()
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

        return new ConsentStatusResponse(latestByType, reconsentNeeded, consentId);
    }
    
    @Override
    @Transactional
    public ConsentStatusResponse withdrawConsent(ConsentWithdrawRequest request) {
        log.info("Withdrawing consent for user: {}", request.userId());
        
        // ---- Validate and parse userId
        UUID userId;
        try {
            userId = UUID.fromString(request.userId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userId format: " + request.userId());
        }
        
        Instant nowUtc = Instant.now();
        LocalDateTime nowLocal = LocalDateTime.ofInstant(nowUtc, ZoneOffset.UTC);
        String ip = RequestContextUtil.getServerCapturedIp();
        String ua = RequestContextUtil.getServerCapturedUserAgent();
        
        // Log the override for audit purposes
        if (!ip.equals(request.ipAddress()) || !ua.equals(request.userAgent())) {
            log.info("Overriding client-provided IP/UA with server-captured values - Client IP: {} -> Server IP: {}, Client UA: {} -> Server UA: {}", 
                    request.ipAddress(), ip, request.userAgent(), ua);
        }
        
        // ---- Locate exact grant (version is @NotBlank, so always use exact version)
        Optional<ConsentLedger> target = consentLedgerRepository
            .findActiveGrantByUserTypeAndVersion(userId, request.consentType(), request.consentVersion());
        
        if (target.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "No active consent found to withdraw for user " + request.userId() + " and type " + request.consentType() + " version " + request.consentVersion());
        }
        ConsentLedger granted = target.get();
        
        // ---- Check if this is the current active version (prevent withdrawing old versions)
        Optional<ConsentLedger> latestGranted = consentLedgerRepository
            .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                userId, request.consentType(), ConsentStatus.GRANTED);
        
        if (latestGranted.isPresent() && !latestGranted.get().getConsentVersion().equals(request.consentVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Cannot withdraw version " + request.consentVersion() + " when version " + latestGranted.get().getConsentVersion() + " is active. " +
                "Only the current active version can be withdrawn.");
        }
        
        // ---- Idempotency: check if withdrawal already exists for this (user,type,version)
        if (consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(userId, request.consentType(), granted.getConsentVersion())) {
            // Fetch the existing withdrawal
            Optional<ConsentLedger> existingWithdrawal = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                    userId, request.consentType(), ConsentStatus.WITHDRAWN);
            if (existingWithdrawal.isPresent()) {
                UUID existingId = existingWithdrawal.get().getConsentId();
                log.info("Consent already withdrawn for user {} type {} v{}, returning existing withdrawal ID: {}", 
                        request.userId(), request.consentType(), granted.getConsentVersion(), existingId);
                return new ConsentStatusResponse(
                    buildEffectiveConsentStatus(userId), // show WITHDRAWN
                    true,
                    existingId
                );
            }
        }
        
        UUID withdrawalId = UUID.randomUUID();
        String receiptJson = buildWithdrawalReceiptJson(withdrawalId, request, granted, ip, ua, nowUtc);
        byte[] recordSignature = generateHmacSignature(receiptJson);
        
        // ---- Persist withdrawal ledger row
        ConsentLedger withdrawalLedger = ConsentLedger.builder()
                .consentId(withdrawalId)
                .userId(userId)
                .consentType(granted.getConsentType())
                .consentVersion(granted.getConsentVersion()) // Use version from grant, not request
                .consentStatus(ConsentStatus.WITHDRAWN)
                .policyUrl(granted.getPolicyUrl())
                .contentHash(granted.getContentHash())
                .jurisdiction(granted.getJurisdiction())
                .region(granted.getRegion())
                .locale(granted.getLocale())
                .lawfulBasis(granted.getLawfulBasis())
                .source(granted.getSource())
                .ipAddress(ip)
                .userAgent(ua)
                .consentTimestamp(nowLocal)
                .parentVerificationId(granted.getParentVerificationId())
                .retentionExpiresAt(granted.getRetentionExpiresAt())
                .withdrawnConsentId(granted.getConsentId()) // Link to the withdrawn consent
                .receiptJson(receiptJson)
                .recordSignature(recordSignature)
                .build();
        
        ConsentLedger savedWithdrawal;
        try {
            savedWithdrawal = consentLedgerRepository.saveAndFlush(withdrawalLedger);
            log.info("Saved consent withdrawal entry with ID: {}", savedWithdrawal.getConsentId());
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKey(e)) {
                log.warn("Duplicate withdrawal raced; returning current status. user={}, type={}, v={}",
                         request.userId(), request.consentType(), granted.getConsentVersion());
                // Fetch existing withdrawal
                UUID existingWithdrawalId = consentLedgerRepository
                    .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        userId, request.consentType(), ConsentStatus.WITHDRAWN)
                    .map(ConsentLedger::getConsentId)
                    .orElse(null);
                return new ConsentStatusResponse(buildEffectiveConsentStatus(userId), true, existingWithdrawalId);
            }
            log.error("Failed to save consent withdrawal", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process consent withdrawal");
        }
        
        // ---- Return effective statuses (not GRANTED-only)
        return new ConsentStatusResponse(
            buildEffectiveConsentStatus(userId),
            true, // reconsentNeeded is true after withdrawal
            withdrawalId
        );
    }
    
    @Override
    @Transactional(readOnly = true)
    public ConsentHistoryResponse getConsentHistory(String userId) {
        log.info("Getting consent history for user: {}", userId);
        
        try {
            UUID userUuid = UUID.fromString(userId);
            
            // Query consent_ledger table for user, ordered by consent timestamp (canonical event time) with deterministic tie-breaker
            List<ConsentLedger> consentLedgers = consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(userUuid);
            
            if (consentLedgers.isEmpty()) {
                return new ConsentHistoryResponse(userId, Collections.emptyList());
            }
            
            // Batch fetch all child coverage data to avoid N+1 queries
            List<UUID> consentIds = consentLedgers.stream()
                .map(ConsentLedger::getConsentId)
                .collect(Collectors.toList());
            
            List<ConsentChildCoverage> allCoverages = consentChildCoverageRepository.findByConsentIds(consentIds);
            
            // Group coverages by consent ID for efficient lookup
            Map<UUID, List<String>> coverageMap = allCoverages.stream()
                .collect(Collectors.groupingBy(
                    ConsentChildCoverage::getConsentId,
                    Collectors.mapping(
                        coverage -> coverage.getKidId().toString(),
                        Collectors.toList()
                    )
                ));
            
            List<ConsentHistoryResponse.ConsentHistoryEntry> entries = consentLedgers.stream()
                .map(consentLedger -> buildConsentHistoryEntry(consentLedger, coverageMap))
                .collect(Collectors.toList());
            
            return new ConsentHistoryResponse(userId, entries);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for userId: {}", userId, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user ID format");
        } catch (Exception e) {
            log.error("Error retrieving consent history for user: {}", userId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve consent history");
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public ConsentHistoryResponse getConsentHistory(String userId, int page, int size) {
        log.info("Getting paginated consent history for user: {} (page: {}, size: {})", userId, page, size);
        
        try {
            UUID userUuid = UUID.fromString(userId);
            
            // Validate pagination parameters
            if (page < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page number must be non-negative");
            }
            if (size <= 0 || size > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100");
            }
            
            // Use explicit Sort to ensure deterministic ordering with tie-breaker
            Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("consentTimestamp"),
                Sort.Order.desc("createdAt")
            ));
            Page<ConsentLedger> consentLedgerPage = consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(userUuid, pageable);
            
            if (consentLedgerPage.isEmpty()) {
                return new ConsentHistoryResponse(userId, Collections.emptyList());
            }
            
            List<ConsentLedger> consentLedgers = consentLedgerPage.getContent();
            
            // Batch fetch all child coverage data to avoid N+1 queries
            List<UUID> consentIds = consentLedgers.stream()
                .map(ConsentLedger::getConsentId)
                .collect(Collectors.toList());
            
            List<ConsentChildCoverage> allCoverages = consentChildCoverageRepository.findByConsentIds(consentIds);
            
            // Group coverages by consent ID for efficient lookup
            Map<UUID, List<String>> coverageMap = allCoverages.stream()
                .collect(Collectors.groupingBy(
                    ConsentChildCoverage::getConsentId,
                    Collectors.mapping(
                        coverage -> coverage.getKidId().toString(),
                        Collectors.toList()
                    )
                ));
            
            List<ConsentHistoryResponse.ConsentHistoryEntry> entries = consentLedgers.stream()
                .map(consentLedger -> buildConsentHistoryEntry(consentLedger, coverageMap))
                .collect(Collectors.toList());
            
            return new ConsentHistoryResponse(userId, entries);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for userId: {}", userId, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user ID format");
        } catch (Exception e) {
            log.error("Error retrieving paginated consent history for user: {}", userId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve consent history");
        }
    }
    
    private ConsentHistoryResponse.ConsentHistoryEntry buildConsentHistoryEntry(
            ConsentLedger consentLedger, 
            Map<UUID, List<String>> coverageMap) {
        
        // Get covered kids for this consent (sorted for deterministic output with duplicates removed)
        List<String> coveredKids = coverageMap.getOrDefault(consentLedger.getConsentId(), Collections.emptyList())
            .stream()
            .distinct() // Remove duplicates as defensive guard
            .sorted()
            .collect(Collectors.toList());
        
        return new ConsentHistoryResponse.ConsentHistoryEntry(
            consentLedger.getConsentId().toString(),
            consentLedger.getConsentType(),
            consentLedger.getConsentVersion(),
            consentLedger.getConsentStatus(),
            consentLedger.getPolicyUrl(),
            consentLedger.getContentHash(),
            consentLedger.getJurisdiction(),
            consentLedger.getRegion(),
            consentLedger.getLocale(),
            consentLedger.getLawfulBasis(),
            consentLedger.getSource(),
            consentLedger.getIpAddress(),
            consentLedger.getUserAgent(),
            consentLedger.getConsentTimestamp(),
            consentLedger.getParentVerificationId() != null ? consentLedger.getParentVerificationId().toString() : null,
            consentLedger.getRetentionExpiresAt(),
            consentLedger.getCreatedAt(),
            coveredKids,
            consentLedger.getWithdrawnConsentId() != null ? consentLedger.getWithdrawnConsentId().toString() : null
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
            false, // reconsentNeeded - to be implemented
            null // consentId - to be implemented
        );
    }

    /** Allowlist the policy host (min guard) */
    private boolean isAllowedPolicyHost(String policyUrl) {
        try {
            URI uri = URI.create(policyUrl);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            
            // Normalize host to lowercase for consistent comparison
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            
            // Allow exact match or subdomain of kidsgpt.club (prevents evilkidsgpt.club)
            return normalizedHost.equals("kidsgpt.club") || 
                   normalizedHost.endsWith(".kidsgpt.club") || 
                   normalizedHost.equals("localhost") ||
                    normalizedHost.equals("example.com");

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
            Instant nowUtc,
            List<UUID> kids,
            String verificationMethod) {
        try {
            Map<String, Object> m = new TreeMap<>(); // TreeMap => sorted keys
            m.put("consent_id", consentId.toString());
            m.put("parent_uuid", req.userId().toString());
            m.put("kids", kids.stream().map(UUID::toString).sorted().collect(Collectors.toList()));
            m.put("jurisdiction", jurisdiction);
            m.put("region", region);
            m.put("method", verificationMethod);
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
    
    /** Build withdrawal receipt JSON with withdrawal-specific fields */
    private String buildWithdrawalReceiptJson(
            UUID consentId,
            ConsentWithdrawRequest req,
            ConsentLedger grantedConsent,
            String ip, String ua,
            Instant nowUtc) {
        try {
            Map<String, Object> m = new TreeMap<>(); // TreeMap => sorted keys
            m.put("consent_id", consentId.toString());
            m.put("parent_uuid", req.userId());
            m.put("withdrawn_consent_id", grantedConsent.getConsentId().toString());
            m.put("consent_type", req.consentType().name());
            m.put("consent_version", grantedConsent.getConsentVersion());
            m.put("policy_url", grantedConsent.getPolicyUrl());
            m.put("content_hash", grantedConsent.getContentHash());
            m.put("jurisdiction", grantedConsent.getJurisdiction());
            m.put("region", grantedConsent.getRegion());
            m.put("locale", grantedConsent.getLocale());
            m.put("lawful_basis", grantedConsent.getLawfulBasis().name());
            m.put("source", grantedConsent.getSource().name());
            m.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(nowUtc));
            m.put("ip", ip);
            m.put("ua", ua);
            m.put("action", "WITHDRAWN");
            if (req.reason() != null && !req.reason().trim().isEmpty()) {
                m.put("reason", req.reason().trim());
            }

            // Jackson with canonical ordering
            ObjectMapper om = new ObjectMapper();
            om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return om.writeValueAsString(m);
        } catch (Exception e) {
            log.error("Failed to build withdrawal receipt JSON", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build withdrawal receipt");
        }
    }
    
    private byte[] generateHmacSignature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            
            // Handle Base64 encoded keys (common from KMS) with fallback to UTF-8
            byte[] keyBytes;
            if (hmacSecret.matches("^[A-Za-z0-9+/=]+$")) {
                try {
                    keyBytes = java.util.Base64.getDecoder().decode(hmacSecret);
                    log.debug("Using Base64 decoded HMAC key");
                } catch (IllegalArgumentException e) {
                    log.warn("Failed to decode Base64 HMAC key, falling back to UTF-8: {}", e.getMessage());
                    keyBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
                }
            } else {
                keyBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
            }
            
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "HmacSHA256");
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
    
    /**
     * Build effective consent status that shows the latest status per type (GRANTED or WITHDRAWN)
     * This is used for withdrawal responses to show the actual current status
     * Uses consentTimestamp for ordering to be consistent with the canonical event time
     */
    private List<ConsentStatusResponse.ConsentStatusByType> buildEffectiveConsentStatus(UUID userId) {
        // Get latest consent for each type (regardless of status)
        List<ConsentLedger> latestConsents = new ArrayList<>();
        
        for (ConsentType type : ConsentType.values()) {
            // Use consentTimestamp for ordering to be consistent with canonical event time (with deterministic tie-breaker)
            Optional<ConsentLedger> latestConsent = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(userId, type);
            latestConsent.ifPresent(latestConsents::add);
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

    /**
     * Check if the DataIntegrityViolationException is specifically a duplicate key violation.
     * Only treat MySQL duplicate key (error code 1062, SQLState 23000) as "idempotent retry".
     */
    private boolean isDuplicateKey(DataIntegrityViolationException ex) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        if (root instanceof SQLIntegrityConstraintViolationException sqlEx) {
            // MySQL duplicate key
            return "23000".equals(sqlEx.getSQLState()) || sqlEx.getErrorCode() == 1062;
        }
        // Fallback: some drivers wrap differently; be conservative
        String msg = root.getMessage();
        return msg != null && (msg.contains("Duplicate entry") || msg.contains("Duplicate key"));
    }
    
    /**
     * Resolve the verification method from the verification ID.
     * 
     * @param verificationId The verification ID to resolve
     * @return The verification method name (e.g., "EMAIL", "SMS", "PHONE_CALL", "DOCUMENT_UPLOAD") or "n/a" if not found
     */
    private String resolveVerificationMethod(UUID verificationId) {
        if (verificationId == null) {
            return "n/a";
        }
        
        try {
            // TODO: In a real implementation, you might want to check verification status as well
            // For now, we just look up the verification method
            return parentVerificationRepository.findById(verificationId)
                    .map(verification -> verification.getVerificationMethod().name())
                    .orElse("unknown");
        } catch (Exception e) {
            log.warn("Failed to resolve verification method for ID: {}, using 'unknown'", verificationId, e);
            return "unknown";
        }
    }
    
    /**
     * Calculate retention years based on consent type and jurisdiction.
     * TODO: Expand this with jurisdiction-specific logic and policy version requirements.
     * 
     * @param consentType The type of consent being granted
     * @param jurisdiction The jurisdiction (e.g., "UK", "EU", "US")
     * @return Retention period in years
     */
    private int calculateRetentionYears(ConsentType consentType, String jurisdiction) {
        String j = jurisdiction == null ? "" : jurisdiction.toUpperCase(Locale.ROOT);
        if ("UK".equals(j)) j = "GB"; // tolerate both UK and GB
        
        switch (consentType) {
            case TERMS_OF_SERVICE:
                // Contract terms typically have longer retention (6-10 years)
                return "GB".equals(j) ? 6 : 7;
                
            case PRIVACY_POLICY:
                // Privacy policies often have standard retention (5-7 years)
                return 5;
                
            case PARENTAL_CONSENT:
                // Parental consent may have child-age-based retention
                // TODO: Consider child's age when calculating retention
                return "GB".equals(j) ? 8 : 7;
                
            case DATA_PROCESSING:
                // Data processing consent may have longer retention for audit purposes
                return 8;
                
            default:
                log.warn("Unknown consent type: {}, using default retention of {} years", consentType, defaultRetentionYears);
                return defaultRetentionYears;
        }
    }
    
    /**
     * Normalize locale to IETF BCP-47 format.
     * Converts formats like "en-gb" to "en-GB" for consistency in receipts.
     * 
     * @param locale The locale string to normalize
     * @return Normalized locale string in IETF BCP-47 format, or null if input is null
     */
    private String normalizeLocale(String locale) {
        if (locale == null || locale.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = locale.trim();
        
        // Handle common patterns for locale normalization
        // Convert "en-gb" -> "en-GB", "en-us" -> "en-US", etc.
        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-", 2);
            if (parts.length == 2) {
                String language = parts[0].toLowerCase();
                String region = parts[1].toUpperCase();
                
                // Validate basic format (language should be 2-3 chars, region should be 2-3 chars)
                if (language.length() >= 2 && language.length() <= 3 && 
                    region.length() >= 2 && region.length() <= 3) {
                    return language + "-" + region;
                }
            }
        }
        
        // If no region part or invalid format, just return lowercase language code
        if (trimmed.length() >= 2 && trimmed.length() <= 3) {
            return trimmed.toLowerCase();
        }
        
        // If format is unrecognized, return as-is but trimmed
        log.debug("Unrecognized locale format: {}, returning as-is", locale);
        return trimmed;
    }
} 