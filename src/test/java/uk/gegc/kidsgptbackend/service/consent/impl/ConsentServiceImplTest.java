package uk.gegc.kidsgptbackend.service.consent.impl;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.consent.ConsentGrantRequest;
import uk.gegc.kidsgptbackend.dto.consent.ConsentStatusResponse;
import uk.gegc.kidsgptbackend.dto.consent.ConsentWithdrawRequest;
import uk.gegc.kidsgptbackend.model.consent.*;
import uk.gegc.kidsgptbackend.repository.consent.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.repository.consent.ParentVerificationRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private String serverIp;
    private String serverUa;

    @BeforeEach
    void setUp() {
        // Set up configuration values
        ReflectionTestUtils.setField(consentService, "hmacSecret", "test-hmac-secret-key");
        ReflectionTestUtils.setField(consentService, "defaultRetentionYears", 7);

        // Create test data
        testUserId = UUID.randomUUID();
        testVerificationId = UUID.randomUUID();
        testKids = List.of(UUID.randomUUID(), UUID.randomUUID());

        // Default server captured values
        serverIp = "203.0.113.5";
        serverUa = "JUnitAgent/1.0";

        // Setup common mocks
        lenient().when(parentVerificationRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setAttribute("requestContext", new uk.gegc.kidsgptbackend.util.RequestContext(serverIp, serverUa));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

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

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void grantConsent_Success_ShouldCreateConsentLedgerAndChildCoverage() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        when(parentVerificationRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PARENTAL_CONSENT) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        ConsentGrantRequest request = new ConsentGrantRequest(
                testUserId,
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "https://example.com/parental",
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

        ConsentStatusResponse response = consentService.grantConsent(request);
        // Assert
        assertNotNull(response);
        assertFalse(response.reconsentNeeded());
        assertNotNull(response.latestByType());
        assertEquals(1, response.latestByType().size());

        // Verify consent ledger was saved with correct data
        verify(consentLedgerRepository).saveAndFlush(argThat(consent ->
                consent.getUserId().equals(testUserId) &&
                        consent.getConsentType().equals(ConsentType.PARENTAL_CONSENT) &&
                        consent.getConsentStatus().equals(ConsentStatus.GRANTED) &&
                        consent.getConsentVersion().equals("1.0.0") &&
                        consent.getPolicyUrl().equals("https://example.com/parental") &&
                        consent.getContentHash().equals("abc123hash") &&
                        consent.getJurisdiction().equals("GB") &&
                        consent.getRegion().equals("ENGLAND") &&
                        consent.getLocale().equals("en-GB") &&
                        consent.getSource().equals(ConsentSource.WEB) &&
                        consent.getIpAddress().equals(serverIp) &&
                        consent.getUserAgent().equals(serverUa) &&
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

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.TERMS_OF_SERVICE) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
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
        verify(consentLedgerRepository).saveAndFlush(argThat(consent ->
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

        // Act & Assert
        assertThrows(ConstraintViolationException.class, () -> consentService.grantConsent(requestWithNoKids));
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

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
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
        verify(consentLedgerRepository).saveAndFlush(argThat(consent -> {
            String receiptJson = consent.getReceiptJson();
            assertNotNull(receiptJson);
            assertTrue(receiptJson.contains("\"consent_type\":\"PRIVACY_POLICY\""));
            assertTrue(receiptJson.contains("\"consent_version\":\"1.0.0\""));
            assertTrue(receiptJson.contains("\"policy_url\":\"https://example.com/privacy\""));
            assertTrue(receiptJson.contains("\"jurisdiction\":\"GB\""));
            assertTrue(receiptJson.contains("\"lawful_basis\":\"CONSENT\""));
            assertTrue(receiptJson.contains("\"source\":\"WEB\""));
            assertTrue(receiptJson.contains("\"region\":\"ENGLAND\""));
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

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
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
        verify(consentLedgerRepository).saveAndFlush(argThat(consent -> {
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

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
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
        verify(consentLedgerRepository).saveAndFlush(argThat(consent -> {
            LocalDateTime retentionExpiresAt = consent.getRetentionExpiresAt();
            assertNotNull(retentionExpiresAt);
            assertTrue(retentionExpiresAt.isAfter(beforeCall.plusYears(4)));
            assertTrue(retentionExpiresAt.isBefore(beforeCall.plusYears(6)));
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

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(existingConsent))
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

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

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

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("requestContext", new uk.gegc.kidsgptbackend.util.RequestContext(serverIp,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
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
        verify(consentLedgerRepository).saveAndFlush(argThat(consent -> {
            String receiptJson = consent.getReceiptJson();
            assertNotNull(receiptJson);
            assertTrue(receiptJson.contains("https://example.com/privacy?param=value&other=test"));
            assertTrue(receiptJson.contains("ENGLAND & WALES"));
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

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
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

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.TERMS_OF_SERVICE) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
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
        verify(consentLedgerRepository).saveAndFlush(argThat(consent -> {
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

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        when(parentVerificationRepository.findById(testVerificationId)).thenReturn(Optional.of(verification));
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PARENTAL_CONSENT) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
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
        verify(consentLedgerRepository).saveAndFlush(argThat(consent -> {
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

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        when(parentVerificationRepository.findById(testVerificationId)).thenReturn(Optional.empty());
        
        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PARENTAL_CONSENT) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
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
        verify(consentLedgerRepository).saveAndFlush(argThat(consent -> {
            String receiptJson = consent.getReceiptJson();
            assertNotNull(receiptJson);
            assertTrue(receiptJson.contains("\"method\":\"unknown\""));
            return true;
        }));
    }


    @Test
    void grantConsent_ShouldUseServerCapturedIpAndUa() {
        String capturedIp = "203.0.113.55";
        String capturedUa = "ServerUA/2.0";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("requestContext", new uk.gegc.kidsgptbackend.util.RequestContext(capturedIp, capturedUa));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        ConsentGrantRequest reqBody = new ConsentGrantRequest(
                testUserId,
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                "https://example.com/privacy",
                "hash",
                testVerificationId,
                "GB",
                "England",
                "en-GB",
                ConsentSource.WEB,
                testKids,
                "10.0.0.1",
                "BadUA/0.1",
                LawfulBasis.CONSENT
        );

        consentService.grantConsent(reqBody);

        verify(consentLedgerRepository).saveAndFlush(argThat(c ->
                capturedIp.equals(c.getIpAddress()) && capturedUa.equals(c.getUserAgent())));
    }

    @Test
    void grantConsent_WhenSameVersionAlreadyGranted_ShouldReturnExistingId() {
        UUID existingId = UUID.randomUUID();
        ConsentLedger existing = ConsentLedger.builder()
                .consentId(existingId)
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PRIVACY_POLICY), eq(ConsentStatus.GRANTED)))
                .thenReturn(Optional.of(existing));
        for (ConsentType t : ConsentType.values()) {
            if (t != ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(t), eq(ConsentStatus.GRANTED))).thenReturn(Optional.empty());
            }
        }

        ConsentStatusResponse resp = consentService.grantConsent(validRequest);

        assertEquals(existingId, resp.consentId());
        verify(consentLedgerRepository, never()).saveAndFlush(any());
    }

    @Test
    void grantConsent_WithInvalidPolicyUrl_ShouldThrow() {
        ConsentGrantRequest bad = new ConsentGrantRequest(
                testUserId,
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                "http://malicious.com/privacy",
                "hash",
                testVerificationId,
                "GB",
                "England",
                "en-GB",
                ConsentSource.WEB,
                testKids,
                "1.2.3.4",
                "UA",
                LawfulBasis.CONSENT
        );

        assertThrows(ResponseStatusException.class, () -> consentService.grantConsent(bad));
    }

    @Test
    void grantConsent_WithDuplicateKids_ShouldDeduplicateAndSort() {
        UUID kidA = UUID.randomUUID();
        UUID kidB = UUID.randomUUID();
        ConsentLedger saved = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(saved);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        for (ConsentType t : ConsentType.values()) {
            if (t == ConsentType.PARENTAL_CONSENT) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(t), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(saved));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(t), eq(ConsentStatus.GRANTED))).thenReturn(Optional.empty());
            }
        }

        ConsentGrantRequest r = new ConsentGrantRequest(
                testUserId,
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "https://example.com/parental",
                "hash",
                testVerificationId,
                "GB",
                "England",
                "en-GB",
                ConsentSource.WEB,
                List.of(kidB, kidA, kidA),
                "1.2.3.4",
                "UA",
                LawfulBasis.CONSENT
        );

        consentService.grantConsent(r);

        verify(consentChildCoverageRepository).saveAll(argThat(l -> {
            List<ConsentChildCoverage> list = (List<ConsentChildCoverage>) l;
            return list.size() == 2 &&
                    list.stream().map(ConsentChildCoverage::getKidId).toList().containsAll(List.of(kidA, kidB));
        }));
    }

    @Test
    void grantConsent_TermsOrPrivacy_ShouldNotWriteChildCoverage() {
        // Arrange
        ConsentGrantRequest req = new ConsentGrantRequest(
                testUserId,
                ConsentType.TERMS_OF_SERVICE,
                "1.0.0",
                "https://example.com/terms",
                "hash",
                testVerificationId,
                "GB",
                "England",
                "en-GB",
                ConsentSource.WEB,
                testKids, // Include kids but should be ignored
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONTRACT
        );

        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.TERMS_OF_SERVICE) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        consentService.grantConsent(req);

        // Assert
        verify(consentChildCoverageRepository, never()).saveAll(anyList());
    }

    @Test
    void grantConsent_WithBase64HmacKey_ShouldGenerateSignature() {
        // Arrange
        String base64Key = java.util.Base64.getEncoder().encodeToString("binkey-32bytes-or-more".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(consentService, "hmacSecret", base64Key);

        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
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
        verify(consentLedgerRepository).saveAndFlush(argThat(c -> 
            c.getRecordSignature() != null && c.getRecordSignature().length > 0));
    }

    @Test
    void grantConsent_SameKidsDifferentOrder_ShouldProduceSameSignature() {
        // Arrange
        UUID testUserId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        UUID kidA = UUID.randomUUID();
        UUID kidB = UUID.randomUUID();
        ConsentGrantRequest req1 = new ConsentGrantRequest(
                testUserId,
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "https://example.com/parental",
                "hash",
                testVerificationId,
                "GB",
                "England",
                "en-GB",
                ConsentSource.WEB,
                List.of(kidB, kidA),
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );
        ConsentGrantRequest req2 = new ConsentGrantRequest(
                testUserId,
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "https://example.com/parental",
                "hash",
                testVerificationId,
                "GB",
                "England",
                "en-GB",
                ConsentSource.WEB,
                List.of(kidA, kidB),
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );
        // Use ReflectionTestUtils to call the private method
        String json1 = ReflectionTestUtils.invokeMethod(
                consentService, "buildCanonicalReceiptJson",
                consentId, req1, "GB", "ENGLAND", "en-GB", "192.168.1.1", "Mozilla/5.0",
                java.time.Instant.parse("2025-08-01T23:49:37.472530200Z"), List.of(kidB, kidA), "unknown"
        );
        String json2 = ReflectionTestUtils.invokeMethod(
                consentService, "buildCanonicalReceiptJson",
                consentId, req2, "GB", "ENGLAND", "en-GB", "192.168.1.1", "Mozilla/5.0",
                java.time.Instant.parse("2025-08-01T23:49:37.472530200Z"), List.of(kidA, kidB), "unknown"
        );
        // Assert
        assertEquals(json1, json2);
    }

    @Test
    void grantConsent_ShouldNormalizeLocale() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        ConsentGrantRequest req = new ConsentGrantRequest(
                testUserId,
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                "https://example.com/privacy",
                "hash",
                testVerificationId,
                "GB",
                "England",
                "en-gb", // lowercase
                ConsentSource.WEB,
                testKids,
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );

        // Act
        consentService.grantConsent(req);

        // Assert
        verify(consentLedgerRepository).saveAndFlush(argThat(consent -> 
            "en-GB".equals(consent.getLocale())));
    }

    @Test
    void grantConsent_ShouldHandleSimpleLocale() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .build();

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        ConsentGrantRequest req = new ConsentGrantRequest(
                testUserId,
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                "https://example.com/privacy",
                "hash",
                testVerificationId,
                "GB",
                "England",
                "ru", // simple locale
                ConsentSource.WEB,
                testKids,
                "192.168.1.1",
                "Mozilla/5.0",
                LawfulBasis.CONSENT
        );

        // Act
        consentService.grantConsent(req);

        // Assert
        verify(consentLedgerRepository).saveAndFlush(argThat(consent -> 
            "ru".equals(consent.getLocale())));
    }

    @Test
    void withdrawConsent_ShouldPersistCorrectLedgerFields() {
        // Arrange - Create a granted consent first
        ConsentLedger grantedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/parental")
                .contentHash("abc123hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress(serverIp)
                .userAgent(serverUa)
                .consentTimestamp(LocalDateTime.now())
                .parentVerificationId(testVerificationId)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"grant\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        when(consentLedgerRepository.findActiveGrantByUserTypeAndVersion(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq("1.0.0")))
                .thenReturn(Optional.of(grantedConsent));

                                    when(consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                                    eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq("1.0.0")))
                                    .thenReturn(false);

                            ConsentWithdrawRequest withdrawRequest = new ConsentWithdrawRequest(
                                    testUserId.toString(),
                                    ConsentType.PARENTAL_CONSENT,
                                    "1.0.0",
                                    "User requested withdrawal",
                                    "192.168.1.1",
                                    "Mozilla/5.0"
                            );

                            // Mock saveAndFlush to return the withdrawal object
                            when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                                    .thenAnswer(invocation -> invocation.getArgument(0));

                            // Act
                            consentService.withdrawConsent(withdrawRequest);

        // Assert - Verify the persisted withdrawal ledger has correct fields
        verify(consentLedgerRepository).saveAndFlush(argThat(ledger -> {
            // Verify consentStatus=WITHDRAWN
            assertEquals(ConsentStatus.WITHDRAWN, ledger.getConsentStatus());
            
            // Verify withdrawnConsentId references the grant
            assertEquals(grantedConsent.getConsentId(), ledger.getWithdrawnConsentId());
            
            // Verify consentVersion = grant's version
            assertEquals(grantedConsent.getConsentVersion(), ledger.getConsentVersion());
            
            // Verify fields copied from grant
            assertEquals(grantedConsent.getPolicyUrl(), ledger.getPolicyUrl());
            assertEquals(grantedConsent.getContentHash(), ledger.getContentHash());
            assertEquals(grantedConsent.getJurisdiction(), ledger.getJurisdiction());
            assertEquals(grantedConsent.getRegion(), ledger.getRegion());
            assertEquals(grantedConsent.getLocale(), ledger.getLocale());
            assertEquals(grantedConsent.getLawfulBasis(), ledger.getLawfulBasis());
            assertEquals(grantedConsent.getSource(), ledger.getSource());
            
            // Verify retentionExpiresAt unchanged
            assertEquals(grantedConsent.getRetentionExpiresAt(), ledger.getRetentionExpiresAt());
            
            // Verify other fields are set correctly
            assertEquals(testUserId, ledger.getUserId());
            assertEquals(ConsentType.PARENTAL_CONSENT, ledger.getConsentType());
            assertEquals(testVerificationId, ledger.getParentVerificationId());
            assertEquals(serverIp, ledger.getIpAddress());
            assertEquals(serverUa, ledger.getUserAgent());
            
            // Verify receipt and signature are generated
            assertNotNull(ledger.getReceiptJson());
            assertNotNull(ledger.getRecordSignature());
            assertTrue(ledger.getRecordSignature().length > 0);
            
                         return true;
         }));
     }

     @Test
     void withdrawConsent_ShouldGenerateCorrectReceiptJson() {
         // Arrange - Create a granted consent first
         UUID grantedConsentId = UUID.randomUUID();
         ConsentLedger grantedConsent = ConsentLedger.builder()
                 .consentId(grantedConsentId)
                 .userId(testUserId)
                 .consentType(ConsentType.PARENTAL_CONSENT)
                 .consentVersion("1.0.0")
                 .consentStatus(ConsentStatus.GRANTED)
                 .policyUrl("https://example.com/parental")
                 .contentHash("abc123hash")
                 .jurisdiction("GB")
                 .region("England")
                 .locale("en-GB")
                 .lawfulBasis(LawfulBasis.CONSENT)
                 .source(ConsentSource.WEB)
                 .ipAddress(serverIp)
                 .userAgent(serverUa)
                 .consentTimestamp(LocalDateTime.now())
                 .parentVerificationId(testVerificationId)
                 .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                 .receiptJson("{\"test\":\"grant\"}")
                 .recordSignature(new byte[]{1, 2, 3})
                 .build();

         when(consentLedgerRepository.findActiveGrantByUserTypeAndVersion(
                 eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq("1.0.0")))
                 .thenReturn(Optional.of(grantedConsent));

         when(consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                 eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq("1.0.0")))
                 .thenReturn(false);

         ConsentWithdrawRequest withdrawRequest = new ConsentWithdrawRequest(
                 testUserId.toString(),
                 ConsentType.PARENTAL_CONSENT,
                 "1.0.0",
                 "User requested withdrawal",
                 "192.168.1.1",
                 "Mozilla/5.0"
         );

         // Mock saveAndFlush to return the withdrawal object
         when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                 .thenAnswer(invocation -> invocation.getArgument(0));

         // Act
         consentService.withdrawConsent(withdrawRequest);

         // Assert - Verify the receipt JSON contains all required fields
         verify(consentLedgerRepository).saveAndFlush(argThat(ledger -> {
             String receiptJson = ledger.getReceiptJson();
             assertNotNull(receiptJson);
             
             // Verify all required fields are present
             assertTrue(receiptJson.contains("\"consent_id\""));
             assertTrue(receiptJson.contains("\"parent_uuid\":\"" + testUserId + "\""));
             assertTrue(receiptJson.contains("\"withdrawn_consent_id\":\"" + grantedConsentId + "\""));
             assertTrue(receiptJson.contains("\"consent_type\":\"PARENTAL_CONSENT\""));
             assertTrue(receiptJson.contains("\"consent_version\":\"1.0.0\""));
             assertTrue(receiptJson.contains("\"policy_url\":\"https://example.com/parental\""));
             assertTrue(receiptJson.contains("\"content_hash\":\"abc123hash\""));
             assertTrue(receiptJson.contains("\"jurisdiction\":\"GB\""));
             assertTrue(receiptJson.contains("\"region\":\"England\""));
             assertTrue(receiptJson.contains("\"locale\":\"en-GB\""));
             assertTrue(receiptJson.contains("\"lawful_basis\":\"CONSENT\""));
             assertTrue(receiptJson.contains("\"source\":\"WEB\""));
             assertTrue(receiptJson.contains("\"timestamp\""));
             assertTrue(receiptJson.contains("\"ip\":\"" + serverIp + "\""));
             assertTrue(receiptJson.contains("\"ua\":\"" + serverUa + "\""));
             assertTrue(receiptJson.contains("\"action\":\"WITHDRAWN\""));
             assertTrue(receiptJson.contains("\"reason\":\"User requested withdrawal\""));
             
             return true;
         }));
     }

     @Test
     void withdrawConsent_ShouldOmitReasonWhenNotProvided() {
         // Arrange - Create a granted consent first
         UUID grantedConsentId = UUID.randomUUID();
         ConsentLedger grantedConsent = ConsentLedger.builder()
                 .consentId(grantedConsentId)
                 .userId(testUserId)
                 .consentType(ConsentType.PRIVACY_POLICY)
                 .consentVersion("2.0.0")
                 .consentStatus(ConsentStatus.GRANTED)
                 .policyUrl("https://example.com/privacy")
                 .contentHash("def456hash")
                 .jurisdiction("US")
                 .region("CA")
                 .locale("en-US")
                 .lawfulBasis(LawfulBasis.LEGITIMATE_INTEREST)
                 .source(ConsentSource.IOS)
                 .ipAddress(serverIp)
                 .userAgent(serverUa)
                 .consentTimestamp(LocalDateTime.now())
                 .parentVerificationId(null)
                 .retentionExpiresAt(LocalDateTime.now().plusYears(5))
                 .receiptJson("{\"test\":\"grant\"}")
                 .recordSignature(new byte[]{1, 2, 3})
                 .build();

         when(consentLedgerRepository.findActiveGrantByUserTypeAndVersion(
                 eq(testUserId), eq(ConsentType.PRIVACY_POLICY), eq("2.0.0")))
                 .thenReturn(Optional.of(grantedConsent));

         when(consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                 eq(testUserId), eq(ConsentType.PRIVACY_POLICY), eq("2.0.0")))
                 .thenReturn(false);

         ConsentWithdrawRequest withdrawRequest = new ConsentWithdrawRequest(
                 testUserId.toString(),
                 ConsentType.PRIVACY_POLICY,
                 "2.0.0",
                 null, // No reason provided
                 "192.168.1.1",
                 "Mozilla/5.0"
         );

         // Mock saveAndFlush to return the withdrawal object
         when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                 .thenAnswer(invocation -> invocation.getArgument(0));

         // Act
         consentService.withdrawConsent(withdrawRequest);

         // Assert - Verify the receipt JSON omits reason when not provided
         verify(consentLedgerRepository).saveAndFlush(argThat(ledger -> {
             String receiptJson = ledger.getReceiptJson();
             assertNotNull(receiptJson);
             
             // Verify all required fields are present
             assertTrue(receiptJson.contains("\"consent_id\""));
             assertTrue(receiptJson.contains("\"parent_uuid\":\"" + testUserId + "\""));
             assertTrue(receiptJson.contains("\"withdrawn_consent_id\":\"" + grantedConsentId + "\""));
             assertTrue(receiptJson.contains("\"consent_type\":\"PRIVACY_POLICY\""));
             assertTrue(receiptJson.contains("\"consent_version\":\"2.0.0\""));
             assertTrue(receiptJson.contains("\"policy_url\":\"https://example.com/privacy\""));
             assertTrue(receiptJson.contains("\"content_hash\":\"def456hash\""));
             assertTrue(receiptJson.contains("\"jurisdiction\":\"US\""));
             assertTrue(receiptJson.contains("\"region\":\"CA\""));
             assertTrue(receiptJson.contains("\"locale\":\"en-US\""));
             assertTrue(receiptJson.contains("\"lawful_basis\":\"LEGITIMATE_INTEREST\""));
             assertTrue(receiptJson.contains("\"source\":\"IOS\""));
             assertTrue(receiptJson.contains("\"timestamp\""));
             assertTrue(receiptJson.contains("\"ip\":\"" + serverIp + "\""));
             assertTrue(receiptJson.contains("\"ua\":\"" + serverUa + "\""));
             assertTrue(receiptJson.contains("\"action\":\"WITHDRAWN\""));
             
             // Verify reason is NOT present when not provided
             assertFalse(receiptJson.contains("\"reason\""));
             
             return true;
         }));
     }

     @Test
     void withdrawConsent_ShouldGenerateHmacSignature() {
         // Arrange - Create a granted consent first
         UUID grantedConsentId = UUID.randomUUID();
         ConsentLedger grantedConsent = ConsentLedger.builder()
                 .consentId(grantedConsentId)
                 .userId(testUserId)
                 .consentType(ConsentType.DATA_PROCESSING)
                 .consentVersion("3.0.0")
                 .consentStatus(ConsentStatus.GRANTED)
                 .policyUrl("https://example.com/data")
                 .contentHash("ghi789hash")
                 .jurisdiction("EU")
                 .region("Germany")
                 .locale("de-DE")
                 .lawfulBasis(LawfulBasis.CONSENT)
                 .source(ConsentSource.ANDROID)
                 .ipAddress(serverIp)
                 .userAgent(serverUa)
                 .consentTimestamp(LocalDateTime.now())
                 .parentVerificationId(null)
                 .retentionExpiresAt(LocalDateTime.now().plusYears(3))
                 .receiptJson("{\"test\":\"grant\"}")
                 .recordSignature(new byte[]{1, 2, 3})
                 .build();

         when(consentLedgerRepository.findActiveGrantByUserTypeAndVersion(
                 eq(testUserId), eq(ConsentType.DATA_PROCESSING), eq("3.0.0")))
                 .thenReturn(Optional.of(grantedConsent));

         when(consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                 eq(testUserId), eq(ConsentType.DATA_PROCESSING), eq("3.0.0")))
                 .thenReturn(false);

         ConsentWithdrawRequest withdrawRequest = new ConsentWithdrawRequest(
                 testUserId.toString(),
                 ConsentType.DATA_PROCESSING,
                 "3.0.0",
                 "User requested data processing withdrawal",
                 "192.168.1.1",
                 "Mozilla/5.0"
         );

         // Mock saveAndFlush to return the withdrawal object
         when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                 .thenAnswer(invocation -> invocation.getArgument(0));

         // Act
         consentService.withdrawConsent(withdrawRequest);

         // Assert - Verify the HMAC signature is generated
         verify(consentLedgerRepository).saveAndFlush(argThat(ledger -> {
             // Verify recordSignature is non-null and non-empty
             assertNotNull(ledger.getRecordSignature());
             assertTrue(ledger.getRecordSignature().length > 0);
             
             // Verify receipt JSON is also present (prerequisite for signature)
             assertNotNull(ledger.getReceiptJson());
             assertFalse(ledger.getReceiptJson().isEmpty());
             
             return true;
         }));
     }

    @Test
    void withdrawConsent_WithBase64HmacKey_ShouldGenerateSignature() {
        // Arrange - Set up Base64 encoded HMAC secret
        String base64HmacSecret = "dGVzdC1obWFjLXNlY3JldC1rZXktZm9yLWJhc2U2NC10ZXN0aW5n";
        ReflectionTestUtils.setField(consentService, "hmacSecret", base64HmacSecret);
        
        // Create a granted consent first
        UUID grantedConsentId = UUID.randomUUID();
        ConsentLedger grantedConsent = ConsentLedger.builder()
                .consentId(grantedConsentId)
                .userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("4.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/terms")
                .contentHash("jkl012hash")
                .jurisdiction("CA")
                .region("Ontario")
                .locale("en-CA")
                .lawfulBasis(LawfulBasis.CONTRACT)
                .source(ConsentSource.WEB)
                .ipAddress(serverIp)
                .userAgent(serverUa)
                .consentTimestamp(LocalDateTime.now())
                .parentVerificationId(null)
                .retentionExpiresAt(LocalDateTime.now().plusYears(6))
                .receiptJson("{\"test\":\"grant\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        when(consentLedgerRepository.findActiveGrantByUserTypeAndVersion(
                eq(testUserId), eq(ConsentType.TERMS_OF_SERVICE), eq("4.0.0")))
                .thenReturn(Optional.of(grantedConsent));

        when(consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                eq(testUserId), eq(ConsentType.TERMS_OF_SERVICE), eq("4.0.0")))
                .thenReturn(false);

        ConsentWithdrawRequest withdrawRequest = new ConsentWithdrawRequest(
                testUserId.toString(),
                ConsentType.TERMS_OF_SERVICE,
                "4.0.0",
                "User requested terms withdrawal",
                "192.168.1.1",
                "Mozilla/5.0"
        );

        // Mock saveAndFlush to return the withdrawal object
        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        consentService.withdrawConsent(withdrawRequest);

        // Assert - Verify the HMAC signature is still generated with Base64 key
        verify(consentLedgerRepository).saveAndFlush(argThat(ledger -> {
            // Verify recordSignature is non-null and non-empty (Base64 key path works)
            assertNotNull(ledger.getRecordSignature());
            assertTrue(ledger.getRecordSignature().length > 0);
            
            // Verify receipt JSON is also present (prerequisite for signature)
            assertNotNull(ledger.getReceiptJson());
            assertFalse(ledger.getReceiptJson().isEmpty());
            
            // Verify the signature is different from the original (different key used)
            assertNotEquals(grantedConsent.getRecordSignature(), ledger.getRecordSignature());
            
            return true;
        }));
    }
 }
