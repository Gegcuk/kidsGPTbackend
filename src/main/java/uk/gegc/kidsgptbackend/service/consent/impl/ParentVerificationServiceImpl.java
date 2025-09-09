package uk.gegc.kidsgptbackend.service.consent.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.consent.VerificationInitiateRequest;
import uk.gegc.kidsgptbackend.dto.consent.VerificationInitiationResult;
import uk.gegc.kidsgptbackend.dto.consent.VerificationStatusResponse;
import uk.gegc.kidsgptbackend.dto.consent.VerificationVerifyRequest;
import uk.gegc.kidsgptbackend.model.consent.ParentVerification;
import uk.gegc.kidsgptbackend.model.consent.VerificationMethod;
import uk.gegc.kidsgptbackend.model.consent.VerificationStatus;
import uk.gegc.kidsgptbackend.repository.consent.ParentVerificationRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.consent.ParentVerificationService;
import uk.gegc.kidsgptbackend.service.email.EmailService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

record VerificationCreationResult(ParentVerification verification, String code) {}

@Service
@RequiredArgsConstructor
@Slf4j
public class ParentVerificationServiceImpl implements ParentVerificationService {

    private final ParentVerificationRepository parentVerificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;
    
    @Value("${verification.pepper}")
    private String verificationPepper;
    
    @Value("${verification.ttl-minutes:30}")
    private int ttlMinutes;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public VerificationInitiationResult initiateVerification(VerificationInitiateRequest request) {
        log.info("Initiating verification for parent: {} with method: {} for contact: {}", 
                request.parentId(), request.verificationMethod(), maskContactInfo(request.contactInfo()));
        
        // Validate parent exists
        if (!userRepository.existsById(request.parentId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent not found with ID: " + request.parentId());
        }
        
        // Check jurisdiction rules for allowed methods (for now, allow all methods)
        // TODO: Implement jurisdiction-specific validation
        
        // Check for existing pending verification (idempotency)
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
        
        boolean newlyCreated = true;
        ParentVerification verification;
        
        String verificationCode = null;
        
        // Normalize contact info before hashing
        String normalizedContact = normalizeContactInfo(request.contactInfo(), request.verificationMethod());
        byte[] contactHash = hashContactInfo(normalizedContact);
        
        // Try to find existing pending verification for the same method and contact
        Optional<ParentVerification> existingForMethod = parentVerificationRepository
                .findPendingForParentMethodContact(request.parentId(), request.verificationMethod(), contactHash, now);
        
        if (existingForMethod.isPresent()) {
            verification = existingForMethod.get();
            newlyCreated = false;
            log.info("Reusing existing pending verification: {}", verification.getVerificationId());
            
            // Rotate the verification code for resend
            verificationCode = generateVerificationCode();
            verification.setVerificationCodeHash(hashVerificationCode(verificationCode, verification.getVerificationId()));
            verification.setExpiresAt(now.plusMinutes(ttlMinutes)); // Extend expiry
            verification = parentVerificationRepository.save(verification);
            log.info("Rotated verification code for existing verification: {}", verification.getVerificationId());
        } else {
            var result = createNewVerification(request, now, contactHash);
            verification = result.verification();
            verificationCode = result.code();
        }
        
        // Schedule email sending after transaction commits
        if (request.verificationMethod() == VerificationMethod.EMAIL && verificationCode != null) {
            scheduleEmailSending(normalizedContact, verificationCode);
        } else if (request.verificationMethod() == VerificationMethod.SMS) {
            // TODO: Implement SMS verification
            log.warn("SMS verification not implemented yet for contact: {}", maskContactInfo(request.contactInfo()));
        } else {
            log.warn("Unsupported verification method: {}", request.verificationMethod());
        }
        
        // Convert to response
        VerificationStatusResponse response = new VerificationStatusResponse(
                verification.getVerificationId(),
                verification.getParentId(),
                verification.getVerificationMethod(),
                verification.getVerificationStatus(),
                verification.getAttemptCount(),
                OffsetDateTime.of(verification.getExpiresAt(), ZoneOffset.UTC),
                verification.getVerifiedAt() != null ? OffsetDateTime.of(verification.getVerifiedAt(), ZoneOffset.UTC) : null,
                OffsetDateTime.of(verification.getCreatedAt(), ZoneOffset.UTC)
        );
        
        return new VerificationInitiationResult(response, newlyCreated);
    }

    private VerificationCreationResult createNewVerification(VerificationInitiateRequest request, LocalDateTime now, byte[] contactHash) {
        String verificationCode = generateVerificationCode();
        LocalDateTime expiresAt = now.plusMinutes(ttlMinutes); // Configurable timeout
        
        ParentVerification verification = ParentVerification.builder()
                .parentId(request.parentId())
                .verificationMethod(request.verificationMethod())
                .verificationStatus(VerificationStatus.PENDING)
                .contactInfoHash(contactHash)
                .verificationCodeHash(new byte[32]) // Placeholder, will be updated after ID generation
                .attemptCount(0)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build();
        
        try {
            verification = parentVerificationRepository.save(verification);
            log.info("Created new verification: {}", verification.getVerificationId());
            
            // Now update the verification code hash with the generated ID
            verification.setVerificationCodeHash(hashVerificationCode(verificationCode, verification.getVerificationId()));
            verification = parentVerificationRepository.save(verification);
            
        } catch (DataIntegrityViolationException e) {
            // Handle race condition - another thread created the same verification
            log.info("Race condition detected, attempting to find existing verification");
            Optional<ParentVerification> existingForMethod = parentVerificationRepository
                    .findPendingForParentMethodContact(request.parentId(), request.verificationMethod(), contactHash, now);
            
            if (existingForMethod.isPresent()) {
                verification = existingForMethod.get();
                log.info("Found existing verification after race condition: {}", verification.getVerificationId());
                
                // Rotate the verification code for resend
                verificationCode = generateVerificationCode();
                verification.setVerificationCodeHash(hashVerificationCode(verificationCode, verification.getVerificationId()));
                verification.setExpiresAt(now.plusMinutes(ttlMinutes)); // Extend expiry
                verification = parentVerificationRepository.save(verification);
                log.info("Rotated verification code after race condition: {}", verification.getVerificationId());
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Verification creation failed due to concurrent request");
            }
        }
        
        return new VerificationCreationResult(verification, verificationCode);
    }
    
    private String normalizeContactInfo(String contactInfo, VerificationMethod method) {
        if (contactInfo == null || contactInfo.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact information cannot be null or empty");
        }
        String normalized = contactInfo.trim();
        if (method == VerificationMethod.EMAIL) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        // For SMS, we already require E.164 format, just trim
        return normalized;
    }
    
    private String generateVerificationCode() {
        // Generate a 6-digit numeric code
        return String.format("%06d", secureRandom.nextInt(1000000));
    }
    
    private byte[] hashContactInfo(String contactInfo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(verificationPepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            return mac.doFinal(contactInfo.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 not available", e);
        }
    }
    
    private byte[] hashVerificationCode(String code, UUID verificationId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(verificationPepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            // Use verificationId as salt for per-record uniqueness
            String salt = verificationId.toString();
            return mac.doFinal((salt + ":" + code).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 not available", e);
        }
    }
    
    private String maskEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "***@unknown";
        }
        int at = email.indexOf('@');
        return at > 0 ? "***@" + email.substring(at + 1) : "***@unknown";
    }
    
    private String maskContactInfo(String contactInfo) {
        if (contactInfo == null || contactInfo.trim().isEmpty()) {
            return "***";
        }
        // If it's an email, mask as email
        if (contactInfo.contains("@")) {
            return maskEmail(contactInfo);
        }
        // If it's a phone number, mask all but last 2 digits
        if (contactInfo.length() > 2) {
            return "***" + contactInfo.substring(contactInfo.length() - 2);
        }
        return "***";
    }
    
    private void scheduleEmailSending(String email, String verificationCode) {
        TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    emailService.sendVerificationEmail(email, verificationCode);
                } catch (Exception e) {
                    log.error("Failed to send verification email to {} after transaction commit", maskEmail(email), e);
                    // Don't throw - transaction is already committed
                }
            }
        });
    }

    @Override
    public VerificationStatusResponse verifyParent(VerificationVerifyRequest request) {
        log.info("STUB: Verifying parent with verification ID: {}", request.verificationId());
        
        // TODO: Implement actual verification logic
        // - Validate verification ID exists and is not expired
        // - Check verification code matches
        // - Handle attempt counting and lockouts
        // - Update verification status to VERIFIED
        // - Log verification timestamp
        
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        
        return new VerificationStatusResponse(
                request.verificationId(),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), // TODO: Get from database
                VerificationMethod.EMAIL, // TODO: Get from database
                VerificationStatus.VERIFIED,
                1, // TODO: Get actual attempt count
                now.plusMinutes(30), // TODO: Get actual expiry
                now, // verified now
                now.minusMinutes(5) // TODO: Get actual creation time
        );
    }

    @Override
    public VerificationStatusResponse getVerificationStatus(UUID verificationId) {
        log.info("STUB: Getting verification status for ID: {}", verificationId);
        
        // TODO: Implement actual status retrieval
        // - Query ParentVerification by ID
        // - Return current status and metadata
        // - Handle not found cases
        
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        
        return new VerificationStatusResponse(
                verificationId,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), // TODO: Get from database
                VerificationMethod.EMAIL, // TODO: Get from database
                VerificationStatus.PENDING, // TODO: Get actual status
                0, // TODO: Get actual attempt count
                now.plusMinutes(25), // TODO: Get actual expiry
                null, // TODO: Get actual verification time
                now.minusMinutes(5) // TODO: Get actual creation time
        );
    }
} 