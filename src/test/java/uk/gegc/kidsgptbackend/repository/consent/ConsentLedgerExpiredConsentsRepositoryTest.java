package uk.gegc.kidsgptbackend.repository.consent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.model.consent.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ConsentLedgerExpiredConsentsRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConsentLedgerRepository consentLedgerRepository;

    private UUID testUserId;
    private ConsentType testConsentType;
    private String testVersion;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testConsentType = ConsentType.PARENTAL_CONSENT;
        testVersion = "1.0.0";
    }

    @Test
    void findExpiredConsents_ShouldReturnOnlyRowsWithRetentionExpiresAtLessThanOrEqualToNow() {
        // Arrange - Create records with different retention expiration dates
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime expired1 = now.minusDays(1); // Expired yesterday
        LocalDateTime expired2 = now.minusHours(1); // Expired 1 hour ago
        LocalDateTime expiresNow = now; // Expires now (should be included)
        LocalDateTime expiresLater = now.plusDays(1); // Expires tomorrow (should not be included)

        // Create an expired record (expired yesterday)
        ConsentLedger expiredRecord1 = ConsentLedger.builder()
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash123")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(10))
                .retentionExpiresAt(expired1)
                .receiptJson("{\"test\":\"expired1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create another expired record (expired 1 hour ago)
        ConsentLedger expiredRecord2 = ConsentLedger.builder()
                .userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash456")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(5))
                .retentionExpiresAt(expired2)
                .receiptJson("{\"test\":\"expired2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Create a record that expires now (should be included)
        ConsentLedger expiresNowRecord = ConsentLedger.builder()
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash789")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(2))
                .retentionExpiresAt(expiresNow)
                .receiptJson("{\"test\":\"expiresNow\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        // Create a record that expires later (should not be included)
        ConsentLedger expiresLaterRecord = ConsentLedger.builder()
                .userId(testUserId)
                .consentType(ConsentType.DATA_PROCESSING)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash101112")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(expiresLater)
                .receiptJson("{\"test\":\"expiresLater\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .build();

        // Save all records and get their auto-generated IDs
        ConsentLedger savedExpiredRecord1 = consentLedgerRepository.save(expiredRecord1);
        ConsentLedger savedExpiredRecord2 = consentLedgerRepository.save(expiredRecord2);
        ConsentLedger savedExpiresNowRecord = consentLedgerRepository.save(expiresNowRecord);
        ConsentLedger savedExpiresLaterRecord = consentLedgerRepository.save(expiresLaterRecord);
        entityManager.flush();
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findExpiredConsents(now);

        // Assert
        assertEquals(3, result.size(), "Should return exactly 3 expired records");

        // Verify that all returned records have retentionExpiresAt <= now
        result.forEach(record -> {
            assertTrue(record.getRetentionExpiresAt().isBefore(now) || record.getRetentionExpiresAt().equals(now),
                    "All returned records should have retentionExpiresAt <= now");
        });

        // Verify specific records are included
        List<UUID> returnedConsentIds = result.stream()
                .map(ConsentLedger::getConsentId)
                .toList();

        assertTrue(returnedConsentIds.contains(savedExpiredRecord1.getConsentId()), "Should include record that expired yesterday");
        assertTrue(returnedConsentIds.contains(savedExpiredRecord2.getConsentId()), "Should include record that expired 1 hour ago");
        assertTrue(returnedConsentIds.contains(savedExpiresNowRecord.getConsentId()), "Should include record that expires now");
        assertFalse(returnedConsentIds.contains(savedExpiresLaterRecord.getConsentId()), "Should not include record that expires later");
    }

    @Test
    void findExpiredConsents_ShouldReturnEmptyWhenNoExpiredRecordsExist() {
        // Arrange - Create only records that expire in the future
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime expiresLater1 = now.plusDays(1);
        LocalDateTime expiresLater2 = now.plusDays(30);

        // Create records that expire in the future
        ConsentLedger futureRecord1 = ConsentLedger.builder()
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash123")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(5))
                .retentionExpiresAt(expiresLater1)
                .receiptJson("{\"test\":\"future1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        ConsentLedger futureRecord2 = ConsentLedger.builder()
                .userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash456")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(10))
                .retentionExpiresAt(expiresLater2)
                .receiptJson("{\"test\":\"future2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        consentLedgerRepository.save(futureRecord1);
        consentLedgerRepository.save(futureRecord2);
        entityManager.flush();
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findExpiredConsents(now);

        // Assert
        assertTrue(result.isEmpty(), "Should return empty list when no expired records exist");
    }

    @Test
    void findExpiredConsents_ShouldReturnEmptyWhenNoRecordsExist() {
        // Act
        List<ConsentLedger> result = consentLedgerRepository.findExpiredConsents(LocalDateTime.now());

        // Assert
        assertTrue(result.isEmpty(), "Should return empty list when no records exist");
    }

    @Test
    void findExpiredConsents_ShouldIncludeRecordsWithExactExpirationTime() {
        // Arrange - Create a record that expires exactly at the specified time
        LocalDateTime exactExpirationTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS).plusMinutes(5); // Set to a specific time

        ConsentLedger exactExpirationRecord = ConsentLedger.builder()
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash123")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(exactExpirationTime)
                .receiptJson("{\"test\":\"exactExpiration\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        ConsentLedger savedExactExpirationRecord = consentLedgerRepository.save(exactExpirationRecord);
        entityManager.flush();
        entityManager.clear();

        // Act - Query with the exact expiration time
        List<ConsentLedger> result = consentLedgerRepository.findExpiredConsents(exactExpirationTime);

        // Assert
        assertEquals(1, result.size(), "Should include record with exact expiration time");
        assertEquals(savedExactExpirationRecord.getConsentId(), result.get(0).getConsentId(), "Should return the correct record");

        // Compare with tolerance for precision differences (database may truncate nanoseconds)
        LocalDateTime actualExpirationTime = result.get(0).getRetentionExpiresAt();
        long diffNanos = Math.abs(actualExpirationTime.toLocalTime().toNanoOfDay() - exactExpirationTime.toLocalTime().toNanoOfDay());
        assertTrue(diffNanos < 1000, "Should have the same expiration time (within 1 microsecond tolerance)");

        // Also verify the date part is exactly the same
        assertEquals(exactExpirationTime.toLocalDate(), actualExpirationTime.toLocalDate(), "Should have the same expiration date");
    }

    @Test
    void findExpiredConsents_ShouldHandleDifferentConsentStatuses() {
        // Arrange - Create expired records with different statuses
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime expiredTime = now.minusDays(1);

        // Create an expired GRANTED record
        ConsentLedger grantedRecord = ConsentLedger.builder()
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash123")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(10))
                .retentionExpiresAt(expiredTime)
                .receiptJson("{\"test\":\"granted\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create an expired WITHDRAWN record
        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.WITHDRAWN)
                .policyUrl("https://example.com/policy")
                .contentHash("hash123")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(5))
                .retentionExpiresAt(expiredTime)
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Save records and get their auto-generated IDs
        ConsentLedger savedGrantedRecord = consentLedgerRepository.save(grantedRecord);
        ConsentLedger savedWithdrawnRecord = consentLedgerRepository.save(withdrawnRecord);
        entityManager.flush();
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findExpiredConsents(now);

        // Assert
        assertEquals(2, result.size(), "Should return both GRANTED and WITHDRAWN expired records");

        List<UUID> returnedConsentIds = result.stream()
                .map(ConsentLedger::getConsentId)
                .toList();

        assertTrue(returnedConsentIds.contains(savedGrantedRecord.getConsentId()), "Should include expired GRANTED record");
        assertTrue(returnedConsentIds.contains(savedWithdrawnRecord.getConsentId()), "Should include expired WITHDRAWN record");
    }
} 