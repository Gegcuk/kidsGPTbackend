package uk.gegc.kidsgptbackend.features.consent.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentPoliciesRepository;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ParentVerificationRepository;
import uk.gegc.kidsgptbackend.features.consent.application.impl.ConsentServiceImpl;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentServiceStatusTest {

    @Mock
    private ConsentLedgerRepository consentLedgerRepository;

    @Mock
    private ConsentChildCoverageRepository consentChildCoverageRepository;

    @Mock
    private ParentVerificationRepository parentVerificationRepository;

    @Mock
    private ConsentPoliciesRepository consentPoliciesRepository;

    @Mock
    private Clock clock;

    private ConsentService consentService;

    @BeforeEach
    void setUp() {
        consentService = new ConsentServiceImpl(
                consentLedgerRepository,
                consentChildCoverageRepository,
                parentVerificationRepository,
                consentPoliciesRepository,
                clock
        );
    }

    @AfterEach
    void tearDown() {
        // Reset all mocks to ensure clean state between tests
        reset(consentLedgerRepository, consentChildCoverageRepository, 
              parentVerificationRepository, consentPoliciesRepository, clock);
    }

    @Test
    void invalidVerificationIdFormat_throws400() {
        // Given: verificationId = "bad"
        String invalidVerificationId = "bad";

        // When / Then: service throws ResponseStatusException(400, "Invalid verification ID format")
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> consentService.getConsentStatus(invalidVerificationId));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Invalid verification ID format", exception.getReason());

        // And: no repository calls
        verifyNoInteractions(parentVerificationRepository);
        verifyNoInteractions(consentLedgerRepository);
        verifyNoInteractions(consentChildCoverageRepository);
        verifyNoInteractions(consentPoliciesRepository);
    }

    @Test
    void verificationNotFound_throws404() {
        // Given: parentVerificationRepository.findById(id) returns empty
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        
        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.empty());

        // When / Then: service throws ResponseStatusException(404, "Verification not found")
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> consentService.getConsentStatus(validUuid));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Verification not found", exception.getReason());

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);
        
        // And: no other repository calls
        verifyNoInteractions(consentLedgerRepository);
        verifyNoInteractions(consentChildCoverageRepository);
        verifyNoInteractions(consentPoliciesRepository);
    }

    @Test
    void verificationExpired_throws410() {
        // Given: verification.expiresAt < Instant.now(clock)
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.randomUUID();
        
        // Use fixed times to avoid timing issues
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        java.time.LocalDateTime expiredTime = java.time.LocalDateTime.parse("2024-01-15T10:00:00"); // 2 hours before current time
        
        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification expiredVerification = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED)
                .contactInfoHash(new byte[64])
                .verificationCodeHash(new byte[64])
                .attemptCount(1)
                .expiresAt(expiredTime) // Expired time (2 hours ago)
                .verifiedAt(java.time.LocalDateTime.parse("2024-01-15T09:00:00"))
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T08:00:00"))
                .build();
        
        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(expiredVerification));
        
        // Mock clock to return fixed current time (which is after expiredTime)
        when(clock.instant()).thenReturn(fixedCurrentTime);
        
        // Verify that the expiration check will work
        java.time.LocalDateTime clockTime = fixedCurrentTime.atOffset(java.time.ZoneOffset.UTC).toLocalDateTime();
        assertTrue(expiredTime.isBefore(clockTime), "Expired time should be before current time for the test to work");

        // When / Then: service throws ResponseStatusException(410, "Verification has expired")
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> consentService.getConsentStatus(validUuid));

        assertEquals(HttpStatus.GONE, exception.getStatusCode());
        assertEquals("Verification has expired", exception.getReason());

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);
        
        // And: clock.instant() called (for the time comparison)
        verify(clock).instant();
        
        // And: no other repository calls
        verifyNoInteractions(consentLedgerRepository);
        verifyNoInteractions(consentChildCoverageRepository);
        verifyNoInteractions(consentPoliciesRepository);
    }

    @Test
    void verificationNotCompleted_throws409() {
        // Given: verification.status ∈ {PENDING, FAILED} (anything != VERIFIED)
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.randomUUID();
        
        // Use fixed times to avoid timing issues
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        java.time.LocalDateTime futureTime = java.time.LocalDateTime.parse("2024-01-15T14:00:00"); // 2 hours in future
        
        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification pendingVerification = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.PENDING) // Not VERIFIED
                .contactInfoHash(new byte[64])
                .verificationCodeHash(new byte[64])
                .attemptCount(1)
                .expiresAt(futureTime) // Not expired (2 hours in future)
                .verifiedAt(null) // Not verified yet
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();
        
        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(pendingVerification));
        
        // Mock clock to return fixed current time (which is before expiresAt)
        when(clock.instant()).thenReturn(fixedCurrentTime);

        // When / Then: service throws ResponseStatusException(409, "Verification not completed")
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> consentService.getConsentStatus(validUuid));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Verification not completed", exception.getReason());

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);
        
        // And: clock.instant() called (for the time comparison)
        verify(clock).instant();
        
        // And: no other repository calls
        verifyNoInteractions(consentLedgerRepository);
        verifyNoInteractions(consentChildCoverageRepository);
        verifyNoInteractions(consentPoliciesRepository);
    }

    @Test
    void happyPathNoConsents_returnsReconsentNeededTrue() {
        // Given: buildEffectiveConsentStatus would return [] (mock via repo methods)
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.randomUUID();
        
        // Use fixed times to avoid timing issues
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        java.time.LocalDateTime futureTime = java.time.LocalDateTime.parse("2024-01-15T14:00:00"); // 2 hours in future
        
        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification verifiedVerification = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED) // VERIFIED
                .contactInfoHash(new byte[64])
                .verificationCodeHash(new byte[64])
                .attemptCount(1)
                .expiresAt(futureTime) // Not expired (2 hours in future)
                .verifiedAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00")) // Verified
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();
        
        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(verifiedVerification));
        
        // Mock clock to return fixed current time (which is before expiresAt)
        when(clock.instant()).thenReturn(fixedCurrentTime);
        
        // Mock buildEffectiveConsentStatus to return empty list (no consents)
        // This is done by mocking the repository method to return empty for all consent types
        for (uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType type : uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.values()) {
            when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(parentId, type))
                    .thenReturn(java.util.Optional.empty());
        }
        
        // Mock getMostRecentConsentId to return null (no consents)
        Page emptyPage =
            mock(Page.class);
        when(emptyPage.getContent()).thenReturn(java.util.List.of());
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), 
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(emptyPage);

        // When: service is called
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse result = consentService.getConsentStatus(validUuid);

        // Then: service returns latestByType=[], reconsentNeeded=true, consentId=null
        assertNotNull(result);
        assertTrue(result.latestByType().isEmpty(), "latestByType should be empty");
        assertTrue(result.reconsentNeeded(), "reconsentNeeded should be true");
        assertNull(result.consentId(), "consentId should be null");

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);
        
        // And: clock.instant() called (for the time comparison)
        verify(clock).instant();
        
        // And: consentLedgerRepository methods called for each consent type
        for (uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType type : uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.values()) {
            verify(consentLedgerRepository).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(parentId, type);
        }
        
        // And: getMostRecentConsentId repository method called
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), 
                any(org.springframework.data.domain.Pageable.class));
        
        // And: no policy repository calls (no lookup of policies is required to decide truthy)
        verifyNoInteractions(consentPoliciesRepository);
        verifyNoInteractions(consentChildCoverageRepository);
    }

    @Test
    void happyPathWithdrawnInAnyType_returnsReconsentNeededTrue() {
        // Given: latest entry for one type has status=WITHDRAWN
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.randomUUID();
        
        // Use fixed times to avoid timing issues
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        java.time.LocalDateTime futureTime = java.time.LocalDateTime.parse("2024-01-15T14:00:00"); // 2 hours in future
        
        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification verifiedVerification = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED) // VERIFIED
                .contactInfoHash(new byte[64])
                .verificationCodeHash(new byte[64])
                .attemptCount(1)
                .expiresAt(futureTime) // Not expired (2 hours in future)
                .verifiedAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00")) // Verified
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();
        
        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(verifiedVerification));
        
        // Mock clock to return fixed current time (which is before expiresAt)
        when(clock.instant()).thenReturn(fixedCurrentTime);
        
        // Mock buildEffectiveConsentStatus to return consents with one WITHDRAWN
        // Create a WITHDRAWN consent for PRIVACY_POLICY
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger withdrawnConsent = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.randomUUID())
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.WITHDRAWN) // WITHDRAWN status
                .policyUrl("https://kidsgpt.club/policies/privacy")
                .contentHash("abc123")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONSENT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2031-01-15T10:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();
        
        // Create a GRANTED consent for TERMS_OF_SERVICE (to show it doesn't matter)
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger grantedConsent = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.randomUUID())
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)
                .consentVersion("2.0.0")
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED) // GRANTED status
                .policyUrl("https://kidsgpt.club/policies/terms")
                .contentHash("def456")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONTRACT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(java.time.LocalDateTime.parse("2024-01-15T11:00:00"))
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2030-01-15T11:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00"))
                .build();
        
        // Mock repository to return the WITHDRAWN consent for PRIVACY_POLICY
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY))
                .thenReturn(java.util.Optional.of(withdrawnConsent));
        
        // Mock repository to return the GRANTED consent for TERMS_OF_SERVICE
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE))
                .thenReturn(java.util.Optional.of(grantedConsent));
        
        // Mock repository to return empty for other consent types
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PARENTAL_CONSENT))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.DATA_PROCESSING))
                .thenReturn(java.util.Optional.empty());
        
        // Mock getMostRecentConsentId to return the WITHDRAWN consent ID
        Page page =
            mock(Page.class);
        when(page.getContent()).thenReturn(java.util.List.of(withdrawnConsent, grantedConsent));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), 
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        // When: service is called
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse result = consentService.getConsentStatus(validUuid);

        // Then: reconsentNeeded=true regardless of policy versions
        assertNotNull(result);
        assertTrue(result.reconsentNeeded(), "reconsentNeeded should be true when any consent type has WITHDRAWN status");
        assertEquals(2, result.latestByType().size(), "Should have 2 consent types");
        assertEquals(withdrawnConsent.getConsentId(), result.consentId(), "consentId should be the most recent consent ID");

        // Verify the WITHDRAWN consent is in the response
        boolean hasWithdrawnConsent = result.latestByType().stream()
                .anyMatch(consent -> consent.status() == uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.WITHDRAWN);
        assertTrue(hasWithdrawnConsent, "Response should contain the WITHDRAWN consent");

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);
        
        // And: clock.instant() called (for the time comparison)
        verify(clock).instant();
        
        // And: consentLedgerRepository methods called for each consent type
        for (uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType type : uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.values()) {
            verify(consentLedgerRepository).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(parentId, type);
        }
        
        // And: getMostRecentConsentId repository method called
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), 
                any(org.springframework.data.domain.Pageable.class));
        
        // And: no policy repository calls (reconsentNeeded=true regardless of policy versions)
        verifyNoInteractions(consentPoliciesRepository);
        verifyNoInteractions(consentChildCoverageRepository);
    }

    @Test
    void versionIsNull_returnsReconsentNeededTrue() {
        // Given: one entry has version=null
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.randomUUID();
        
        // Use fixed times to avoid timing issues
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        java.time.LocalDateTime futureTime = java.time.LocalDateTime.parse("2024-01-15T14:00:00"); // 2 hours in future
        
        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification verifiedVerification = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED) // VERIFIED
                .contactInfoHash(new byte[64])
                .verificationCodeHash(new byte[64])
                .attemptCount(1)
                .expiresAt(futureTime) // Not expired (2 hours in future)
                .verifiedAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00")) // Verified
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();
        
        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(verifiedVerification));
        
        // Mock clock to return fixed current time (which is before expiresAt)
        when(clock.instant()).thenReturn(fixedCurrentTime);
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);
        
        // Create a consent with null version for PRIVACY_POLICY
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger nullVersionConsent = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.randomUUID())
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .consentVersion(null) // NULL version - this is the key test condition
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .policyUrl("https://kidsgpt.club/policies/privacy")
                .contentHash("abc123")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONSENT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2031-01-15T10:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();
        
        // Create a normal consent for TERMS_OF_SERVICE (to show it doesn't matter)
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger normalConsent = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.randomUUID())
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)
                .consentVersion("2.0.0") // Normal version
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .policyUrl("https://kidsgpt.club/policies/terms")
                .contentHash("def456")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONTRACT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(java.time.LocalDateTime.parse("2024-01-15T11:00:00"))
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2030-01-15T11:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00"))
                .build();
        
        // Mock repository to return the null version consent for PRIVACY_POLICY
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY))
                .thenReturn(java.util.Optional.of(nullVersionConsent));
        
        // Mock repository to return the normal consent for TERMS_OF_SERVICE
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE))
                .thenReturn(java.util.Optional.of(normalConsent));
        
        // Mock repository to return empty for other consent types
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PARENTAL_CONSENT))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.DATA_PROCESSING))
                .thenReturn(java.util.Optional.empty());
        
        // And: there exists an active policy for that type (any version)
        // Create an active policy for PRIVACY_POLICY
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies activePolicy = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies.builder()
                .policyId(java.util.UUID.randomUUID())
                .policyType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .version("1.5.0") // Any version
                .effectiveDate(java.time.LocalDate.parse("2024-01-01"))
                .contentHash("policy123")
                .policyUrl("https://kidsgpt.club/policies/privacy")
                .locale("en-GB")
                .isActive(true) // Active policy
                .createdAt(java.time.LocalDateTime.parse("2024-01-01T00:00:00"))
                .build();
        
        // Mock policy repository to return the active policy
        when(consentPoliciesRepository.findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                any(java.time.LocalDate.class)))
                .thenReturn(java.util.List.of(activePolicy));
        
        // Mock getMostRecentConsentId to return the null version consent ID
        Page page =
            mock(Page.class);
        when(page.getContent()).thenReturn(java.util.List.of(nullVersionConsent, normalConsent));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), 
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        // When: service is called
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse result = consentService.getConsentStatus(validUuid);

        // Then: true (treated as outdated)
        assertNotNull(result);
        assertTrue(result.reconsentNeeded(), "reconsentNeeded should be true when consent version is null (treated as outdated)");
        assertEquals(2, result.latestByType().size(), "Should have 2 consent types");
        assertEquals(nullVersionConsent.getConsentId(), result.consentId(), "consentId should be the most recent consent ID");

        // Verify the null version consent is in the response
        boolean hasNullVersionConsent = result.latestByType().stream()
                .anyMatch(consent -> consent.type() == uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY);
        assertTrue(hasNullVersionConsent, "Response should contain the PRIVACY_POLICY consent");

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);
        
        // And: clock.instant() called (for the time comparison and policy date check)
        verify(clock, times(2)).instant();
        
        // And: consentLedgerRepository methods called for each consent type
        for (uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType type : uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.values()) {
            verify(consentLedgerRepository).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(parentId, type);
        }
        
        // And: getMostRecentConsentId repository method called
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), 
                any(org.springframework.data.domain.Pageable.class));
        
        // And: policy repository called for the null version consent type
        verify(consentPoliciesRepository).findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                any(java.time.LocalDate.class));
        
        // And: no child coverage repository calls
        verifyNoInteractions(consentChildCoverageRepository);
    }

    @Test
    void latestPolicyByLocaleUrlHasEnGb_noReconsentForThatType() {
        // Given: policyUrl includes a path segment en-GB
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.randomUUID();
        
        // Use fixed times to avoid timing issues
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        java.time.LocalDateTime futureTime = java.time.LocalDateTime.parse("2024-01-15T14:00:00"); // 2 hours in future
        
        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification verifiedVerification = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED) // VERIFIED
                .contactInfoHash(new byte[64])
                .verificationCodeHash(new byte[64])
                .attemptCount(1)
                .expiresAt(futureTime) // Not expired (2 hours in future)
                .verifiedAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00")) // Verified
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();
        
        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(verifiedVerification));
        
        // Mock clock to return fixed current time (which is before expiresAt)
        when(clock.instant()).thenReturn(fixedCurrentTime);
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);
        
        // Create a consent with en-GB locale URL and matching version for PRIVACY_POLICY
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger enGbConsent = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.randomUUID())
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .consentVersion("1.2.3") // Matching version - this is the key test condition
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .policyUrl("https://kidsgpt.club/policies/privacy/en-GB") // URL includes en-GB locale
                .contentHash("abc123")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONSENT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2031-01-15T10:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();
        
        // Create a consent with outdated version for TERMS_OF_SERVICE (to show reconsent is needed)
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger outdatedConsent = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.randomUUID())
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.0.0") // Outdated version
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .policyUrl("https://kidsgpt.club/policies/terms")
                .contentHash("def456")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONTRACT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(java.time.LocalDateTime.parse("2024-01-15T11:00:00"))
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2030-01-15T11:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00"))
                .build();
        
        // Mock repository to return the en-GB consent for PRIVACY_POLICY
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY))
                .thenReturn(java.util.Optional.of(enGbConsent));
        
        // Mock repository to return the outdated consent for TERMS_OF_SERVICE
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE))
                .thenReturn(java.util.Optional.of(outdatedConsent));
        
        // Mock repository to return empty for other consent types
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PARENTAL_CONSENT))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.DATA_PROCESSING))
                .thenReturn(java.util.Optional.empty());
        
        // And: consentPoliciesRepository.findActivePoliciesByTypeLocaleAndDate(type, "en-GB", today) returns item with version="1.2.3"
        // Create an active policy for PRIVACY_POLICY with en-GB locale
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies enGbPolicy = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies.builder()
                .policyId(java.util.UUID.randomUUID())
                .policyType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .version("1.2.3") // Matching version
                .effectiveDate(java.time.LocalDate.parse("2024-01-01"))
                .contentHash("policy123")
                .policyUrl("https://kidsgpt.club/policies/privacy/en-GB")
                .locale("en-GB") // en-GB locale
                .isActive(true) // Active policy
                .createdAt(java.time.LocalDateTime.parse("2024-01-01T00:00:00"))
                .build();
        
        // Mock locale-aware policy repository to return the en-GB policy
        when(consentPoliciesRepository.findActivePoliciesByTypeLocaleAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                eq("en-GB"),
                any(java.time.LocalDate.class)))
                .thenReturn(java.util.List.of(enGbPolicy));
        
        // Mock non-locale policy repository to return a different version for TERMS_OF_SERVICE
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies termsPolicy = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies.builder()
                .policyId(java.util.UUID.randomUUID())
                .policyType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)
                .version("2.0.0") // Newer version (outdated consent is 1.0.0)
                .effectiveDate(java.time.LocalDate.parse("2024-01-01"))
                .contentHash("policy456")
                .policyUrl("https://kidsgpt.club/policies/terms")
                .locale(null) // No locale
                .isActive(true) // Active policy
                .createdAt(java.time.LocalDateTime.parse("2024-01-01T00:00:00"))
                .build();
        
        when(consentPoliciesRepository.findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE),
                any(java.time.LocalDate.class)))
                .thenReturn(java.util.List.of(termsPolicy));
        
        // Mock getMostRecentConsentId to return the en-GB consent ID
        org.springframework.data.domain.Page<uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger> page = 
            org.mockito.Mockito.mock(org.springframework.data.domain.Page.class);
        when(page.getContent()).thenReturn(java.util.List.of(enGbConsent, outdatedConsent));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), 
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        // When: service is called
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse result = consentService.getConsentStatus(validUuid);

        // Then: no reconsent for that type (PRIVACY_POLICY should not need reconsent, but TERMS_OF_SERVICE should)
        assertNotNull(result);
        assertTrue(result.reconsentNeeded(), "reconsentNeeded should be true because TERMS_OF_SERVICE is outdated");
        assertEquals(2, result.latestByType().size(), "Should have 2 consent types");
        assertEquals(enGbConsent.getConsentId(), result.consentId(), "consentId should be the most recent consent ID");

        // Verify the en-GB consent is in the response
        boolean hasEnGbConsent = result.latestByType().stream()
                .anyMatch(consent -> consent.type() == uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY);
        assertTrue(hasEnGbConsent, "Response should contain the PRIVACY_POLICY consent");

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);
        
        // And: clock.instant() called (for the time comparison and policy date check)
        verify(clock, times(3)).instant();
        
        // And: consentLedgerRepository methods called for each consent type
        for (uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType type : uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.values()) {
            verify(consentLedgerRepository).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(parentId, type);
        }
        
        // And: getMostRecentConsentId repository method called
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), 
                any(org.springframework.data.domain.Pageable.class));
        
        // And: locale-aware policy repository called for the en-GB consent type
        verify(consentPoliciesRepository).findActivePoliciesByTypeLocaleAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                eq("en-GB"),
                any(java.time.LocalDate.class));
        
        // And: non-locale policy repository called for the TERMS_OF_SERVICE consent type
        verify(consentPoliciesRepository).findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE),
                any(java.time.LocalDate.class));
        
        // And: no child coverage repository calls
        verifyNoInteractions(consentChildCoverageRepository);
    }
    
    @Test
    void latestPolicyWithoutLocaleNoLocaleDerivable_returnsReconsentNeededTrue() {
        // Given: policyUrl=null or has no locale segment
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.randomUUID();
        
        // Use fixed times to avoid timing issues
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        java.time.LocalDateTime futureTime = java.time.LocalDateTime.parse("2024-01-15T14:00:00"); // 2 hours in future
        
        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification verifiedVerification = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED) // VERIFIED
                .contactInfoHash(new byte[64])
                .verificationCodeHash(new byte[64])
                .attemptCount(1)
                .expiresAt(futureTime) // Not expired (2 hours in future)
                .verifiedAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00")) // Verified
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();
        
        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(verifiedVerification));
        
        // Mock clock to return fixed current time (which is before expiresAt)
        when(clock.instant()).thenReturn(fixedCurrentTime);
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);
        
        // Create a consent with no locale URL and outdated version for PRIVACY_POLICY
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger noLocaleConsent = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.randomUUID())
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .consentVersion("1.9.9") // Outdated version - this is the key test condition
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .policyUrl("https://kidsgpt.club/policies/privacy") // URL has no locale segment
                .contentHash("abc123")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONSENT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2031-01-15T10:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();
        
        // Create a consent with null policyUrl for TERMS_OF_SERVICE (to show null handling)
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger nullUrlConsent = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.randomUUID())
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.0.0") // Outdated version
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .policyUrl(null) // null policyUrl
                .contentHash("def456")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONTRACT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(java.time.LocalDateTime.parse("2024-01-15T11:00:00"))
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2030-01-15T11:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00"))
                .build();
        
        // Mock repository to return the no-locale consent for PRIVACY_POLICY
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY))
                .thenReturn(java.util.Optional.of(noLocaleConsent));
        
        // Mock repository to return the null-url consent for TERMS_OF_SERVICE
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE))
                .thenReturn(java.util.Optional.of(nullUrlConsent));
        
        // Mock repository to return empty for other consent types
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PARENTAL_CONSENT))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.DATA_PROCESSING))
                .thenReturn(java.util.Optional.empty());
        
        // And: findActivePoliciesByTypeAndDate(type, today) returns "2.0.0" for PRIVACY_POLICY
        // Create an active policy for PRIVACY_POLICY without locale
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies privacyPolicy = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies.builder()
                .policyId(java.util.UUID.randomUUID())
                .policyType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .version("2.0.0") // Newer version (consent is 1.9.9)
                .effectiveDate(java.time.LocalDate.parse("2024-01-01"))
                .contentHash("policy123")
                .policyUrl("https://kidsgpt.club/policies/privacy")
                .locale(null) // No locale
                .isActive(true) // Active policy
                .createdAt(java.time.LocalDateTime.parse("2024-01-01T00:00:00"))
                .build();
        
        // Mock non-locale policy repository to return the newer version for PRIVACY_POLICY
        when(consentPoliciesRepository.findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                any(java.time.LocalDate.class)))
                .thenReturn(java.util.List.of(privacyPolicy));
        
        // Mock non-locale policy repository to return a different version for TERMS_OF_SERVICE
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies termsPolicy = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies.builder()
                .policyId(java.util.UUID.randomUUID())
                .policyType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)
                .version("2.0.0") // Newer version (outdated consent is 1.0.0)
                .effectiveDate(java.time.LocalDate.parse("2024-01-01"))
                .contentHash("policy456")
                .policyUrl("https://kidsgpt.club/policies/terms")
                .locale(null) // No locale
                .isActive(true) // Active policy
                .createdAt(java.time.LocalDateTime.parse("2024-01-01T00:00:00"))
                .build();
        
        when(consentPoliciesRepository.findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE),
                any(java.time.LocalDate.class)))
                .thenReturn(java.util.List.of(termsPolicy));
        
        // Mock getMostRecentConsentId to return the no-locale consent ID
        org.springframework.data.domain.Page<uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger> page = 
            org.mockito.Mockito.mock(org.springframework.data.domain.Page.class);
        when(page.getContent()).thenReturn(java.util.List.of(noLocaleConsent, nullUrlConsent));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), 
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        // When: service is called
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse result = consentService.getConsentStatus(validUuid);

        // Then: reconsentNeeded=true because both consents are outdated
        assertNotNull(result);
        assertTrue(result.reconsentNeeded(), "reconsentNeeded should be true because both consents are outdated");
        assertEquals(2, result.latestByType().size(), "Should have 2 consent types");
        assertEquals(noLocaleConsent.getConsentId(), result.consentId(), "consentId should be the most recent consent ID");

        // Verify both consents are in the response
        boolean hasPrivacyConsent = result.latestByType().stream()
                .anyMatch(consent -> consent.type() == uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY);
        assertTrue(hasPrivacyConsent, "Response should contain the PRIVACY_POLICY consent");
        
        boolean hasTermsConsent = result.latestByType().stream()
                .anyMatch(consent -> consent.type() == uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE);
        assertTrue(hasTermsConsent, "Response should contain the TERMS_OF_SERVICE consent");

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);
        
        // And: clock.instant() called (for the time comparison and policy date check)
        verify(clock, times(2)).instant();
        
        // And: consentLedgerRepository methods called for each consent type
        for (uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType type : uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.values()) {
            verify(consentLedgerRepository).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(parentId, type);
        }
        
        // And: getMostRecentConsentId repository method called
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), 
                any(org.springframework.data.domain.Pageable.class));
        
        // And: non-locale policy repository called for the first consent type only (early return after first outdated consent)
        verify(consentPoliciesRepository).findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                any(java.time.LocalDate.class));
        verify(consentPoliciesRepository, never()).findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE),
                any(java.time.LocalDate.class));
        
        // And: no locale-aware policy repository calls (since no locale derivable)
        verify(consentPoliciesRepository, never()).findActivePoliciesByTypeLocaleAndDate(
                any(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.class),
                any(String.class),
                any(java.time.LocalDate.class));
        
        // And: no child coverage repository calls
        verifyNoInteractions(consentChildCoverageRepository);
    }

    @Test
    void noActivePolicyAvailable_treatsAsNotOutdated() {
        // Given: repo returns empty for the type/locale/date
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.randomUUID();

        // Use fixed times to avoid timing issues
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        java.time.LocalDateTime futureTime = java.time.LocalDateTime.parse("2024-01-15T14:00:00"); // 2 hours in future

        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification verifiedVerification =
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED) // VERIFIED
                .contactInfoHash(new byte[64])
                .verificationCodeHash(new byte[64])
                .attemptCount(1)
                .expiresAt(futureTime) // Not expired (2 hours in future)
                .verifiedAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00")) // Verified
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();

        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(verifiedVerification));

        // Mock clock to return fixed current time (which is before expiresAt)
        when(clock.instant()).thenReturn(fixedCurrentTime);
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);

        // Create a consent with an old version for PRIVACY_POLICY
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger oldVersionConsent =
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.randomUUID())
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0") // Old version
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .policyUrl("https://kidsgpt.club/policies/privacy")
                .contentHash("abc123")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONSENT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2031-01-15T10:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();

        // Mock repository to return the old version consent for PRIVACY_POLICY
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY))
                .thenReturn(java.util.Optional.of(oldVersionConsent));

        // Mock repository to return empty for other consent types
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PARENTAL_CONSENT))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.DATA_PROCESSING))
                .thenReturn(java.util.Optional.empty());

        // And: repo returns empty for the type/locale/date (no active policy available)
        when(consentPoliciesRepository.findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                any(java.time.LocalDate.class)))
                .thenReturn(java.util.List.of()); // Empty list - no active policy

        // Mock getMostRecentConsentId to return the old version consent ID
        org.springframework.data.domain.Page<uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger> page =
            org.mockito.Mockito.mock(org.springframework.data.domain.Page.class);
        when(page.getContent()).thenReturn(java.util.List.of(oldVersionConsent));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        // When: service is called
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse result = consentService.getConsentStatus(validUuid);

        // Then: reconsentNeeded=false because no active policy means not outdated
        assertNotNull(result);
        assertFalse(result.reconsentNeeded(), "reconsentNeeded should be false because no active policy means not outdated");
        assertEquals(1, result.latestByType().size(), "Should have 1 consent type");
        assertEquals(oldVersionConsent.getConsentId(), result.consentId(), "consentId should be the most recent consent ID");

        // Verify the consent is in the response
        boolean hasPrivacyConsent = result.latestByType().stream()
                .anyMatch(consent -> consent.type() == uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY);
        assertTrue(hasPrivacyConsent, "Response should contain the PRIVACY_POLICY consent");

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);

        // And: clock.instant() called (for the time comparison and policy date check)
        verify(clock, times(2)).instant(); // Once for time comparison, once for policy date check

        // And: consentLedgerRepository methods called for each consent type
        for (uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType type : uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.values()) {
            verify(consentLedgerRepository).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(parentId, type);
        }

        // And: getMostRecentConsentId repository method called
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId),
                any(org.springframework.data.domain.Pageable.class));

        // And: non-locale policy repository called for PRIVACY_POLICY
        verify(consentPoliciesRepository).findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                any(java.time.LocalDate.class));

        // And: no locale-aware policy repository calls (since no locale derivable)
        verify(consentPoliciesRepository, never()).findActivePoliciesByTypeLocaleAndDate(
                any(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.class),
                any(String.class),
                any(java.time.LocalDate.class));

        // And: no child coverage repository calls
        verifyNoInteractions(consentChildCoverageRepository);
    }

    @Test
    void mostRecentConsentIdSelection_ordering() {
        // Given: repo findByUserIdOrderByConsentTimestampDescCreatedAtDesc(parentId, PageRequest.of(0,1)) returns a single row
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.randomUUID();

        // Use fixed times to avoid timing issues
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        java.time.LocalDateTime futureTime = java.time.LocalDateTime.parse("2024-01-15T14:00:00"); // 2 hours in future

        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification verifiedVerification =
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED) // VERIFIED
                .contactInfoHash(new byte[64])
                .verificationCodeHash(new byte[64])
                .attemptCount(1)
                .expiresAt(futureTime) // Not expired (2 hours in future)
                .verifiedAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00")) // Verified
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();

        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(verifiedVerification));

        // Mock clock to return fixed current time (which is before expiresAt)
        when(clock.instant()).thenReturn(fixedCurrentTime);
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);
        
        // Create a single consent for PRIVACY_POLICY
        java.util.UUID singleConsentId = java.util.UUID.randomUUID();
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger singleConsent =
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(singleConsentId)
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .policyUrl("https://kidsgpt.club/policies/privacy")
                .contentHash("abc123")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONSENT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2031-01-15T10:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();

        // Mock repository to return the single consent for PRIVACY_POLICY
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY))
                .thenReturn(java.util.Optional.of(singleConsent));

        // Mock repository to return empty for other consent types
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PARENTAL_CONSENT))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.DATA_PROCESSING))
                .thenReturn(java.util.Optional.empty());

        // Mock getMostRecentConsentId to return the single consent ID
        org.springframework.data.domain.Page<uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger> singlePage =
            org.mockito.Mockito.mock(org.springframework.data.domain.Page.class);
        when(singlePage.getContent()).thenReturn(java.util.List.of(singleConsent));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(singlePage);

        // When: service is called
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse result = consentService.getConsentStatus(validUuid);

        // Then: returned consentId equals that row's id
        assertNotNull(result);
        assertEquals(singleConsentId, result.consentId(), "consentId should equal the single row's id");

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);

        // And: getMostRecentConsentId repository method called once
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId),
                any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void mostRecentConsentIdSelection_orderingWithSameTimestampDifferentCreatedAt() {
        // Given: two rows having same consentTimestamp but different createdAt
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.randomUUID();

        // Use fixed times to avoid timing issues
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        java.time.LocalDateTime futureTime = java.time.LocalDateTime.parse("2024-01-15T14:00:00"); // 2 hours in future

        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification verifiedVerification =
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED) // VERIFIED
                .contactInfoHash(new byte[64])
                .verificationCodeHash(new byte[64])
                .attemptCount(1)
                .expiresAt(futureTime) // Not expired (2 hours in future)
                .verifiedAt(java.time.LocalDateTime.parse("2024-01-15T11:00:00")) // Verified
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .createdAt(java.time.LocalDateTime.parse("2024-01-15T10:00:00"))
                .build();

        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(verifiedVerification));

        // Mock clock to return fixed current time (which is before expiresAt)
        when(clock.instant()).thenReturn(fixedCurrentTime);
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);

        // Create two consents with same consentTimestamp but different createdAt
        java.util.UUID earlierConsentId = java.util.UUID.randomUUID();
        java.util.UUID laterConsentId = java.util.UUID.randomUUID();
        
        // Same consentTimestamp but different createdAt (later createdAt should come first)
        java.time.LocalDateTime sameConsentTimestamp = java.time.LocalDateTime.parse("2024-01-15T10:00:00");
        java.time.LocalDateTime earlierCreatedAt = java.time.LocalDateTime.parse("2024-01-15T09:00:00");
        java.time.LocalDateTime laterCreatedAt = java.time.LocalDateTime.parse("2024-01-15T11:00:00");

        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger earlierConsent =
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(earlierConsentId)
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .policyUrl("https://kidsgpt.club/policies/privacy")
                .contentHash("abc123")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONSENT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(sameConsentTimestamp)
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2031-01-15T10:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(earlierCreatedAt) // Earlier createdAt
                .build();

        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger laterConsent =
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(laterConsentId)
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.0.0")
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .policyUrl("https://kidsgpt.club/policies/terms")
                .contentHash("def456")
                .jurisdiction("GB")
                .region("UK")
                .locale("en-GB")
                .lawfulBasis(uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis.CONTRACT)
                .source(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource.WEB)
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .consentTimestamp(sameConsentTimestamp) // Same consentTimestamp
                .parentVerificationId(verificationUuid)
                .retentionExpiresAt(java.time.LocalDateTime.parse("2030-01-15T10:00:00"))
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .withdrawnConsentId(null)
                .createdAt(laterCreatedAt) // Later createdAt (should come first in ordering)
                .build();

        // Mock repository to return the consents for their respective types
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY))
                .thenReturn(java.util.Optional.of(earlierConsent));
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE))
                .thenReturn(java.util.Optional.of(laterConsent));

        // Mock repository to return empty for other consent types
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PARENTAL_CONSENT))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                parentId, uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.DATA_PROCESSING))
                .thenReturn(java.util.Optional.empty());

        // Mock getMostRecentConsentId to return the later consent ID (due to ordering by createdAt DESC)
        // The repository ordering should yield the later createdAt first
        org.springframework.data.domain.Page<uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger> orderingPage =
            org.mockito.Mockito.mock(org.springframework.data.domain.Page.class);
        when(orderingPage.getContent()).thenReturn(java.util.List.of(laterConsent, earlierConsent)); // Later createdAt first
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(orderingPage);

        // When: service is called
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse result = consentService.getConsentStatus(validUuid);

        // Then: returned consentId equals the later createdAt row's id
        assertNotNull(result);
        assertEquals(laterConsentId, result.consentId(), "consentId should be the later createdAt row's id");

        // And: parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);

        // And: getMostRecentConsentId repository method called once
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId),
                any(org.springframework.data.domain.Pageable.class));

        // And: consentLedgerRepository methods called for each consent type
        for (uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType type : uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.values()) {
            verify(consentLedgerRepository).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(parentId, type);
        }
    }

    @Test
    void repositoryInteractions_verifyCorrectCalls() {
        // Given: a valid verification with multiple consent types
        String validUuid = "550e8400-e29b-41d4-a716-446655440000";
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        
        // Mock verification
        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification verification = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED)
                .expiresAt(java.time.LocalDateTime.now().plusDays(1))
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .build();
        
        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(verification));
        
        // Mock clock
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        when(clock.instant()).thenReturn(fixedCurrentTime);
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);
        
        // Mock consent ledger calls for buildEffectiveConsentStatus
        // For each consent type, we need to mock the findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc call
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger privacyConsent = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .consentTimestamp(java.time.LocalDateTime.now())
                .policyUrl("https://kidsgpt.club/privacy/en-GB")
                .build();
        
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger termsConsent = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440003"))
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)
                .consentVersion("2.0.0")
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .consentTimestamp(java.time.LocalDateTime.now())
                .policyUrl("https://kidsgpt.club/terms")
                .build();
        
        // Mock the findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc calls
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)))
                .thenReturn(java.util.Optional.of(privacyConsent));
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)))
                .thenReturn(java.util.Optional.of(termsConsent));
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PARENTAL_CONSENT)))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.DATA_PROCESSING)))
                .thenReturn(java.util.Optional.empty());
        
        // Mock policy repository calls for isOutdatedAgainstActivePolicy
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies privacyPolicy = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies.builder()
                .version("1.0.0")
                .policyType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .isActive(true)
                .effectiveDate(java.time.LocalDate.of(2024, 1, 1))
                .build();
        
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies termsPolicy = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies.builder()
                .version("2.0.0")
                .policyType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)
                .isActive(true)
                .effectiveDate(java.time.LocalDate.of(2024, 1, 1))
                .build();
        
        // Mock locale-aware call for privacy policy (has en-GB in URL)
        when(consentPoliciesRepository.findActivePoliciesByTypeLocaleAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                eq("en-GB"),
                any(java.time.LocalDate.class)))
                .thenReturn(java.util.List.of(privacyPolicy));
        
        // Mock non-locale call for terms policy (no locale in URL)
        when(consentPoliciesRepository.findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE),
                any(java.time.LocalDate.class)))
                .thenReturn(java.util.List.of(termsPolicy));
        
        // Mock getMostRecentConsentId call
        org.springframework.data.domain.Page<uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger> consentPage = 
            mock(org.springframework.data.domain.Page.class);
        when(consentPage.getContent()).thenReturn(java.util.List.of(privacyConsent));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), any(org.springframework.data.domain.PageRequest.class)))
                .thenReturn(consentPage);
        
        // When: getConsentStatus is called
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse result = consentService.getConsentStatus(validUuid);
        
        // Then: verify all repository interactions
        assertNotNull(result);
        
        // Verify parentVerificationRepository.findById() called exactly once
        verify(parentVerificationRepository, times(1)).findById(verificationUuid);
        verifyNoMoreInteractions(parentVerificationRepository);
        
        // Verify consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc called for each consent type
        verify(consentLedgerRepository, times(1)).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY));
        verify(consentLedgerRepository, times(1)).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE));
        verify(consentLedgerRepository, times(1)).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PARENTAL_CONSENT));
        verify(consentLedgerRepository, times(1)).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.DATA_PROCESSING));
        
        // Verify either locale-aware or non-locale policy lookup is used per entry
        // Privacy policy should use locale-aware lookup (en-GB)
        verify(consentPoliciesRepository, times(1)).findActivePoliciesByTypeLocaleAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                eq("en-GB"),
                any(java.time.LocalDate.class));
        
        // Terms policy should use non-locale lookup (no locale in URL)
        verify(consentPoliciesRepository, times(1)).findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE),
                any(java.time.LocalDate.class));
        
        // Verify consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc called once
        verify(consentLedgerRepository, times(1)).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), any(org.springframework.data.domain.PageRequest.class));
        
        // Verify no other repository interactions
        verifyNoInteractions(consentChildCoverageRepository);
    }

    @Test
    void nullSafetyAndDefaults_malformedPolicyUrlFallsBackToNonLocaleLookup() {
        // Given: policyUrl malformed or unexpected
        String validUuid = "550e8400-e29b-41d4-a716-446655440000";
        java.util.UUID verificationUuid = java.util.UUID.fromString(validUuid);
        java.util.UUID parentId = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        
        // Mock verification
        uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification verification = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus.VERIFIED)
                .expiresAt(java.time.LocalDateTime.now().plusDays(1))
                .verificationMethod(uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod.EMAIL)
                .build();
        
        when(parentVerificationRepository.findById(verificationUuid))
                .thenReturn(java.util.Optional.of(verification));
        
        // Mock clock
        java.time.Instant fixedCurrentTime = java.time.Instant.parse("2024-01-15T12:00:00Z");
        when(clock.instant()).thenReturn(fixedCurrentTime);
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);
        
        // Mock consent with malformed policyUrl (contains invalid characters that could cause issues)
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger consentWithMalformedUrl = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger.builder()
                .consentId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
                .userId(parentId)
                .consentType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED)
                .consentTimestamp(java.time.LocalDateTime.now())
                .policyUrl("https://kidsgpt.club/privacy/invalid@#$%^&*()") // Malformed URL with special characters
                .build();
        
        // Mock the findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc calls
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)))
                .thenReturn(java.util.Optional.of(consentWithMalformedUrl));
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE)))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PARENTAL_CONSENT)))
                .thenReturn(java.util.Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.DATA_PROCESSING)))
                .thenReturn(java.util.Optional.empty());
        
        // Mock policy repository - should use non-locale lookup since deriveLocaleFromPolicyUrl returns null
        uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies policy = 
            uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies.builder()
                .version("2.0.0") // Newer version than consent
                .policyType(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY)
                .isActive(true)
                .effectiveDate(java.time.LocalDate.of(2024, 1, 1))
                .build();
        
        // Mock non-locale call (should be used since deriveLocaleFromPolicyUrl returns null)
        when(consentPoliciesRepository.findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                any(java.time.LocalDate.class)))
                .thenReturn(java.util.List.of(policy));
        
        // Mock getMostRecentConsentId call
        org.springframework.data.domain.Page<uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger> consentPage = 
            mock(org.springframework.data.domain.Page.class);
        when(consentPage.getContent()).thenReturn(java.util.List.of(consentWithMalformedUrl));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                eq(parentId), any(org.springframework.data.domain.PageRequest.class)))
                .thenReturn(consentPage);
        
        // When: getConsentStatus is called
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse result = consentService.getConsentStatus(validUuid);
        
        // Then: deriveLocaleFromPolicyUrl returns null and code falls back to non-locale lookup without exception
        assertNotNull(result);
        assertTrue(result.reconsentNeeded(), "reconsentNeeded should be true because consent version is outdated");
        
        // Verify: non-locale policy lookup is used (not locale-aware)
        verify(consentPoliciesRepository, times(1)).findActivePoliciesByTypeAndDate(
                eq(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY),
                any(java.time.LocalDate.class));
        
        // Verify: locale-aware lookup is NOT used
        verify(consentPoliciesRepository, never()).findActivePoliciesByTypeLocaleAndDate(
                any(uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.class),
                any(String.class),
                any(java.time.LocalDate.class));
        
        // Verify: no exceptions were thrown during processing
        // (The test would have failed with an exception if malformed URL caused issues)
    }
} 