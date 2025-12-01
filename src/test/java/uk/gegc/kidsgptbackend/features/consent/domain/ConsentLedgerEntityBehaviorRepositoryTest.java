package uk.gegc.kidsgptbackend.features.consent.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.features.consent.domain.model.*;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentLedgerRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ConsentLedgerEntityBehaviorRepositoryTest {

    @Autowired
    private ConsentLedgerRepository consentLedgerRepository;

    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    private UUID testUserId;
    private ConsentType testConsentType;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testConsentType = ConsentType.PRIVACY_POLICY;
    }

    @Test
    void entityPersistenceDefaults_ShouldAutoPopulateCreatedAtInUTCWhenNull() {
        // Arrange - Create a ConsentLedger with null createdAt
        LocalDateTime beforeInsert = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        
        ConsentLedger consentLedger = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)                .consentType(testConsentType)
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
                .receiptJson("{\"test\":\"data\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .createdAt(null) // Explicitly set to null to test @PrePersist
                .build();

        // Act - Save the entity and get its auto-generated ID
        ConsentLedger savedLedger = consentLedgerRepository.save(consentLedger);
        entityManager.flush();
        entityManager.clear();

        // Assert - Verify createdAt was auto-populated
        ConsentLedger persistedLedger = entityManager.find(ConsentLedger.class, savedLedger.getConsentId());
        assertNotNull(persistedLedger, "Entity should be persisted");
        assertNotNull(persistedLedger.getCreatedAt(), "createdAt should be auto-populated");
        
        // Verify the timestamp is in UTC and reasonable (within a few seconds of before insert)
        LocalDateTime afterInsert = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        assertTrue(persistedLedger.getCreatedAt().isAfter(beforeInsert.minusSeconds(5)), 
                "createdAt should be after or close to before insert time");
        assertTrue(persistedLedger.getCreatedAt().isBefore(afterInsert.plusSeconds(5)), 
                "createdAt should be before or close to after insert time");
        
        // Verify it's in UTC (should match the system's UTC time)
        LocalDateTime expectedUTC = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        assertTrue(Math.abs(persistedLedger.getCreatedAt().toEpochSecond(ZoneOffset.UTC) - 
                           expectedUTC.toEpochSecond(ZoneOffset.UTC)) < 10, 
                "createdAt should be in UTC timezone");
    }

    @Test
    void entityPersistenceDefaults_ShouldNotOverrideExistingCreatedAt() {
        // Arrange - Create a ConsentLedger with a specific createdAt value
        LocalDateTime specificCreatedAt = LocalDateTime.of(2023, 1, 1, 12, 0, 0);
        
        ConsentLedger consentLedger = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .receiptJson("{\"test\":\"data\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .createdAt(specificCreatedAt) // Set a specific value
                .build();

        // Act - Save the entity and get its auto-generated ID
        ConsentLedger savedLedger = consentLedgerRepository.save(consentLedger);
        entityManager.flush();
        entityManager.clear();

        // Assert - Verify createdAt was not overridden
        ConsentLedger persistedLedger = entityManager.find(ConsentLedger.class, savedLedger.getConsentId());
        assertNotNull(persistedLedger, "Entity should be persisted");
        assertEquals(specificCreatedAt, persistedLedger.getCreatedAt(), 
                "createdAt should not be overridden when already set");
    }

    @Test
    void entityPersistenceDefaults_ShouldHandleMultipleInsertsWithNullCreatedAt() {
        // Arrange - Create multiple ConsentLedger entities with null createdAt
        
        ConsentLedger consentLedger1 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .receiptJson("{\"test\":\"data1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .createdAt(null)
                .build();

        ConsentLedger consentLedger2 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .receiptJson("{\"test\":\"data2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .createdAt(null)
                .build();

        ConsentLedger consentLedger3 = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .receiptJson("{\"test\":\"data3\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .createdAt(null)
                .build();

        // Act - Save all entities and get their auto-generated IDs
        ConsentLedger savedLedger1 = consentLedgerRepository.save(consentLedger1);
        ConsentLedger savedLedger2 = consentLedgerRepository.save(consentLedger2);
        ConsentLedger savedLedger3 = consentLedgerRepository.save(consentLedger3);
        entityManager.flush();
        entityManager.clear();

        // Assert - Verify all entities have auto-populated createdAt
        ConsentLedger persisted1 = entityManager.find(ConsentLedger.class, savedLedger1.getConsentId());
        ConsentLedger persisted2 = entityManager.find(ConsentLedger.class, savedLedger2.getConsentId());
        ConsentLedger persisted3 = entityManager.find(ConsentLedger.class, savedLedger3.getConsentId());

        assertNotNull(persisted1.getCreatedAt(), "First entity should have auto-populated createdAt");
        assertNotNull(persisted2.getCreatedAt(), "Second entity should have auto-populated createdAt");
        assertNotNull(persisted3.getCreatedAt(), "Third entity should have auto-populated createdAt");

        // Verify all timestamps are in UTC
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        assertTrue(persisted1.getCreatedAt().isBefore(now.plusSeconds(5)), 
                "First entity createdAt should be in UTC");
        assertTrue(persisted2.getCreatedAt().isBefore(now.plusSeconds(5)), 
                "Second entity createdAt should be in UTC");
        assertTrue(persisted3.getCreatedAt().isBefore(now.plusSeconds(5)), 
                "Third entity createdAt should be in UTC");
    }

    @Test
    void entityPersistenceDefaults_ShouldVerifyPrecisionAndZoneExpectations() {
        // Arrange - Create a ConsentLedger with null createdAt
        
        ConsentLedger consentLedger = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)
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
                .receiptJson("{\"test\":\"data\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .createdAt(null)
                .build();

        // Act - Save the entity and get its auto-generated ID
        ConsentLedger savedLedger = consentLedgerRepository.save(consentLedger);
        entityManager.flush();
        entityManager.clear();

        // Assert - Verify precision and zone expectations
        ConsentLedger persistedLedger = entityManager.find(ConsentLedger.class, savedLedger.getConsentId());
        assertNotNull(persistedLedger.getCreatedAt(), "createdAt should be auto-populated");

        // Verify the timestamp has reasonable precision (nanoseconds are acceptable for LocalDateTime)
        LocalDateTime createdAt = persistedLedger.getCreatedAt();
        assertTrue(createdAt.getNano() >= 0 && createdAt.getNano() < 1_000_000_000, 
                "createdAt should have valid nanosecond precision");

        // Verify it's in UTC timezone by comparing with system UTC time
        LocalDateTime systemUTC = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        long timeDiffSeconds = Math.abs(createdAt.toEpochSecond(ZoneOffset.UTC) - 
                                       systemUTC.toEpochSecond(ZoneOffset.UTC));
        assertTrue(timeDiffSeconds < 10, "createdAt should be in UTC timezone and close to current time");
    }
} 