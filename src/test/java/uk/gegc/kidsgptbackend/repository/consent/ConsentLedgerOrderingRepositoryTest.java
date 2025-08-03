package uk.gegc.kidsgptbackend.repository.consent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.model.consent.*;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ConsentLedgerOrderingRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConsentLedgerRepository consentLedgerRepository;

    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
    }

    @Test
    void findByUserIdOrderByConsentTimestampDescCreatedAtDesc_SameConsentTimestampDifferentCreatedAt_ReturnsDeterministicOrder() {
        // Given: multiple rows with same consentTimestamp but different createdAt
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();

        LocalDateTime sameConsentTimestamp = LocalDateTime.of(2024, 1, 15, 12, 0, 0);
        LocalDateTime createdAt1 = LocalDateTime.of(2024, 1, 15, 12, 0, 10); // Latest
        LocalDateTime createdAt2 = LocalDateTime.of(2024, 1, 15, 12, 0, 5);  // Middle
        LocalDateTime createdAt3 = LocalDateTime.of(2024, 1, 15, 12, 0, 1);  // Earliest

        // Create records with same consentTimestamp but different createdAt values
        ConsentLedger record1 = ConsentLedger.builder()
                .consentId(consentId1)
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
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
                .consentType(ConsentType.DATA_PROCESSING)
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

        // Persist records in non-chronological order to ensure ordering is not based on insertion order
        entityManager.persistAndFlush(record3); // Earliest createdAt
        entityManager.persistAndFlush(record1); // Latest createdAt
        entityManager.persistAndFlush(record2); // Middle createdAt
        entityManager.clear();

        // When: Call the repository method
        var pageable = PageRequest.of(0, 10);
        var result = consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(testUserId, pageable);

        // Then: result order is desc by consentTimestamp, then desc by createdAt (deterministic)
        assertTrue(result.hasContent(), "Should return content");
        var content = result.getContent();
        assertEquals(3, content.size(), "Should return all 3 records");

        // Verify order: consentTimestamp DESC, then createdAt DESC
        // Since all have same consentTimestamp, order should be by createdAt DESC
        assertEquals(consentId1, content.get(0).getConsentId(), 
                "First record should be the one with latest createdAt (record1)");
        assertEquals(consentId2, content.get(1).getConsentId(), 
                "Second record should be the one with middle createdAt (record2)");
        assertEquals(consentId3, content.get(2).getConsentId(), 
                "Third record should be the one with earliest createdAt (record3)");

        // Verify all records have the same consentTimestamp
        content.forEach(record -> {
            assertEquals(sameConsentTimestamp, record.getConsentTimestamp(), 
                    "All records should have the same consentTimestamp");
        });

        // Verify createdAt values are in descending order
        assertTrue(content.get(0).getCreatedAt().isAfter(content.get(1).getCreatedAt()), 
                "First record should have later createdAt than second");
        assertTrue(content.get(1).getCreatedAt().isAfter(content.get(2).getCreatedAt()), 
                "Second record should have later createdAt than third");
    }

    @Test
    void findByUserIdOrderByConsentTimestampDescCreatedAtDesc_DifferentConsentTimestamps_OrdersByConsentTimestampFirst() {
        // Given: records with different consentTimestamps
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();

        LocalDateTime consentTimestamp1 = LocalDateTime.of(2024, 1, 15, 12, 0, 0); // Latest
        LocalDateTime consentTimestamp2 = LocalDateTime.of(2024, 1, 15, 11, 0, 0); // Middle
        LocalDateTime consentTimestamp3 = LocalDateTime.of(2024, 1, 15, 10, 0, 0); // Earliest

        // Create records with different consentTimestamps but same createdAt
        LocalDateTime sameCreatedAt = LocalDateTime.of(2024, 1, 15, 12, 0, 0);

        ConsentLedger record1 = ConsentLedger.builder()
                .consentId(consentId1)
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
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
                .consentTimestamp(consentTimestamp1)
                .createdAt(sameCreatedAt)
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
                .consentTimestamp(consentTimestamp2)
                .createdAt(sameCreatedAt)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        ConsentLedger record3 = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(testUserId)
                .consentType(ConsentType.DATA_PROCESSING)
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
                .consentTimestamp(consentTimestamp3)
                .createdAt(sameCreatedAt)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"record3\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        // Persist records in non-chronological order
        entityManager.persistAndFlush(record3); // Earliest consentTimestamp
        entityManager.persistAndFlush(record1); // Latest consentTimestamp
        entityManager.persistAndFlush(record2); // Middle consentTimestamp
        entityManager.clear();

        // When: Call the repository method
        var pageable = PageRequest.of(0, 10);
        var result = consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(testUserId, pageable);

        // Then: result order is desc by consentTimestamp, then desc by createdAt
        assertTrue(result.hasContent(), "Should return content");
        var content = result.getContent();
        assertEquals(3, content.size(), "Should return all 3 records");

        // Verify order: consentTimestamp DESC first (since they're different)
        assertEquals(consentId1, content.get(0).getConsentId(), 
                "First record should be the one with latest consentTimestamp (record1)");
        assertEquals(consentId2, content.get(1).getConsentId(), 
                "Second record should be the one with middle consentTimestamp (record2)");
        assertEquals(consentId3, content.get(2).getConsentId(), 
                "Third record should be the one with earliest consentTimestamp (record3)");

        // Verify consentTimestamp values are in descending order
        assertTrue(content.get(0).getConsentTimestamp().isAfter(content.get(1).getConsentTimestamp()), 
                "First record should have later consentTimestamp than second");
        assertTrue(content.get(1).getConsentTimestamp().isAfter(content.get(2).getConsentTimestamp()), 
                "Second record should have later consentTimestamp than third");
    }
} 