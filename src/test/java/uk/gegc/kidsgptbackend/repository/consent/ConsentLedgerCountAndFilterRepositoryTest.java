package uk.gegc.kidsgptbackend.repository.consent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.model.consent.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ConsentLedgerCountAndFilterRepositoryTest {

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

    @Test
    void findByJurisdictionAndRegion_ShouldFilterByExactJurisdictionAndRegion() {
        // Arrange - Create records with different jurisdictions and regions
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID consentId4 = UUID.randomUUID();
        UUID consentId5 = UUID.randomUUID();

        // Create a record with exact jurisdiction and region
        ConsentLedger exactMatchRecord = ConsentLedger.builder()
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
                .receiptJson("{\"test\":\"exactMatch\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create another record with same jurisdiction and region
        ConsentLedger exactMatchRecord2 = ConsentLedger.builder()
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
                .consentTimestamp(LocalDateTime.now().minusDays(4))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"exactMatch2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Create a record with different jurisdiction
        ConsentLedger differentJurisdictionRecord = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash789")
                .jurisdiction("US") // Different jurisdiction
                .region("England")
                .locale("en-US")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(3))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"differentJurisdiction\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        // Create a record with different region
        ConsentLedger differentRegionRecord = ConsentLedger.builder()
                .consentId(consentId4)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash101112")
                .jurisdiction("GB")
                .region("Scotland") // Different region
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(2))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"differentRegion\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .build();

        // Create a record with null region
        ConsentLedger nullRegionRecord = ConsentLedger.builder()
                .consentId(consentId5)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash131415")
                .jurisdiction("GB")
                .region(null) // Null region
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"nullRegion\"}")
                .recordSignature(new byte[]{13, 14, 15})
                .build();

        // Persist all records
        entityManager.persistAndFlush(exactMatchRecord);
        entityManager.persistAndFlush(exactMatchRecord2);
        entityManager.persistAndFlush(differentJurisdictionRecord);
        entityManager.persistAndFlush(differentRegionRecord);
        entityManager.persistAndFlush(nullRegionRecord);
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findByJurisdictionAndRegion("GB", "England");

        // Assert
        assertEquals(2, result.size(), "Should return exactly 2 records with jurisdiction 'GB' and region 'England'");

        // Verify the returned records are the correct ones
        List<UUID> returnedConsentIds = result.stream()
                .map(ConsentLedger::getConsentId)
                .toList();

        assertTrue(returnedConsentIds.contains(consentId1), "Should include the first exact match record");
        assertTrue(returnedConsentIds.contains(consentId2), "Should include the second exact match record");
        assertFalse(returnedConsentIds.contains(consentId3), "Should not include record with different jurisdiction");
        assertFalse(returnedConsentIds.contains(consentId4), "Should not include record with different region");
        assertFalse(returnedConsentIds.contains(consentId5), "Should not include record with null region");

        // Verify all returned records have the correct jurisdiction and region
        result.forEach(record -> {
            assertEquals("GB", record.getJurisdiction(), "All returned records should have jurisdiction 'GB'");
            assertEquals("England", record.getRegion(), "All returned records should have region 'England'");
        });
    }

    @Test
    void findByJurisdictionAndRegion_ShouldPreserveCase() {
        // Arrange - Create records with different case variations
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();

        // Create a record with lowercase jurisdiction and region
        ConsentLedger lowercaseRecord = ConsentLedger.builder()
                .consentId(consentId1)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash123")
                .jurisdiction("gb") // lowercase
                .region("england") // lowercase
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(3))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"lowercase\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a record with uppercase jurisdiction and region
        ConsentLedger uppercaseRecord = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash456")
                .jurisdiction("GB") // uppercase
                .region("ENGLAND") // uppercase
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(2))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"uppercase\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Create a record with mixed case jurisdiction and region
        ConsentLedger mixedCaseRecord = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash789")
                .jurisdiction("Gb") // mixed case
                .region("England") // mixed case
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"mixedCase\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        // Persist all records
        entityManager.persistAndFlush(lowercaseRecord);
        entityManager.persistAndFlush(uppercaseRecord);
        entityManager.persistAndFlush(mixedCaseRecord);
        entityManager.clear();

        // Act - Search for lowercase
        List<ConsentLedger> lowercaseResult = consentLedgerRepository.findByJurisdictionAndRegion("gb", "england");

        // Assert - Should only find the lowercase record
        assertEquals(1, lowercaseResult.size(), "Should return exactly 1 record with lowercase jurisdiction and region");
        assertEquals(consentId1, lowercaseResult.get(0).getConsentId(), "Should return the lowercase record");

        // Act - Search for uppercase
        List<ConsentLedger> uppercaseResult = consentLedgerRepository.findByJurisdictionAndRegion("GB", "ENGLAND");

        // Assert - Should only find the uppercase record
        assertEquals(1, uppercaseResult.size(), "Should return exactly 1 record with uppercase jurisdiction and region");
        assertEquals(consentId2, uppercaseResult.get(0).getConsentId(), "Should return the uppercase record");

        // Act - Search for mixed case
        List<ConsentLedger> mixedCaseResult = consentLedgerRepository.findByJurisdictionAndRegion("Gb", "England");

        // Assert - Should only find the mixed case record
        assertEquals(1, mixedCaseResult.size(), "Should return exactly 1 record with mixed case jurisdiction and region");
        assertEquals(consentId3, mixedCaseResult.get(0).getConsentId(), "Should return the mixed case record");
    }

    @Test
    void findByJurisdictionAndRegion_ShouldReturnEmptyWhenNoMatches() {
        // Arrange - Create a record with different jurisdiction and region
        UUID consentId = UUID.randomUUID();

        ConsentLedger record = ConsentLedger.builder()
                .consentId(consentId)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash123")
                .jurisdiction("US")
                .region("California")
                .locale("en-US")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        entityManager.persistAndFlush(record);
        entityManager.clear();

        // Act - Search for non-existent jurisdiction and region
        List<ConsentLedger> result = consentLedgerRepository.findByJurisdictionAndRegion("GB", "England");

        // Assert
        assertTrue(result.isEmpty(), "Should return empty list when no records match the jurisdiction and region");
    }

    @Test
    void findByJurisdictionAndRegion_ShouldReturnEmptyWhenNoRecordsExist() {
        // Act - Search when no records exist
        List<ConsentLedger> result = consentLedgerRepository.findByJurisdictionAndRegion("GB", "England");

        // Assert
        assertTrue(result.isEmpty(), "Should return empty list when no records exist");
    }

    @Test
    void findByJurisdictionAndRegion_ShouldHandleNullRegion() {
        // Arrange - Create records with null region
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();

        // Create a record with null region
        ConsentLedger nullRegionRecord = ConsentLedger.builder()
                .consentId(consentId1)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash123")
                .jurisdiction("GB")
                .region(null) // null region
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(2))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"nullRegion\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a record with non-null region
        ConsentLedger nonNullRegionRecord = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash456")
                .jurisdiction("GB")
                .region("England") // non-null region
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"nonNullRegion\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        entityManager.persistAndFlush(nullRegionRecord);
        entityManager.persistAndFlush(nonNullRegionRecord);
        entityManager.clear();

        // Act - Search for null region
        List<ConsentLedger> nullRegionResult = consentLedgerRepository.findByJurisdictionAndRegion("GB", null);

        // Assert - Should only find the record with null region
        assertEquals(1, nullRegionResult.size(), "Should return exactly 1 record with null region");
        assertEquals(consentId1, nullRegionResult.get(0).getConsentId(), "Should return the record with null region");
        assertNull(nullRegionResult.get(0).getRegion(), "Returned record should have null region");

        // Act - Search for non-null region
        List<ConsentLedger> nonNullRegionResult = consentLedgerRepository.findByJurisdictionAndRegion("GB", "England");

        // Assert - Should only find the record with non-null region
        assertEquals(1, nonNullRegionResult.size(), "Should return exactly 1 record with non-null region");
        assertEquals(consentId2, nonNullRegionResult.get(0).getConsentId(), "Should return the record with non-null region");
        assertEquals("England", nonNullRegionResult.get(0).getRegion(), "Returned record should have region 'England'");
    }

    @Test
    void findByConsentTimestampBetween_ShouldIncludeBoundariesAndExcludeOutOfRangeRows() {
        // Arrange - Create records with different timestamps
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID consentId4 = UUID.randomUUID();
        UUID consentId5 = UUID.randomUUID();

        LocalDateTime baseTime = LocalDateTime.now().withNano(0); // Remove nanoseconds for consistent testing
        LocalDateTime fromDate = baseTime.minusDays(2);
        LocalDateTime toDate = baseTime.plusDays(2);

        // Create a record before the range (should be excluded)
        ConsentLedger beforeRangeRecord = ConsentLedger.builder()
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
                .consentTimestamp(baseTime.minusDays(3)) // Before range
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"beforeRange\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a record at the from boundary (should be included)
        ConsentLedger fromBoundaryRecord = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
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
                .consentTimestamp(fromDate) // At from boundary
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"fromBoundary\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Create a record within the range (should be included)
        ConsentLedger withinRangeRecord = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
                .consentType(testConsentType)
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
                .consentTimestamp(baseTime) // Within range
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withinRange\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        // Create a record at the to boundary (should be included)
        ConsentLedger toBoundaryRecord = ConsentLedger.builder()
                .consentId(consentId4)
                .userId(testUserId)
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
                .consentTimestamp(toDate) // At to boundary
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"toBoundary\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .build();

        // Create a record after the range (should be excluded)
        ConsentLedger afterRangeRecord = ConsentLedger.builder()
                .consentId(consentId5)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
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
                .consentTimestamp(baseTime.plusDays(3)) // After range
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"afterRange\"}")
                .recordSignature(new byte[]{13, 14, 15})
                .build();

        // Persist all records
        entityManager.persistAndFlush(beforeRangeRecord);
        entityManager.persistAndFlush(fromBoundaryRecord);
        entityManager.persistAndFlush(withinRangeRecord);
        entityManager.persistAndFlush(toBoundaryRecord);
        entityManager.persistAndFlush(afterRangeRecord);
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findByConsentTimestampBetween(fromDate, toDate);

        // Assert
        assertEquals(3, result.size(), "Should return exactly 3 records within the range (including boundaries)");
        
        // Verify the returned records are the correct ones
        List<UUID> returnedConsentIds = result.stream()
                .map(ConsentLedger::getConsentId)
                .toList();
        
        assertTrue(returnedConsentIds.contains(consentId2), "Should include the from boundary record");
        assertTrue(returnedConsentIds.contains(consentId3), "Should include the within range record");
        assertTrue(returnedConsentIds.contains(consentId4), "Should include the to boundary record");
        assertFalse(returnedConsentIds.contains(consentId1), "Should not include the before range record");
        assertFalse(returnedConsentIds.contains(consentId5), "Should not include the after range record");

        // Verify all returned records have timestamps within the range
        result.forEach(record -> {
            assertTrue(record.getConsentTimestamp().isAfter(fromDate.minusNanos(1)) || record.getConsentTimestamp().equals(fromDate), 
                    "All returned records should have timestamp >= fromDate");
            assertTrue(record.getConsentTimestamp().isBefore(toDate.plusNanos(1)) || record.getConsentTimestamp().equals(toDate), 
                    "All returned records should have timestamp <= toDate");
        });
    }

    @Test
    void findByConsentTimestampBetween_ShouldReturnEmptyWhenNoRecordsInRange() {
        // Arrange - Create records outside the range
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();

        LocalDateTime baseTime = LocalDateTime.now().withNano(0);
        LocalDateTime fromDate = baseTime.minusDays(1);
        LocalDateTime toDate = baseTime.plusDays(1);

        // Create a record before the range
        ConsentLedger beforeRangeRecord = ConsentLedger.builder()
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
                .consentTimestamp(baseTime.minusDays(3)) // Before range
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"beforeRange\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a record after the range
        ConsentLedger afterRangeRecord = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
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
                .consentTimestamp(baseTime.plusDays(3)) // After range
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"afterRange\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        entityManager.persistAndFlush(beforeRangeRecord);
        entityManager.persistAndFlush(afterRangeRecord);
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findByConsentTimestampBetween(fromDate, toDate);

        // Assert
        assertTrue(result.isEmpty(), "Should return empty list when no records are within the range");
    }

    @Test
    void findByConsentTimestampBetween_ShouldReturnEmptyWhenNoRecordsExist() {
        // Arrange
        LocalDateTime fromDate = LocalDateTime.now().minusDays(1);
        LocalDateTime toDate = LocalDateTime.now().plusDays(1);

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findByConsentTimestampBetween(fromDate, toDate);

        // Assert
        assertTrue(result.isEmpty(), "Should return empty list when no records exist");
    }

    @Test
    void findByConsentTimestampBetween_ShouldHandleSameFromAndToDate() {
        // Arrange - Create records with the same timestamp
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();

        LocalDateTime exactTime = LocalDateTime.now().withNano(0);

        // Create a record at the exact time
        ConsentLedger exactTimeRecord = ConsentLedger.builder()
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
                .consentTimestamp(exactTime)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"exactTime\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a record at a different time
        ConsentLedger differentTimeRecord = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
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
                .consentTimestamp(exactTime.plusHours(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"differentTime\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        entityManager.persistAndFlush(exactTimeRecord);
        entityManager.persistAndFlush(differentTimeRecord);
        entityManager.clear();

        // Act - Search with same from and to date
        List<ConsentLedger> result = consentLedgerRepository.findByConsentTimestampBetween(exactTime, exactTime);

        // Assert
        assertEquals(1, result.size(), "Should return exactly 1 record when from and to dates are the same");
        assertEquals(consentId1, result.get(0).getConsentId(), "Should return the record with the exact timestamp");
        assertEquals(exactTime, result.get(0).getConsentTimestamp(), "Returned record should have the exact timestamp");
    }

    @Test
    void findByConsentTimestampBetween_ShouldHandleDifferentConsentStatuses() {
        // Arrange - Create records with different statuses within the range
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();

        LocalDateTime baseTime = LocalDateTime.now().withNano(0);
        LocalDateTime fromDate = baseTime.minusDays(1);
        LocalDateTime toDate = baseTime.plusDays(1);

        // Create a GRANTED record within range
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
                .consentTimestamp(baseTime)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Create a WITHDRAWN record within range
        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
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
                .consentTimestamp(baseTime.plusHours(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .withdrawnConsentId(consentId1)
                .build();

        // Create an EXPIRED record within range
        ConsentLedger expiredRecord = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.EXPIRED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash789")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(baseTime.plusHours(2))
                .retentionExpiresAt(LocalDateTime.now().minusDays(1)) // Expired
                .receiptJson("{\"test\":\"expired\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        entityManager.persistAndFlush(grantedRecord);
        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.persistAndFlush(expiredRecord);
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findByConsentTimestampBetween(fromDate, toDate);

        // Assert
        assertEquals(3, result.size(), "Should return all 3 records regardless of consent status");
        
        // Verify all statuses are included
        List<ConsentStatus> returnedStatuses = result.stream()
                .map(ConsentLedger::getConsentStatus)
                .toList();
        
        assertTrue(returnedStatuses.contains(ConsentStatus.GRANTED), "Should include GRANTED records");
        assertTrue(returnedStatuses.contains(ConsentStatus.WITHDRAWN), "Should include WITHDRAWN records");
        assertTrue(returnedStatuses.contains(ConsentStatus.EXPIRED), "Should include EXPIRED records");
    }

    @Test
    void findByParentVerificationId_ShouldReturnRowsMatchingVerificationId() {
        // Arrange - Create records with different parent verification IDs
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID consentId4 = UUID.randomUUID();
        UUID consentId5 = UUID.randomUUID();

        UUID targetVerificationId = UUID.randomUUID();
        UUID differentVerificationId = UUID.randomUUID();

        // Create a record with the target verification ID
        ConsentLedger targetRecord1 = ConsentLedger.builder()
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"target1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .parentVerificationId(targetVerificationId)
                .build();

        // Create another record with the same target verification ID
        ConsentLedger targetRecord2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
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
                .consentTimestamp(LocalDateTime.now().plusHours(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"target2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .parentVerificationId(targetVerificationId)
                .withdrawnConsentId(consentId1)
                .build();

        // Create a record with a different verification ID
        ConsentLedger differentRecord = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
                .consentType(testConsentType)
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
                .consentTimestamp(LocalDateTime.now().plusHours(2))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"different\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .parentVerificationId(differentVerificationId)
                .build();

        // Create a record with null verification ID
        ConsentLedger nullRecord = ConsentLedger.builder()
                .consentId(consentId4)
                .userId(testUserId)
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
                .consentTimestamp(LocalDateTime.now().plusHours(3))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"null\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .parentVerificationId(null)
                .build();

        // Create a record with no verification ID field set
        ConsentLedger noVerificationRecord = ConsentLedger.builder()
                .consentId(consentId5)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
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
                .consentTimestamp(LocalDateTime.now().plusHours(4))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"noVerification\"}")
                .recordSignature(new byte[]{13, 14, 15})
                .build();

        // Persist all records
        entityManager.persistAndFlush(targetRecord1);
        entityManager.persistAndFlush(targetRecord2);
        entityManager.persistAndFlush(differentRecord);
        entityManager.persistAndFlush(nullRecord);
        entityManager.persistAndFlush(noVerificationRecord);
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findByParentVerificationId(targetVerificationId);

        // Assert
        assertEquals(2, result.size(), "Should return exactly 2 records matching the target verification ID");
        
        // Verify the returned records are the correct ones
        List<UUID> returnedConsentIds = result.stream()
                .map(ConsentLedger::getConsentId)
                .toList();
        
        assertTrue(returnedConsentIds.contains(consentId1), "Should include the first target record");
        assertTrue(returnedConsentIds.contains(consentId2), "Should include the second target record");
        assertFalse(returnedConsentIds.contains(consentId3), "Should not include the different verification ID record");
        assertFalse(returnedConsentIds.contains(consentId4), "Should not include the null verification ID record");
        assertFalse(returnedConsentIds.contains(consentId5), "Should not include the no verification ID record");

        // Verify all returned records have the correct parent verification ID
        result.forEach(record -> {
            assertEquals(targetVerificationId, record.getParentVerificationId(), 
                    "All returned records should have the target parent verification ID");
        });
    }

    @Test
    void findByParentVerificationId_ShouldReturnEmptyWhenNoMatchingRecords() {
        // Arrange - Create records with different verification IDs
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();

        UUID existingVerificationId1 = UUID.randomUUID();
        UUID existingVerificationId2 = UUID.randomUUID();
        UUID nonExistentVerificationId = UUID.randomUUID();

        // Create a record with first verification ID
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .parentVerificationId(existingVerificationId1)
                .build();

        // Create a record with second verification ID
        ConsentLedger record2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
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
                .consentTimestamp(LocalDateTime.now().plusHours(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .parentVerificationId(existingVerificationId2)
                .build();

        entityManager.persistAndFlush(record1);
        entityManager.persistAndFlush(record2);
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findByParentVerificationId(nonExistentVerificationId);

        // Assert
        assertTrue(result.isEmpty(), "Should return empty list when no records match the verification ID");
    }

    @Test
    void findByParentVerificationId_ShouldReturnEmptyWhenNoRecordsExist() {
        // Arrange
        UUID nonExistentVerificationId = UUID.randomUUID();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findByParentVerificationId(nonExistentVerificationId);

        // Assert
        assertTrue(result.isEmpty(), "Should return empty list when no records exist");
    }

    @Test
    void findByParentVerificationId_ShouldHandleDifferentConsentStatuses() {
        // Arrange - Create records with different statuses but same verification ID
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();

        UUID targetVerificationId = UUID.randomUUID();

        // Create a GRANTED record with target verification ID
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"granted\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .parentVerificationId(targetVerificationId)
                .build();

        // Create a WITHDRAWN record with target verification ID
        ConsentLedger withdrawnRecord = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
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
                .consentTimestamp(LocalDateTime.now().plusHours(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawn\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .parentVerificationId(targetVerificationId)
                .withdrawnConsentId(consentId1)
                .build();

        // Create an EXPIRED record with target verification ID
        ConsentLedger expiredRecord = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
                .consentType(testConsentType)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.EXPIRED)
                .policyUrl("https://example.com/policy")
                .contentHash("hash789")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().plusHours(2))
                .retentionExpiresAt(LocalDateTime.now().minusDays(1)) // Expired
                .receiptJson("{\"test\":\"expired\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .parentVerificationId(targetVerificationId)
                .build();

        entityManager.persistAndFlush(grantedRecord);
        entityManager.persistAndFlush(withdrawnRecord);
        entityManager.persistAndFlush(expiredRecord);
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findByParentVerificationId(targetVerificationId);

        // Assert
        assertEquals(3, result.size(), "Should return all 3 records regardless of consent status");
        
        // Verify all statuses are included
        List<ConsentStatus> returnedStatuses = result.stream()
                .map(ConsentLedger::getConsentStatus)
                .toList();
        
        assertTrue(returnedStatuses.contains(ConsentStatus.GRANTED), "Should include GRANTED records");
        assertTrue(returnedStatuses.contains(ConsentStatus.WITHDRAWN), "Should include WITHDRAWN records");
        assertTrue(returnedStatuses.contains(ConsentStatus.EXPIRED), "Should include EXPIRED records");
    }

    @Test
    void findByParentVerificationId_ShouldHandleMultipleRecordsWithSameVerificationId() {
        // Arrange - Create multiple records with the same verification ID
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID consentId4 = UUID.randomUUID();

        UUID targetVerificationId = UUID.randomUUID();

        // Create multiple records with the same verification ID
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
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .parentVerificationId(targetVerificationId)
                .build();

        ConsentLedger record2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(testUserId)
                .consentType(testConsentType)
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
                .consentTimestamp(LocalDateTime.now().plusHours(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .parentVerificationId(targetVerificationId)
                .build();

        ConsentLedger record3 = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
                .consentType(testConsentType)
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
                .consentTimestamp(LocalDateTime.now().plusHours(2))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record3\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .parentVerificationId(targetVerificationId)
                .build();

        ConsentLedger record4 = ConsentLedger.builder()
                .consentId(consentId4)
                .userId(testUserId)
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
                .consentTimestamp(LocalDateTime.now().plusHours(3))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record4\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .parentVerificationId(targetVerificationId)
                .build();

        entityManager.persistAndFlush(record1);
        entityManager.persistAndFlush(record2);
        entityManager.persistAndFlush(record3);
        entityManager.persistAndFlush(record4);
        entityManager.clear();

        // Act
        List<ConsentLedger> result = consentLedgerRepository.findByParentVerificationId(targetVerificationId);

        // Assert
        assertEquals(4, result.size(), "Should return all 4 records with the same verification ID");
        
        // Verify all returned records have the correct verification ID
        result.forEach(record -> {
            assertEquals(targetVerificationId, record.getParentVerificationId(), 
                    "All returned records should have the target parent verification ID");
        });

        // Verify all expected consent IDs are returned
        List<UUID> returnedConsentIds = result.stream()
                .map(ConsentLedger::getConsentId)
                .toList();
        
        assertTrue(returnedConsentIds.contains(consentId1), "Should include record1");
        assertTrue(returnedConsentIds.contains(consentId2), "Should include record2");
        assertTrue(returnedConsentIds.contains(consentId3), "Should include record3");
        assertTrue(returnedConsentIds.contains(consentId4), "Should include record4");
    }
} 