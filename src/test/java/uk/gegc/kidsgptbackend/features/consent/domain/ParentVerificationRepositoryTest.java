package uk.gegc.kidsgptbackend.features.consent.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gegc.kidsgptbackend.features.consent.domain.model.*;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ParentVerificationRepository;
import uk.gegc.kidsgptbackend.test.BaseRepositoryTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ParentVerification Repository Tests")
class ParentVerificationRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private ParentVerificationRepository parentVerificationRepository;

    private UUID testParentId;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        testParentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("findById: should persist and retrieve ParentVerification correctly")
    void findById_shouldPersistAndRetrieveCorrectly() {
        // Given
        ParentVerification verification = ParentVerification.builder()
                .parentId(testParentId)
                .verificationMethod(VerificationMethod.EMAIL)
                .verificationStatus(VerificationStatus.VERIFIED)
                .contactInfoHash("test@example.com".getBytes())
                .verificationCodeHash("123456".getBytes())
                .attemptCount(1)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .verifiedAt(LocalDateTime.now())
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .build();

        // When
        ParentVerification savedVerification = persistFlushAndClear(verification);
        Optional<ParentVerification> foundVerification = parentVerificationRepository.findById(savedVerification.getVerificationId());

        // Then
        assertThat(savedVerification.getVerificationId()).isNotNull();
        assertThat(savedVerification.getParentId()).isEqualTo(testParentId);
        assertThat(savedVerification.getVerificationMethod()).isEqualTo(VerificationMethod.EMAIL);
        assertThat(savedVerification.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(savedVerification.getAttemptCount()).isEqualTo(1);
        assertThat(savedVerification.getCreatedAt()).isNotNull();
        
        assertThat(foundVerification).isPresent();
        ParentVerification retrieved = foundVerification.get();
        assertThat(retrieved.getVerificationId()).isEqualTo(savedVerification.getVerificationId());
        assertThat(retrieved.getParentId()).isEqualTo(testParentId);
        assertThat(retrieved.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(retrieved.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("findById: when non-existent ID then returns empty")
    void findById_whenNonExistentId_thenReturnsEmpty() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        Optional<ParentVerification> found = parentVerificationRepository.findById(nonExistentId);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("onCreate: should auto-populate attemptCount and createdAt")
    void onCreate_shouldAutoPopulateFields() {
        // Given
        ParentVerification verification = ParentVerification.builder()
                .parentId(testParentId)
                .verificationMethod(VerificationMethod.EMAIL)
                .verificationStatus(VerificationStatus.PENDING)
                .contactInfoHash("test@example.com".getBytes())
                .verificationCodeHash("123456".getBytes())
                .attemptCount(null) // Should be auto-populated
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        // When
        ParentVerification saved = persistFlushAndClear(verification);
        Optional<ParentVerification> found = parentVerificationRepository.findById(saved.getVerificationId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getAttemptCount()).isEqualTo(0);
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getCreatedAt()).isBefore(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(5));
    }

    @Test
    @DisplayName("findByParentIdOrderByCreatedAtDesc: should return verifications ordered by creation time")
    void findByParentIdOrderByCreatedAtDesc_shouldReturnOrderedResults() {
        // Given - use different contact hashes to avoid unique constraint violation
        LocalDateTime time1 = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime time2 = LocalDateTime.of(2025, 1, 1, 11, 0);
        LocalDateTime time3 = LocalDateTime.of(2025, 1, 1, 12, 0);
        
        ParentVerification v1 = createVerification(testParentId, time1, "test1@example.com".getBytes());
        ParentVerification v2 = createVerification(testParentId, time2, "test2@example.com".getBytes());
        ParentVerification v3 = createVerification(testParentId, time3, "test3@example.com".getBytes());
        
        persistFlushAndClear(v1);
        persistFlushAndClear(v2);
        persistFlushAndClear(v3);

        // When
        List<ParentVerification> results = parentVerificationRepository.findByParentIdOrderByCreatedAtDesc(testParentId);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getCreatedAt()).isEqualTo(time3); // Most recent first
        assertThat(results.get(1).getCreatedAt()).isEqualTo(time2);
        assertThat(results.get(2).getCreatedAt()).isEqualTo(time1);
    }

    @Test
    @DisplayName("findByVerificationIdAndVerificationStatus: should find verification by ID and status")
    void findByVerificationIdAndVerificationStatus_shouldFindByBothCriteria() {
        // Given
        ParentVerification verification = ParentVerification.builder()
                .parentId(testParentId)
                .verificationMethod(VerificationMethod.EMAIL)
                .verificationStatus(VerificationStatus.PENDING)
                .contactInfoHash("test@example.com".getBytes())
                .verificationCodeHash("123456".getBytes())
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        
        ParentVerification saved = persistFlushAndClear(verification);

        // When
        Optional<ParentVerification> found = parentVerificationRepository.findByVerificationIdAndVerificationStatus(
                saved.getVerificationId(), VerificationStatus.PENDING);
        Optional<ParentVerification> notFound = parentVerificationRepository.findByVerificationIdAndVerificationStatus(
                saved.getVerificationId(), VerificationStatus.VERIFIED);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getVerificationId()).isEqualTo(saved.getVerificationId());
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("findPendingVerificationsByParent: should return only pending and non-expired verifications")
    void findPendingVerificationsByParent_shouldReturnOnlyPendingAndNonExpired() {
        // Given
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        
        ParentVerification pendingFuture = ParentVerification.builder()
                .parentId(testParentId)
                .verificationMethod(VerificationMethod.EMAIL)
                .verificationStatus(VerificationStatus.PENDING)
                .contactInfoHash("test1@example.com".getBytes())
                .verificationCodeHash("hash1".getBytes())
                .attemptCount(0)
                .expiresAt(now.plusHours(1)) // Not expired
                .build();
        
        ParentVerification pendingExpired = ParentVerification.builder()
                .parentId(testParentId)
                .verificationMethod(VerificationMethod.EMAIL)
                .verificationStatus(VerificationStatus.PENDING)
                .contactInfoHash("test2@example.com".getBytes())
                .verificationCodeHash("hash2".getBytes())
                .attemptCount(0)
                .expiresAt(now.minusHours(1)) // Expired
                .build();
        
        ParentVerification verified = ParentVerification.builder()
                .parentId(testParentId)
                .verificationMethod(VerificationMethod.EMAIL)
                .verificationStatus(VerificationStatus.VERIFIED)
                .contactInfoHash("test3@example.com".getBytes())
                .verificationCodeHash("hash3".getBytes())
                .attemptCount(1)
                .expiresAt(now.plusHours(1))
                .verifiedAt(now)
                .build();
        
        persistFlushAndClear(pendingFuture);
        persistFlushAndClear(pendingExpired);
        persistFlushAndClear(verified);

        // When
        List<ParentVerification> results = parentVerificationRepository.findPendingVerificationsByParent(testParentId, now);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(results.get(0).getExpiresAt()).isAfter(now);
    }

    @Test
    @DisplayName("findByVerificationStatus: should return all verifications with given status")
    void findByVerificationStatus_shouldReturnAllWithStatus() {
        // Given
        ParentVerification pending1 = createVerificationWithStatus(VerificationStatus.PENDING);
        ParentVerification pending2 = createVerificationWithStatus(VerificationStatus.PENDING);
        ParentVerification verified = createVerificationWithStatus(VerificationStatus.VERIFIED);
        
        persistFlushAndClear(pending1);
        persistFlushAndClear(pending2);
        persistFlushAndClear(verified);

        // When
        List<ParentVerification> pendingResults = parentVerificationRepository.findByVerificationStatus(VerificationStatus.PENDING);
        List<ParentVerification> verifiedResults = parentVerificationRepository.findByVerificationStatus(VerificationStatus.VERIFIED);

        // Then
        assertThat(pendingResults).hasSize(2);
        assertThat(verifiedResults).hasSize(1);
        assertThat(verifiedResults.get(0).getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("countFailedAttemptsSince: should count failed verifications since given time")
    void countFailedAttemptsSince_shouldCountFailedSinceTime() {
        // Given
        LocalDateTime since = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime before = since.minusHours(1);
        LocalDateTime after = since.plusHours(1);
        
        ParentVerification failedBefore = ParentVerification.builder()
                .parentId(testParentId)
                .verificationMethod(VerificationMethod.EMAIL)
                .verificationStatus(VerificationStatus.FAILED)
                .contactInfoHash("test1@example.com".getBytes())
                .verificationCodeHash("hash1".getBytes())
                .attemptCount(3)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .createdAt(before)
                .build();
        
        ParentVerification failedAfter = ParentVerification.builder()
                .parentId(testParentId)
                .verificationMethod(VerificationMethod.EMAIL)
                .verificationStatus(VerificationStatus.FAILED)
                .contactInfoHash("test2@example.com".getBytes())
                .verificationCodeHash("hash2".getBytes())
                .attemptCount(3)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .createdAt(after)
                .build();
        
        persistFlushAndClear(failedBefore);
        persistFlushAndClear(failedAfter);

        // When
        long count = parentVerificationRepository.countFailedAttemptsSince(testParentId, since);

        // Then
        assertThat(count).isEqualTo(1); // Only the one created after 'since'
    }

    // Helper methods
    private ParentVerification createVerification(UUID parentId, LocalDateTime createdAt) {
        return createVerification(parentId, createdAt, "test@example.com".getBytes());
    }

    private ParentVerification createVerification(UUID parentId, LocalDateTime createdAt, byte[] contactHash) {
        return ParentVerification.builder()
                .parentId(parentId)
                .verificationMethod(VerificationMethod.EMAIL)
                .verificationStatus(VerificationStatus.PENDING)
                .contactInfoHash(contactHash)
                .verificationCodeHash("123456".getBytes())
                .attemptCount(0)
                .expiresAt(createdAt.plusDays(1))
                .createdAt(createdAt)
                .build();
    }

    private ParentVerification createVerificationWithStatus(VerificationStatus status) {
        return ParentVerification.builder()
                .parentId(UUID.randomUUID())
                .verificationMethod(VerificationMethod.EMAIL)
                .verificationStatus(status)
                .contactInfoHash("test@example.com".getBytes())
                .verificationCodeHash("123456".getBytes())
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }
}
