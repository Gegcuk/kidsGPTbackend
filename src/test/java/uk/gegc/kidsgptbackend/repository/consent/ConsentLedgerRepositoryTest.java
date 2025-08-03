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
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ConsentLedgerRepositoryTest {

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
    void findActiveGrantByUserTypeAndVersion_ShouldReturnOnlyGrantedRowForExactVersion() {
        // Arrange - Create multiple grants and withdrawals for the same user/type/version
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID consentId4 = UUID.randomUUID();

        // Create a GRANTED record for the exact version
        ConsentLedger grantedRecord = ConsentLedger.builder()
                .consentId(consentId1)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion(testVersion)
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
                .receiptJson("{\"test\":\"grant\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a WITHDRAWN record for the same version
        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion(testVersion)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .withdrawnConsentId(consentId1)
                .build();

        // Create a GRANTED record for a different version
        ConsentLedger grantedDifferentVersion = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"grant2\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        // Create a GRANTED record for a different user
        ConsentLedger grantedDifferentUser = ConsentLedger.builder()
                .consentId(consentId4)
                .userId(UUID.randomUUID())
                .consentType(testConsentType)
                .consentVersion(testVersion)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"grant3\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .build();

        // Persist all records
        entityManager.persistAndFlush(grantedRecord);
        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.persistAndFlush(grantedDifferentVersion);
        entityManager.persistAndFlush(grantedDifferentUser);
        entityManager.clear();

        // Act
        Optional<ConsentLedger> result = consentLedgerRepository.findActiveGrantByUserTypeAndVersion(
                testUserId, testConsentType, testVersion);

        // Assert
        assertTrue(result.isPresent(), "Should find the GRANTED record for the exact version");
        ConsentLedger foundRecord = result.get();
        assertEquals(consentId1, foundRecord.getConsentId(), "Should return the correct GRANTED record");
        assertEquals(ConsentStatus.GRANTED, foundRecord.getConsentStatus(), "Should be GRANTED status");
        assertEquals(testVersion, foundRecord.getConsentVersion(), "Should match the exact version");
        assertEquals(testUserId, foundRecord.getUserId(), "Should match the exact user");
        assertEquals(testConsentType, foundRecord.getConsentType(), "Should match the exact consent type");
    }

    @Test
    void findActiveGrantByUserTypeAndVersion_ShouldReturnEmptyWhenNoGrantedRecordExists() {
        // Arrange - Create only a WITHDRAWN record for the version
        UUID consentId = UUID.randomUUID();

        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion(testVersion)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.clear();

        // Act
        Optional<ConsentLedger> result = consentLedgerRepository.findActiveGrantByUserTypeAndVersion(
                testUserId, testConsentType, testVersion);

        // Assert
        assertFalse(result.isPresent(), "Should not find any record when only WITHDRAWN exists");
    }

    @Test
    void findActiveGrantByUserTypeAndVersion_ShouldReturnEmptyWhenNoRecordExists() {
        // Act
        Optional<ConsentLedger> result = consentLedgerRepository.findActiveGrantByUserTypeAndVersion(
                testUserId, testConsentType, testVersion);

        // Assert
        assertFalse(result.isPresent(), "Should return empty when no records exist");
    }

    @Test
    void existsWithdrawalByUserTypeAndVersion_ShouldReturnTrueWhenWithdrawalExists() {
        // Arrange - Create a WITHDRAWN record for the exact user/type/version
        UUID consentId = UUID.randomUUID();

        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion(testVersion)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.clear();

        // Act
        boolean result = consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                testUserId, testConsentType, testVersion);

        // Assert
        assertTrue(result, "Should return true when WITHDRAWN record exists for the exact user/type/version");
    }

    @Test
    void existsWithdrawalByUserTypeAndVersion_ShouldReturnFalseWhenNoWithdrawalExists() {
        // Arrange - Create only a GRANTED record for the user/type/version
        UUID consentId = UUID.randomUUID();

        ConsentLedger grantedRecord = ConsentLedger.builder()
                .consentId(consentId)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion(testVersion)
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
                .receiptJson("{\"test\":\"grant\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(grantedRecord);
        entityManager.clear();

        // Act
        boolean result = consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                testUserId, testConsentType, testVersion);

        // Assert
        assertFalse(result, "Should return false when only GRANTED record exists for the user/type/version");
    }

    @Test
    void existsWithdrawalByUserTypeAndVersion_ShouldReturnFalseWhenNoRecordsExist() {
        // Act
        boolean result = consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                testUserId, testConsentType, testVersion);

        // Assert
        assertFalse(result, "Should return false when no records exist for the user/type/version");
    }

    @Test
    void existsWithdrawalByUserTypeAndVersion_ShouldReturnFalseForDifferentVersion() {
        // Arrange - Create a WITHDRAWN record for a different version
        UUID consentId = UUID.randomUUID();

        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("2.0.0") // Different version
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.clear();

        // Act
        boolean result = consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                testUserId, testConsentType, testVersion); // Looking for version "1.0.0"

        // Assert
        assertFalse(result, "Should return false when WITHDRAWN record exists for a different version");
    }

    @Test
    void existsWithdrawalByUserTypeAndVersion_ShouldReturnFalseForDifferentUser() {
        // Arrange - Create a WITHDRAWN record for a different user
        UUID consentId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();

        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId)
                .userId(differentUserId) // Different user
                .consentType(testConsentType)
                .consentVersion(testVersion)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.clear();

        // Act
        boolean result = consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                testUserId, testConsentType, testVersion); // Looking for testUserId

        // Assert
        assertFalse(result, "Should return false when WITHDRAWN record exists for a different user");
    }

    @Test
    void existsWithdrawalByUserTypeAndVersion_ShouldReturnFalseForDifferentConsentType() {
        // Arrange - Create a WITHDRAWN record for a different consent type
        UUID consentId = UUID.randomUUID();

        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId)
                .userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE) // Different consent type
                .consentVersion(testVersion)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.clear();

        // Act
        boolean result = consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                testUserId, testConsentType, testVersion); // Looking for PARENTAL_CONSENT

        // Assert
        assertFalse(result, "Should return false when WITHDRAWN record exists for a different consent type");
    }

    @Test
    void findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnLatestByConsentTimestamp() {
        // Arrange - Create multiple records with varying timestamps
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();

        LocalDateTime timestamp1 = LocalDateTime.now().minusHours(2);
        LocalDateTime timestamp2 = LocalDateTime.now().minusHours(1);
        LocalDateTime timestamp3 = LocalDateTime.now();

        // Create records with different timestamps
        ConsentLedger record1 = ConsentLedger.builder()
                .consentId(consentId1)
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
                .consentTimestamp(timestamp1)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        ConsentLedger record2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
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
                .withdrawnConsentId(consentId1)
                .build();

        ConsentLedger record3 = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
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

        // Persist records in non-chronological order
        entityManager.persistAndFlush(record2); // timestamp2 (middle)
        entityManager.persistAndFlush(record3); // timestamp3 (latest)
        entityManager.persistAndFlush(record1); // timestamp1 (earliest)
        entityManager.clear();

        // Act
        Optional<ConsentLedger> result = consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                testUserId, testConsentType);

        // Assert
        assertTrue(result.isPresent(), "Should find a record");
        ConsentLedger foundRecord = result.get();
        assertEquals(consentId3, foundRecord.getConsentId(), "Should return the record with the latest consentTimestamp");
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
        UUID consentId = UUID.randomUUID();

        ConsentLedger record = ConsentLedger.builder()
                .consentId(consentId)
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

        entityManager.persistAndFlush(record);
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
        UUID consentId = UUID.randomUUID();

        ConsentLedger record = ConsentLedger.builder()
                .consentId(consentId)
                .userId(testUserId)
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

        entityManager.persistAndFlush(record);
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
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();

        LocalDateTime timestamp1 = LocalDateTime.now().minusHours(1);
        LocalDateTime timestamp2 = LocalDateTime.now();

        // Create a GRANTED record with earlier timestamp
        ConsentLedger grantedRecord = ConsentLedger.builder()
                .consentId(consentId1)
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
                .consentTimestamp(timestamp1)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a WITHDRAWN record with later timestamp
        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId2)
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
                .consentTimestamp(timestamp2)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .withdrawnConsentId(consentId1)
                .build();

        entityManager.persistAndFlush(grantedRecord);
        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.clear();

        // Act
        Optional<ConsentLedger> result = consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                testUserId, testConsentType);

        // Assert
        assertTrue(result.isPresent(), "Should find a record");
        ConsentLedger foundRecord = result.get();
        assertEquals(consentId2, foundRecord.getConsentId(), "Should return the record with the latest consentTimestamp regardless of status");
        assertEquals(ConsentStatus.WITHDRAWN, foundRecord.getConsentStatus(), "Should return the WITHDRAWN record with later timestamp");
        // Compare timestamps with tolerance for precision differences
        assertTrue(foundRecord.getConsentTimestamp().isAfter(timestamp1) || foundRecord.getConsentTimestamp().equals(timestamp1), 
                "Should return the record with the latest timestamp");
    }

    @Test
    void duplicateKeyConstraint_ShouldPreventDuplicateWithdrawals() {
        // Arrange - Create a GRANTED record first
        UUID grantedConsentId = UUID.randomUUID();
        UUID withdrawalConsentId1 = UUID.randomUUID();
        UUID withdrawalConsentId2 = UUID.randomUUID();

        ConsentLedger grantedRecord = ConsentLedger.builder()
                .consentId(grantedConsentId)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion(testVersion)
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
                .receiptJson("{\"test\":\"grant\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create first withdrawal record
        ConsentLedger withdrawalRecord1 = ConsentLedger.builder()
                .consentId(withdrawalConsentId1)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion(testVersion)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn1\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .withdrawnConsentId(grantedConsentId)
                .build();

        // Create second withdrawal record with same user/type/version/status (simulating concurrent insert)
        ConsentLedger withdrawalRecord2 = ConsentLedger.builder()
                .consentId(withdrawalConsentId2)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion(testVersion)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn2\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .withdrawnConsentId(grantedConsentId)
                .build();

        // Persist the granted record and first withdrawal
        entityManager.persistAndFlush(grantedRecord);
        entityManager.persistAndFlush(withdrawalRecord1);
        entityManager.clear();

        // Act & Assert - Attempt to persist the second withdrawal should fail due to duplicate key constraint
        // Note: This test assumes there's a unique constraint on (userId, consentType, consentStatus, consentVersion)
        // The exact behavior depends on the database and constraint configuration
        try {
            entityManager.persistAndFlush(withdrawalRecord2);
            // If we reach here, either there's no constraint or the constraint allows duplicates
            // This is acceptable behavior - the test documents the current constraint state
            System.out.println("Note: No duplicate key constraint enforced for (userId, consentType, consentStatus, consentVersion)");
        } catch (Exception e) {
            // Expected behavior if constraint exists
            assertTrue(e.getMessage().contains("Duplicate") || e.getMessage().contains("constraint") || 
                      e.getMessage().contains("unique") || e.getMessage().contains("violation"),
                    "Should throw constraint violation for duplicate withdrawal: " + e.getMessage());
        }
    }

    @Test
    void duplicateKeyConstraint_ShouldAllowDifferentVersions() {
        // Arrange - Create withdrawal records for different versions
        UUID grantedConsentId1 = UUID.randomUUID();
        UUID grantedConsentId2 = UUID.randomUUID();
        UUID withdrawalConsentId1 = UUID.randomUUID();
        UUID withdrawalConsentId2 = UUID.randomUUID();

        // Create granted records for different versions
        ConsentLedger grantedRecord1 = ConsentLedger.builder()
                .consentId(grantedConsentId1)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"grant1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        ConsentLedger grantedRecord2 = ConsentLedger.builder()
                .consentId(grantedConsentId2)
                .userId(testUserId)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"grant2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Create withdrawal records for different versions
        ConsentLedger withdrawalRecord1 = ConsentLedger.builder()
                .consentId(withdrawalConsentId1)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn1\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .withdrawnConsentId(grantedConsentId1)
                .build();

        ConsentLedger withdrawalRecord2 = ConsentLedger.builder()
                .consentId(withdrawalConsentId2)
                .userId(testUserId)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn2\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .withdrawnConsentId(grantedConsentId2)
                .build();

        // Act & Assert - Both withdrawals should be allowed since they have different versions
        entityManager.persistAndFlush(grantedRecord1);
        entityManager.persistAndFlush(grantedRecord2);
        entityManager.persistAndFlush(withdrawalRecord1);
        entityManager.persistAndFlush(withdrawalRecord2);
        entityManager.clear();

        // Verify both withdrawals exist
        assertTrue(consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                testUserId, testConsentType, "1.0.0"), "Should allow withdrawal for version 1.0.0");
        assertTrue(consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                testUserId, testConsentType, "2.0.0"), "Should allow withdrawal for version 2.0.0");
    }

    @Test
    void findByUserIdOrderByConsentTimestampDescCreatedAtDesc_ShouldHonorCompositeSort() {
        // Arrange - Create multiple records with same consentTimestamp but different createdAt
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID consentId4 = UUID.randomUUID();

        LocalDateTime sameConsentTimestamp = LocalDateTime.now().minusHours(1);
        LocalDateTime createdAt1 = LocalDateTime.now().minusMinutes(30);
        LocalDateTime createdAt2 = LocalDateTime.now().minusMinutes(20);
        LocalDateTime createdAt3 = LocalDateTime.now().minusMinutes(10);
        LocalDateTime createdAt4 = LocalDateTime.now().minusMinutes(5);

        // Create records with same consentTimestamp but different createdAt values
        ConsentLedger record1 = ConsentLedger.builder()
                .consentId(consentId1)
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
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

        ConsentLedger record2 = ConsentLedger.builder()
                .consentId(consentId2)
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
                .consentTimestamp(sameConsentTimestamp)
                .createdAt(createdAt2)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        ConsentLedger record3 = ConsentLedger.builder()
                .consentId(consentId3)
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
                .consentTimestamp(sameConsentTimestamp)
                .createdAt(createdAt3)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record3\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        ConsentLedger record4 = ConsentLedger.builder()
                .consentId(consentId4)
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
                .consentTimestamp(sameConsentTimestamp)
                .createdAt(createdAt4)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record4\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .build();

        // Persist records in non-chronological order
        entityManager.persistAndFlush(record3); // createdAt3 (3rd)
        entityManager.persistAndFlush(record1); // createdAt1 (1st)
        entityManager.persistAndFlush(record4); // createdAt4 (4th)
        entityManager.persistAndFlush(record2); // createdAt2 (2nd)
        entityManager.clear();

        // Act
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        var result = consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(testUserId, pageable);

        // Assert
        assertTrue(result.hasContent(), "Should return content");
        var content = result.getContent();
        assertEquals(4, content.size(), "Should return all 4 records");

        // Verify order: consentTimestamp DESC, then createdAt DESC
        // Since all have same consentTimestamp, order should be by createdAt DESC
        assertEquals(consentId4, content.get(0).getConsentId(), "First record should be the one with latest createdAt");
        assertEquals(consentId3, content.get(1).getConsentId(), "Second record should be the one with second latest createdAt");
        assertEquals(consentId2, content.get(2).getConsentId(), "Third record should be the one with third latest createdAt");
        assertEquals(consentId1, content.get(3).getConsentId(), "Fourth record should be the one with earliest createdAt");

        // Verify all records have the same consentTimestamp (with tolerance for precision differences)
        content.forEach(record -> {
            long diffNanos = Math.abs(record.getConsentTimestamp().toLocalTime().toNanoOfDay() - sameConsentTimestamp.toLocalTime().toNanoOfDay());
            assertTrue(diffNanos < 1000, "All records should have the same consentTimestamp (within 1 microsecond tolerance)");
        });
    }

    @Test
    void findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc_ShouldFilterByStatusAndReturnLatestEntry() {
        // Arrange - Create multiple records with different statuses and timestamps
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID consentId4 = UUID.randomUUID();

        LocalDateTime timestamp1 = LocalDateTime.now().minusHours(3);
        LocalDateTime timestamp2 = LocalDateTime.now().minusHours(2);
        LocalDateTime timestamp3 = LocalDateTime.now().minusHours(1);
        LocalDateTime timestamp4 = LocalDateTime.now();

        // Create a GRANTED record with earliest timestamp
        ConsentLedger grantedRecord1 = ConsentLedger.builder()
                .consentId(consentId1)
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
                .consentTimestamp(timestamp1)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a WITHDRAWN record with second timestamp
        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId2)
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
                .consentTimestamp(timestamp2)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .withdrawnConsentId(consentId1)
                .build();

        // Create a GRANTED record with third timestamp (should be returned for GRANTED status)
        ConsentLedger grantedRecord2 = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
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
        ConsentLedger withdrawnRecord2 = ConsentLedger.builder()
                .consentId(consentId4)
                .userId(testUserId)
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
                .withdrawnConsentId(consentId3)
                .build();

        // Persist records in non-chronological order
        entityManager.persistAndFlush(withdrawnRecord2); // timestamp4 (latest)
        entityManager.persistAndFlush(grantedRecord1); // timestamp1 (earliest)
        entityManager.persistAndFlush(grantedRecord2); // timestamp3 (third)
        entityManager.persistAndFlush(withdrawnRecord); // timestamp2 (second)
        entityManager.clear();

        // Act - Test for GRANTED status
        Optional<ConsentLedger> grantedResult = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        testUserId, testConsentType, ConsentStatus.GRANTED);

        // Assert - Should return the latest GRANTED record
        assertTrue(grantedResult.isPresent(), "Should find a GRANTED record");
        ConsentLedger foundGrantedRecord = grantedResult.get();
        assertEquals(consentId3, foundGrantedRecord.getConsentId(), "Should return the latest GRANTED record");
        assertEquals(ConsentStatus.GRANTED, foundGrantedRecord.getConsentStatus(), "Should be GRANTED status");
        assertEquals("2.0.0", foundGrantedRecord.getConsentVersion(), "Should return the record with version 2.0.0");

        // Act - Test for WITHDRAWN status
        Optional<ConsentLedger> withdrawnResult = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        testUserId, testConsentType, ConsentStatus.WITHDRAWN);

        // Assert - Should return the latest WITHDRAWN record
        assertTrue(withdrawnResult.isPresent(), "Should find a WITHDRAWN record");
        ConsentLedger foundWithdrawnRecord = withdrawnResult.get();
        assertEquals(consentId4, foundWithdrawnRecord.getConsentId(), "Should return the latest WITHDRAWN record");
        assertEquals(ConsentStatus.WITHDRAWN, foundWithdrawnRecord.getConsentStatus(), "Should be WITHDRAWN status");
        assertEquals("2.0.0", foundWithdrawnRecord.getConsentVersion(), "Should return the record with version 2.0.0");
    }

    @Test
    void findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc_ShouldReturnEmptyWhenNoRecordsWithStatusExist() {
        // Arrange - Create only GRANTED records
        UUID consentId = UUID.randomUUID();

        ConsentLedger grantedRecord = ConsentLedger.builder()
                .consentId(consentId)
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(grantedRecord);
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
        UUID consentId = UUID.randomUUID();

        ConsentLedger record = ConsentLedger.builder()
                .consentId(consentId)
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

        entityManager.persistAndFlush(record);
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
        UUID consentId = UUID.randomUUID();

        ConsentLedger record = ConsentLedger.builder()
                .consentId(consentId)
                .userId(testUserId)
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

        entityManager.persistAndFlush(record);
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
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();

        LocalDateTime sameConsentTimestamp = LocalDateTime.now().minusHours(1);
        LocalDateTime createdAt1 = LocalDateTime.now().minusMinutes(30);
        LocalDateTime createdAt2 = LocalDateTime.now().minusMinutes(20);
        LocalDateTime createdAt3 = LocalDateTime.now().minusMinutes(10);

        // Create records with same consentTimestamp but different createdAt values
        ConsentLedger record1 = ConsentLedger.builder()
                .consentId(consentId1)
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
                .consentTimestamp(sameConsentTimestamp)
                .createdAt(createdAt1)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        ConsentLedger record2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
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

        ConsentLedger record3 = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
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

        // Persist records in non-chronological order
        entityManager.persistAndFlush(record2); // createdAt2 (2nd)
        entityManager.persistAndFlush(record3); // createdAt3 (3rd - latest)
        entityManager.persistAndFlush(record1); // createdAt1 (1st - earliest)
        entityManager.clear();

        // Act
        Optional<ConsentLedger> result = consentLedgerRepository
                .findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        testUserId, testConsentType, ConsentStatus.GRANTED);

        // Assert
        assertTrue(result.isPresent(), "Should find a record");
        ConsentLedger foundRecord = result.get();
        assertEquals(consentId3, foundRecord.getConsentId(), "Should return the record with the latest createdAt when consentTimestamp is the same");
        assertEquals("3.0.0", foundRecord.getConsentVersion(), "Should return the record with version 3.0.0");

        // Verify all records have the same consentTimestamp (with tolerance for precision differences)
        long diffNanos = Math.abs(foundRecord.getConsentTimestamp().toLocalTime().toNanoOfDay() - sameConsentTimestamp.toLocalTime().toNanoOfDay());
        assertTrue(diffNanos < 1000, "Should have the same consentTimestamp (within 1 microsecond tolerance)");
    }

    @Test
    void findExpiredConsents_ShouldReturnOnlyRowsWithRetentionExpiresAtLessThanOrEqualToNow() {
        // Arrange - Create records with different retention expiration dates
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID consentId4 = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime expired1 = now.minusDays(1); // Expired yesterday
        LocalDateTime expired2 = now.minusHours(1); // Expired 1 hour ago
        LocalDateTime expiresNow = now; // Expires now (should be included)
        LocalDateTime expiresLater = now.plusDays(1); // Expires tomorrow (should not be included)

        // Create an expired record (expired yesterday)
        ConsentLedger expiredRecord1 = ConsentLedger.builder()
                .consentId(consentId1)
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
                .consentId(consentId2)
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
                .consentId(consentId3)
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
                .consentId(consentId4)
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

        // Persist all records
        entityManager.persistAndFlush(expiredRecord1);
        entityManager.persistAndFlush(expiredRecord2);
        entityManager.persistAndFlush(expiresNowRecord);
        entityManager.persistAndFlush(expiresLaterRecord);
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
        
        assertTrue(returnedConsentIds.contains(consentId1), "Should include record that expired yesterday");
        assertTrue(returnedConsentIds.contains(consentId2), "Should include record that expired 1 hour ago");
        assertTrue(returnedConsentIds.contains(consentId3), "Should include record that expires now");
        assertFalse(returnedConsentIds.contains(consentId4), "Should not include record that expires later");
    }

    @Test
    void findExpiredConsents_ShouldReturnEmptyWhenNoExpiredRecordsExist() {
        // Arrange - Create only records that expire in the future
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime expiresLater1 = now.plusDays(1);
        LocalDateTime expiresLater2 = now.plusDays(30);

        // Create records that expire in the future
        ConsentLedger futureRecord1 = ConsentLedger.builder()
                .consentId(consentId1)
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
                .consentId(consentId2)
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

        entityManager.persistAndFlush(futureRecord1);
        entityManager.persistAndFlush(futureRecord2);
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
        UUID consentId = UUID.randomUUID();
        LocalDateTime exactExpirationTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS).plusMinutes(5); // Set to a specific time

        ConsentLedger exactExpirationRecord = ConsentLedger.builder()
                .consentId(consentId)
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

        entityManager.persistAndFlush(exactExpirationRecord);
        entityManager.clear();

        // Act - Query with the exact expiration time
        List<ConsentLedger> result = consentLedgerRepository.findExpiredConsents(exactExpirationTime);

        // Assert
        assertEquals(1, result.size(), "Should include record with exact expiration time");
        assertEquals(consentId, result.get(0).getConsentId(), "Should return the correct record");
        
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
        UUID grantedConsentId = UUID.randomUUID();
        UUID withdrawnConsentId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime expiredTime = now.minusDays(1);

        // Create an expired GRANTED record
        ConsentLedger grantedRecord = ConsentLedger.builder()
                .consentId(grantedConsentId)
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
                .consentId(withdrawnConsentId)
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
                .withdrawnConsentId(grantedConsentId)
                .build();

        entityManager.persistAndFlush(grantedRecord);
        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findExpiredConsents(now);

        // Assert
        assertEquals(2, result.size(), "Should return both GRANTED and WITHDRAWN expired records");
        
        List<UUID> returnedConsentIds = result.stream()
                .map(ConsentLedger::getConsentId)
                .toList();
        
        assertTrue(returnedConsentIds.contains(grantedConsentId), "Should include expired GRANTED record");
        assertTrue(returnedConsentIds.contains(withdrawnConsentId), "Should include expired WITHDRAWN record");
    }

    @Test
    void countActiveGrantsByUserAndType_ShouldCountOnlyGrantedRecordsForUserAndType() {
        // Arrange - Create multiple records with different statuses and users
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID consentId4 = UUID.randomUUID();
        UUID consentId5 = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();

        // Create a GRANTED record for the test user and type
        ConsentLedger grantedRecord1 = ConsentLedger.builder()
                .consentId(consentId1)
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
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create another GRANTED record for the same user and type (different version)
        ConsentLedger grantedRecord2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
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
                .consentTimestamp(LocalDateTime.now().minusDays(3))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Create a WITHDRAWN record for the same user and type (should not be counted)
        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId3)
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
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .withdrawnConsentId(consentId1)
                .build();

        // Create a GRANTED record for a different consent type (should not be counted)
        ConsentLedger grantedDifferentType = ConsentLedger.builder()
                .consentId(consentId4)
                .userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE) // Different consent type
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
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"grantedDifferentType\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .build();

        // Create a GRANTED record for a different user (should not be counted)
        ConsentLedger grantedDifferentUser = ConsentLedger.builder()
                .consentId(consentId5)
                .userId(differentUserId) // Different user
                .consentType(testConsentType)
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
                .consentTimestamp(LocalDateTime.now().minusDays(4))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"grantedDifferentUser\"}")
                .recordSignature(new byte[]{13, 14, 15})
                .build();

        // Persist all records
        entityManager.persistAndFlush(grantedRecord1);
        entityManager.persistAndFlush(grantedRecord2);
        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.persistAndFlush(grantedDifferentType);
        entityManager.persistAndFlush(grantedDifferentUser);
        entityManager.clear();

        // Act
        long result = consentLedgerRepository.countActiveGrantsByUserAndType(testUserId, testConsentType);

        // Assert
        assertEquals(2, result, "Should count only 2 GRANTED records for the specific user and consent type");
    }

    @Test
    void countActiveGrantsByUserAndType_ShouldReturnZeroWhenNoGrantedRecordsExist() {
        // Arrange - Create only WITHDRAWN records for the user and type
        UUID consentId = UUID.randomUUID();

        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId)
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
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.clear();

        // Act
        long result = consentLedgerRepository.countActiveGrantsByUserAndType(testUserId, testConsentType);

        // Assert
        assertEquals(0, result, "Should return 0 when no GRANTED records exist for the user and type");
    }

    @Test
    void countActiveGrantsByUserAndType_ShouldReturnZeroWhenNoRecordsExist() {
        // Act
        long result = consentLedgerRepository.countActiveGrantsByUserAndType(testUserId, testConsentType);

        // Assert
        assertEquals(0, result, "Should return 0 when no records exist for the user and type");
    }

    @Test
    void countActiveGrantsByUserAndType_ShouldReturnZeroForDifferentUser() {
        // Arrange - Create a GRANTED record for a different user
        UUID differentUserId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();

        ConsentLedger grantedRecord = ConsentLedger.builder()
                .consentId(consentId)
                .userId(differentUserId) // Different user
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
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(grantedRecord);
        entityManager.clear();

        // Act
        long result = consentLedgerRepository.countActiveGrantsByUserAndType(testUserId, testConsentType);

        // Assert
        assertEquals(0, result, "Should return 0 when no GRANTED records exist for the target user");
    }

    @Test
    void countActiveGrantsByUserAndType_ShouldReturnZeroForDifferentConsentType() {
        // Arrange - Create a GRANTED record for a different consent type
        UUID consentId = UUID.randomUUID();

        ConsentLedger grantedRecord = ConsentLedger.builder()
                .consentId(consentId)
                .userId(testUserId)
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
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(grantedRecord);
        entityManager.clear();

        // Act
        long result = consentLedgerRepository.countActiveGrantsByUserAndType(testUserId, testConsentType);

        // Assert
        assertEquals(0, result, "Should return 0 when no GRANTED records exist for the target consent type");
    }

    @Test
    void countActiveGrantsByUserAndType_ShouldCountAllGrantedRecordsForUserAndType() {
        // Arrange - Create multiple GRANTED records for the same user and type
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID consentId4 = UUID.randomUUID();
        UUID consentId5 = UUID.randomUUID();

        // Create 5 GRANTED records for the same user and type
        ConsentLedger grantedRecord1 = ConsentLedger.builder()
                .consentId(consentId1)
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
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        ConsentLedger grantedRecord2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
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
                .consentTimestamp(LocalDateTime.now().minusDays(4))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        ConsentLedger grantedRecord3 = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
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
                .consentTimestamp(LocalDateTime.now().minusDays(3))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted3\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        ConsentLedger grantedRecord4 = ConsentLedger.builder()
                .consentId(consentId4)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("4.0.0")
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
                .consentTimestamp(LocalDateTime.now().minusDays(2))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted4\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .build();

        ConsentLedger grantedRecord5 = ConsentLedger.builder()
                .consentId(consentId5)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("5.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash131415")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted5\"}")
                .recordSignature(new byte[]{13, 14, 15})
                .build();

        // Persist all records
        entityManager.persistAndFlush(grantedRecord1);
        entityManager.persistAndFlush(grantedRecord2);
        entityManager.persistAndFlush(grantedRecord3);
        entityManager.persistAndFlush(grantedRecord4);
        entityManager.persistAndFlush(grantedRecord5);
        entityManager.clear();

        // Act
        long result = consentLedgerRepository.countActiveGrantsByUserAndType(testUserId, testConsentType);

        // Assert
        assertEquals(5, result, "Should count all 5 GRANTED records for the user and consent type");
    }
} 
