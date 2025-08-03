package uk.gegc.kidsgptbackend.service.consent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.repository.consent.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentPoliciesRepository;
import uk.gegc.kidsgptbackend.repository.consent.ParentVerificationRepository;
import uk.gegc.kidsgptbackend.service.consent.impl.ConsentServiceImpl;

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
        
        uk.gegc.kidsgptbackend.model.consent.ParentVerification expiredVerification = 
            uk.gegc.kidsgptbackend.model.consent.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.model.consent.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.model.consent.VerificationStatus.VERIFIED)
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
        
        uk.gegc.kidsgptbackend.model.consent.ParentVerification pendingVerification = 
            uk.gegc.kidsgptbackend.model.consent.ParentVerification.builder()
                .verificationId(verificationUuid)
                .parentId(parentId)
                .verificationMethod(uk.gegc.kidsgptbackend.model.consent.VerificationMethod.EMAIL)
                .verificationStatus(uk.gegc.kidsgptbackend.model.consent.VerificationStatus.PENDING) // Not VERIFIED
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
} 