package uk.gegc.kidsgptbackend.features.consent.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.features.consent.domain.model.*;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentLedgerRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ConsentLedgerFindFirstRepositoryTest {

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
    void findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnLatestByConsentTimestamp() {
        // Arrange - Create multiple records with varying timestamps
        LocalDateTime timestamp1 = LocalDateTime.now().minusHours(2);
        LocalDateTime timestamp2 = LocalDateTime.now().minusHours(1);
        LocalDateTime timestamp3 = LocalDateTime.now();

        // Create records with different timestamps
        ConsentLedger record1 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .consentTimestamp(timestamp1)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        ConsentLedger record2 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.WITHDRAWN)
                .policyUrl("https://example.com/policy")
                .contentHash("hash456")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(timestamp2)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        ConsentLedger record3 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("3.0.0")
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
                .consentTimestamp(timestamp3)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record3\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        // Save records in non-chronological order and get their auto-generated IDs
        ConsentLedger savedRecord2 = consentLedgerRepository.save(record2); // timestamp2 (middle)
        ConsentLedger savedRecord3 = consentLedgerRepository.save(record3); // timestamp3 (latest)
        ConsentLedger savedRecord1 = consentLedgerRepository.save(record1); // timestamp1 (earliest)
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<ConsentLedger> result = consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                testUserId, testConsentType);

        // Assert
        assertTrue(result.isPresent(), "Should find a record");
        ConsentLedger foundRecord = result.get();
        assertEquals(savedRecord3.getConsentId(), foundRecord.getConsentId(), "Should return the record with the latest consentTimestamp");
        assertEquals("3.0.0", foundRecord.getConsentVersion(), "Should return the record with version 3.0.0");
        // Compare timestamps with tolerance for precision differences
        assertTrue(foundRecord.getConsentTimestamp().isAfter(timestamp2) || foundRecord.getConsentTimestamp().equals(timestamp2),
                "Should return the record with the latest timestamp");
    }

    @Test
    void findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnEmptyWhenNoRecordsExist() {
        // Act
        Optional<ConsentLedger> result = consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                testUserId, testConsentType);

        // Assert
        assertFalse(result.isPresent(), "Should return empty when no records exist for the user/type");
    }

    @Test
    void findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnEmptyForDifferentUser() {
        // Arrange - Create a record for a different user
        UUID differentUserId = UUID.randomUUID();

        ConsentLedger record = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(differentUserId)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        consentLedgerRepository.save(record);
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<ConsentLedger> result = consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                testUserId, testConsentType); // Looking for testUserId

        // Assert
        assertFalse(result.isPresent(), "Should return empty when no records exist for the target user");
    }

    @Test
    void findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnEmptyForDifferentConsentType() {
        // Arrange - Create a record for a different consent type
        ConsentLedger record = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE) // Different consent type
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        consentLedgerRepository.save(record);
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<ConsentLedger> result = consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                testUserId, testConsentType); // Looking for PARENTAL_CONSENT

        // Assert
        assertFalse(result.isPresent(), "Should return empty when no records exist for the target consent type");
    }

    @Test
    void findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnLatestRegardlessOfStatus() {
        // Arrange - Create records with different statuses but varying timestamps
        LocalDateTime timestamp1 = LocalDateTime.now().minusHours(1);
        LocalDateTime timestamp2 = LocalDateTime.now();

        // Create a GRANTED record with earlier timestamp
        ConsentLedger grantedRecord = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .consentTimestamp(timestamp1)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a WITHDRAWN record with later timestamp
        ConsentLedger withdrawnRecord = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .consentTimestamp(timestamp2)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Save records and get their auto-generated IDs
        ConsentLedger savedGrantedRecord = consentLedgerRepository.save(grantedRecord);
        ConsentLedger savedWithdrawnRecord = consentLedgerRepository.save(withdrawnRecord);
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<ConsentLedger> result = consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                testUserId, testConsentType);

        // Assert
        assertTrue(result.isPresent(), "Should find a record");
        ConsentLedger foundRecord = result.get();
        assertEquals(savedWithdrawnRecord.getConsentId(), foundRecord.getConsentId(), "Should return the record with the latest consentTimestamp regardless of status");
        assertEquals(ConsentStatus.WITHDRAWN, foundRecord.getConsentStatus(), "Should return the WITHDRAWN record with later timestamp");
        // Compare timestamps with tolerance for precision differences
        assertTrue(foundRecord.getConsentTimestamp().isAfter(timestamp1) || foundRecord.getConsentTimestamp().equals(timestamp1),
                "Should return the record with the latest timestamp");
    }



    @Test
    void findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc_ShouldFilterByStatusAndReturnLatestEntry() {
        // Arrange - Create multiple records with different statuses and timestamps
        LocalDateTime timestamp1 = LocalDateTime.now().minusHours(3);
        LocalDateTime timestamp2 = LocalDateTime.now().minusHours(2);
        LocalDateTime timestamp3 = LocalDateTime.now().minusHours(1);
        LocalDateTime timestamp4 = LocalDateTime.now();

        // Create a GRANTED record with earliest timestamp
        ConsentLedger grantedRecord1 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .consentTimestamp(timestamp1)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a WITHDRAWN record with second timestamp
        ConsentLedger withdrawnRecord = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .consentTimestamp(timestamp2)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Create a GRANTED record with third timestamp (should be returned for GRANTED status)
        ConsentLedger grantedRecord2 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("2.0.0")
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
                .consentTimestamp(timestamp3)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted2\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        // Create a WITHDRAWN record with latest timestamp (should be returned for WITHDRAWN status)
        ConsentLedger withdrawnRecord2 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.WITHDRAWN)
                .policyUrl("https://example.com/policy")
                .contentHash("hash456")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(timestamp4)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn2\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .build();

        // Save records in non-chronological order and get their auto-generated IDs
        ConsentLedger savedWithdrawnRecord2 = consentLedgerRepository.save(withdrawnRecord2); // timestamp4 (latest)
        ConsentLedger savedGrantedRecord1 = consentLedgerRepository.save(grantedRecord1); // timestamp1 (earliest)
        ConsentLedger savedGrantedRecord2 = consentLedgerRepository.save(grantedRecord2); // timestamp3 (third)
        ConsentLedger savedWithdrawnRecord = consentLedgerRepository.save(withdrawnRecord); // timestamp2 (second)
        entityManager.flush();
        entityManager.clear();

        // Act - Test for GRANTED status
        Optional<ConsentLedger> grantedResult = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        testUserId, testConsentType, ConsentStatus.GRANTED);

        // Assert - Should return the latest GRANTED record
        assertTrue(grantedResult.isPresent(), "Should find a GRANTED record");
        ConsentLedger foundGrantedRecord = grantedResult.get();
        assertEquals(savedGrantedRecord2.getConsentId(), foundGrantedRecord.getConsentId(), "Should return the latest GRANTED record");
        assertEquals(ConsentStatus.GRANTED, foundGrantedRecord.getConsentStatus(), "Should be GRANTED status");
        assertEquals("2.0.0", foundGrantedRecord.getConsentVersion(), "Should return the record with version 2.0.0");

        // Act - Test for WITHDRAWN status
        Optional<ConsentLedger> withdrawnResult = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        testUserId, testConsentType, ConsentStatus.WITHDRAWN);

        // Assert - Should return the latest WITHDRAWN record
        assertTrue(withdrawnResult.isPresent(), "Should find a WITHDRAWN record");
        ConsentLedger foundWithdrawnRecord = withdrawnResult.get();
        assertEquals(savedWithdrawnRecord2.getConsentId(), foundWithdrawnRecord.getConsentId(), "Should return the latest WITHDRAWN record");
        assertEquals(ConsentStatus.WITHDRAWN, foundWithdrawnRecord.getConsentStatus(), "Should be WITHDRAWN status");
        assertEquals("2.0.0", foundWithdrawnRecord.getConsentVersion(), "Should return the record with version 2.0.0");
    }

    @Test
    void findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnEmptyWhenNoRecordsWithStatusExist() {
        // Arrange - Create only GRANTED records
        ConsentLedger grantedRecord = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        consentLedgerRepository.save(grantedRecord);
        entityManager.flush();
        entityManager.clear();

        // Act - Test for WITHDRAWN status when only GRANTED exists
        Optional<ConsentLedger> result = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        testUserId, testConsentType, ConsentStatus.WITHDRAWN);

        // Assert
        assertFalse(result.isPresent(), "Should return empty when no WITHDRAWN records exist");
    }

    @Test
    void findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnEmptyWhenNoRecordsExist() {
        // Act - Test for any status when no records exist
        Optional<ConsentLedger> result = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        testUserId, testConsentType, ConsentStatus.GRANTED);

        // Assert
        assertFalse(result.isPresent(), "Should return empty when no records exist");
    }

    @Test
    void findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnEmptyForDifferentUser() {
        // Arrange - Create a record for a different user
        UUID differentUserId = UUID.randomUUID();

        ConsentLedger record = ConsentLedger.builder() 
                .consentId(UUID.randomUUID())
                .userId(differentUserId)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        consentLedgerRepository.save(record);
        entityManager.flush();
        entityManager.clear();

        // Act - Test for the target user
        Optional<ConsentLedger> result = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        testUserId, testConsentType, ConsentStatus.GRANTED);

        // Assert
        assertFalse(result.isPresent(), "Should return empty when no records exist for the target user");
    }

    @Test
    void findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnEmptyForDifferentConsentType() {
        // Arrange - Create a record for a different consent type
        ConsentLedger record = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE) // Different consent type
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        consentLedgerRepository.save(record);
        entityManager.flush();
        entityManager.clear();

        // Act - Test for the target consent type
        Optional<ConsentLedger> result = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        testUserId, testConsentType, ConsentStatus.GRANTED); // Looking for PARENTAL_CONSENT

        // Assert
        assertFalse(result.isPresent(), "Should return empty when no records exist for the target consent type");
    }

    @Test
    void findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc_ShouldHonorCompositeSortForSameTimestamp() {
        // Arrange - Create multiple records with same consentTimestamp but different createdAt
        LocalDateTime sameConsentTimestamp = LocalDateTime.now().minusHours(1);
        LocalDateTime createdAt1 = LocalDateTime.now().minusMinutes(30);
        LocalDateTime createdAt2 = LocalDateTime.now().minusMinutes(20);
        LocalDateTime createdAt3 = LocalDateTime.now().minusMinutes(10);

        // Create records with same consentTimestamp but different createdAt values
        ConsentLedger record1 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .consentTimestamp(sameConsentTimestamp)
                .createdAt(createdAt1)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        ConsentLedger record2 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("2.0.0")
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
                .consentTimestamp(sameConsentTimestamp)
                .createdAt(createdAt2)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        ConsentLedger record3 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("3.0.0")
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
                .consentTimestamp(sameConsentTimestamp)
                .createdAt(createdAt3)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record3\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        // Save records in non-chronological order and get their auto-generated IDs
        ConsentLedger savedRecord2 = consentLedgerRepository.save(record2); // createdAt2 (2nd)
        ConsentLedger savedRecord3 = consentLedgerRepository.save(record3); // createdAt3 (3rd - latest)
        ConsentLedger savedRecord1 = consentLedgerRepository.save(record1); // createdAt1 (1st - earliest)
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<ConsentLedger> result = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        testUserId, testConsentType, ConsentStatus.GRANTED);

        // Assert
        assertTrue(result.isPresent(), "Should find a record");
        ConsentLedger foundRecord = result.get();
        assertEquals(savedRecord3.getConsentId(), foundRecord.getConsentId(), "Should return the record with the latest createdAt when consentTimestamp is the same");
        assertEquals("3.0.0", foundRecord.getConsentVersion(), "Should return the record with version 3.0.0");

        // Verify all records have the same consentTimestamp (with tolerance for precision differences)
        long diffNanos = Math.abs(foundRecord.getConsentTimestamp().toLocalTime().toNanoOfDay() - sameConsentTimestamp.toLocalTime().toNanoOfDay());
        assertTrue(diffNanos < 1000, "Should have the same consentTimestamp (within 1 microsecond tolerance)");
    }
} 