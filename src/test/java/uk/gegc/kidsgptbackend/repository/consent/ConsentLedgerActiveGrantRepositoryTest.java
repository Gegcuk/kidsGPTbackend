package uk.gegc.kidsgptbackend.repository.consent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.model.consent.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ConsentLedgerActiveGrantRepositoryTest {

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

} 