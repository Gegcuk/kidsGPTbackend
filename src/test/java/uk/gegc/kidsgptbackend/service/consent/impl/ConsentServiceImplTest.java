package uk.gegc.kidsgptbackend.service.consent.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.dto.consent.ConsentGrantRequest;
import uk.gegc.kidsgptbackend.dto.consent.ConsentStatusResponse;
import uk.gegc.kidsgptbackend.model.consent.*;
import uk.gegc.kidsgptbackend.repository.consent.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.repository.consent.ParentVerificationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentServiceImplTest {

    @Mock
    private ConsentLedgerRepository consentLedgerRepository;

    @Mock
    private ConsentChildCoverageRepository consentChildCoverageRepository;

    @Mock
    private ParentVerificationRepository parentVerificationRepository;

    @InjectMocks
    private ConsentServiceImpl consentService;

    private ConsentGrantRequest validRequest;
    private UUID testUserId;
    private UUID testVerificationId;
    private List<UUID> testKids;

    @BeforeEach
    void setUp() {
        // Set up configuration values
        ReflectionTestUtils.setField(consentService, "hmacSecret", "test-hmac-secret-key");
        ReflectionTestUtils.setField(consentService, "defaultRetentionYears", 7);

        // Create test data
        testUserId = UUID.randomUUID();
        testVerificationId = UUID.randomUUID();
        testKids = List.of(UUID.randomUUID(), UUID.randomUUID());

        validRequest = new ConsentGrantRequest(
                testUserId,
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                "https://example.com/privacy",
                "abc123hash",
                testVerificationId,
                "GB",
                "England",
                "en-GB",
                ConsentSource.WEB,
                testKids,
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );
    }

    @Test
    void grantConsent_Success_ShouldCreateConsentLedgerAndChildCoverage() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        ConsentStatusResponse response = consentService.grantConsent(validRequest);

        // Assert
        assertNotNull(response);
        assertFalse(response.reconsentNeeded());
        assertNotNull(response.latestByType());
        assertEquals(1, response.latestByType().size());

        // Verify consent ledger was saved with correct data
        verify(consentLedgerRepository).save(argThat(consent -> 
                consent.getUserId().equals(testUserId) &&
                consent.getConsentType().equals(ConsentType.PRIVACY_POLICY) &&
                consent.getConsentStatus().equals(ConsentStatus.GRANTED) &&
                consent.getConsentVersion().equals("1.0.0") &&
                consent.getPolicyUrl().equals("https://example.com/privacy") &&
                consent.getContentHash().equals("abc123hash") &&
                consent.getJurisdiction().equals("GB") &&
                consent.getRegion().equals("England") &&
                consent.getLocale().equals("en-GB") &&
                consent.getSource().equals(ConsentSource.WEB) &&
                consent.getIpAddress().equals("192.168.1.1") &&
                consent.getUserAgent().equals("Mozilla/5.0") &&
                consent.getLawfulBasis().equals(LawfulBasis.CONSENT) &&
                consent.getParentVerificationId().equals(testVerificationId) &&
                consent.getReceiptJson() != null &&
                consent.getRecordSignature() != null &&
                consent.getRetentionExpiresAt() != null
        ));

        // Verify child coverage records were created
        verify(consentChildCoverageRepository).saveAll(argThat(coverageList -> {
            List<ConsentChildCoverage> list = (List<ConsentChildCoverage>) coverageList;
            return list.size() == 2 &&
                    list.stream().allMatch(coverage -> 
                            coverage.getConsentId().equals(savedConsent.getConsentId()) &&
                            testKids.contains(coverage.getKidId())
                    );
        }));
    }

    @Test
    void grantConsent_WithNullVerificationId_ShouldHandleGracefully() {
        // Arrange
        ConsentGrantRequest requestWithoutVerification = new ConsentGrantRequest(
                testUserId,
                ConsentType.TERMS_OF_SERVICE,
                "2.0.0",
                "https://example.com/terms",
                "def456hash",
                null, // null verification ID
                "US",
                "CA",
                "en-US",
                ConsentSource.IOS,
                testKids,
                "10.0.0.1",
                "iOS App",
                LawfulBasis.CONTRACT
        );

        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.TERMS_OF_SERVICE) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        ConsentStatusResponse response = consentService.grantConsent(requestWithoutVerification);

        // Assert
        assertNotNull(response);
        verify(consentLedgerRepository).save(argThat(consent -> 
                consent.getParentVerificationId() == null
        ));
    }

    @Test
    void grantConsent_WithEmptyKidsList_ShouldHandleGracefully() {
        // Arrange
        ConsentGrantRequest requestWithNoKids = new ConsentGrantRequest(
                testUserId,
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "https://example.com/parental",
                "ghi789hash",
                testVerificationId,
                "AU",
                null,
                "en-AU",
                ConsentSource.ANDROID,
                List.of(), // empty kids list
                "172.16.0.1",
                "Android App",
                LawfulBasis.LEGITIMATE_INTEREST
        );

        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PARENTAL_CONSENT) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        ConsentStatusResponse response = consentService.grantConsent(requestWithNoKids);

        // Assert
        assertNotNull(response);
        verify(consentChildCoverageRepository).saveAll(argThat(coverageList -> {
            List<ConsentChildCoverage> list = (List<ConsentChildCoverage>) coverageList;
            return list.isEmpty();
        }));
    }

    @Test
    void grantConsent_ShouldGenerateValidReceiptJson() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        consentService.grantConsent(validRequest);

        // Assert
        verify(consentLedgerRepository).save(argThat(consent -> {
            String receiptJson = consent.getReceiptJson();
            assertNotNull(receiptJson);
            assertTrue(receiptJson.contains("\"consentType\":\"PRIVACY_POLICY\""));
            assertTrue(receiptJson.contains("\"consentVersion\":\"1.0.0\""));
            assertTrue(receiptJson.contains("\"policyUrl\":\"https://example.com/privacy\""));
            assertTrue(receiptJson.contains("\"jurisdiction\":\"GB\""));
            assertTrue(receiptJson.contains("\"lawfulBasis\":\"CONSENT\""));
            assertTrue(receiptJson.contains("\"source\":\"WEB\""));
            assertTrue(receiptJson.contains("\"parentVerificationId\":\"" + testVerificationId + "\""));
            assertTrue(receiptJson.contains("\"kids\":["));
            return true;
        }));
    }

    @Test
    void grantConsent_ShouldGenerateHmacSignature() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        consentService.grantConsent(validRequest);

        // Assert
        verify(consentLedgerRepository).save(argThat(consent -> {
            byte[] signature = consent.getRecordSignature();
            assertNotNull(signature);
            assertTrue(signature.length > 0);
            return true;
        }));
    }

    @Test
    void grantConsent_ShouldSetRetentionExpiryDate() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        LocalDateTime beforeCall = LocalDateTime.now();

        // Act
        consentService.grantConsent(validRequest);

        // Assert
        verify(consentLedgerRepository).save(argThat(consent -> {
            LocalDateTime retentionExpiresAt = consent.getRetentionExpiresAt();
            assertNotNull(retentionExpiresAt);
            assertTrue(retentionExpiresAt.isAfter(beforeCall.plusYears(6)));
            assertTrue(retentionExpiresAt.isBefore(beforeCall.plusYears(8)));
            return true;
        }));
    }

    @Test
    void grantConsent_WhenReconsentNeeded_ShouldReturnTrue() {
        // Arrange
        ConsentLedger existingConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("0.9.0") // Different version
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(existingConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        ConsentStatusResponse response = consentService.grantConsent(validRequest);

        // Assert
        assertNotNull(response);
        assertTrue(response.reconsentNeeded());
    }

    @Test
    void grantConsent_WhenNoExistingConsent_ShouldReturnFalseForReconsent() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                    eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                    .thenReturn(Optional.empty());
        }

        // Act
        ConsentStatusResponse response = consentService.grantConsent(validRequest);

        // Assert
        assertNotNull(response);
        assertFalse(response.reconsentNeeded());
    }

    @Test
    void grantConsent_ShouldHandleSpecialCharactersInReceiptJson() {
        // Arrange
        ConsentGrantRequest requestWithSpecialChars = new ConsentGrantRequest(
                testUserId,
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                "https://example.com/privacy?param=value&other=test",
                "abc123hash",
                testVerificationId,
                "GB",
                "England & Wales",
                "en-GB",
                ConsentSource.WEB,
                testKids,
                "192.168.1.1",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                LawfulBasis.CONSENT
        );

        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        consentService.grantConsent(requestWithSpecialChars);

        // Assert
        verify(consentLedgerRepository).save(argThat(consent -> {
            String receiptJson = consent.getReceiptJson();
            assertNotNull(receiptJson);
            assertTrue(receiptJson.contains("https://example.com/privacy?param=value&other=test"));
            assertTrue(receiptJson.contains("England & Wales"));
            assertTrue(receiptJson.contains("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));
            return true;
        }));
    }

    @Test
    void grantConsent_ShouldBuildCorrectLatestConsentStatus() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/privacy")
                .consentTimestamp(LocalDateTime.now())
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        ConsentStatusResponse response = consentService.grantConsent(validRequest);

        // Assert
        assertNotNull(response);
        assertNotNull(response.latestByType());
        assertEquals(1, response.latestByType().size());
        
        ConsentStatusResponse.ConsentStatusByType status = response.latestByType().get(0);
        assertEquals(ConsentType.PRIVACY_POLICY, status.type());
        assertEquals("1.0.0", status.version());
        assertEquals(ConsentStatus.GRANTED, status.status());
        assertEquals("https://example.com/privacy", status.policyUrl());
    }

    @Test
    void grantConsent_ShouldCalculateRetentionBasedOnConsentType() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.TERMS_OF_SERVICE) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        ConsentGrantRequest request = new ConsentGrantRequest(
                testUserId,
                ConsentType.TERMS_OF_SERVICE,
                "1.0.0",
                "https://example.com/terms",
                "abc123hash",
                testVerificationId,
                "UK", // UK jurisdiction should result in 6 years for TERMS_OF_SERVICE
                "England",
                "en-GB",
                ConsentSource.WEB,
                testKids,
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );

        // Act
        consentService.grantConsent(request);

        // Assert
        verify(consentLedgerRepository).save(argThat(consent -> {
            LocalDateTime retentionExpiresAt = consent.getRetentionExpiresAt();
            assertNotNull(retentionExpiresAt);
            
            // Should be 6 years for UK TERMS_OF_SERVICE
            LocalDateTime expectedExpiry = LocalDateTime.now().plusYears(6);
            assertTrue(retentionExpiresAt.isAfter(expectedExpiry.minusDays(1)));
            assertTrue(retentionExpiresAt.isBefore(expectedExpiry.plusDays(1)));
            return true;
        }));
    }

    @Test
    void grantConsent_ShouldResolveVerificationMethod() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        uk.gegc.kidsgptbackend.model.consent.ParentVerification verification = uk.gegc.kidsgptbackend.model.consent.ParentVerification.builder()
                .verificationId(testVerificationId)
                .verificationMethod(VerificationMethod.EMAIL)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        when(parentVerificationRepository.findById(testVerificationId)).thenReturn(Optional.of(verification));
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PARENTAL_CONSENT) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        ConsentGrantRequest request = new ConsentGrantRequest(
                testUserId,
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "https://example.com/parental",
                "abc123hash",
                testVerificationId,
                "UK",
                "England",
                "en-GB",
                ConsentSource.WEB,
                testKids,
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );

        // Act
        consentService.grantConsent(request);

        // Assert
        verify(consentLedgerRepository).save(argThat(consent -> {
            String receiptJson = consent.getReceiptJson();
            assertNotNull(receiptJson);
            assertTrue(receiptJson.contains("\"method\":\"EMAIL\""));
            return true;
        }));
    }

    @Test
    void grantConsent_ShouldHandleUnknownVerificationMethod() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.save(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        when(parentVerificationRepository.findById(testVerificationId)).thenReturn(Optional.empty());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PARENTAL_CONSENT) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        ConsentGrantRequest request = new ConsentGrantRequest(
                testUserId,
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "https://example.com/parental",
                "abc123hash",
                testVerificationId,
                "UK",
                "England",
                "en-GB",
                ConsentSource.WEB,
                testKids,
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );

        // Act
        consentService.grantConsent(request);

        // Assert
        verify(consentLedgerRepository).save(argThat(consent -> {
            String receiptJson = consent.getReceiptJson();
            assertNotNull(receiptJson);
            assertTrue(receiptJson.contains("\"method\":\"unknown\""));
            return true;
        }));
    }
} 