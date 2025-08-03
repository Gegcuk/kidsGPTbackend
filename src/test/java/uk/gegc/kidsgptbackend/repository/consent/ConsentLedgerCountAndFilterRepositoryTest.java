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
} 