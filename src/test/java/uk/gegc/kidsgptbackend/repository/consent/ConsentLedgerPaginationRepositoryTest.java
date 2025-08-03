package uk.gegc.kidsgptbackend.repository.consent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.model.consent.*;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class ConsentLedgerPaginationRepositoryTest {

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
} 