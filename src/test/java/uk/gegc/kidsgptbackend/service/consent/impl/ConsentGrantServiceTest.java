package uk.gegc.kidsgptbackend.service.consent.impl;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.consent.ConsentGrantRequest;
import uk.gegc.kidsgptbackend.dto.consent.ConsentStatusResponse;
import uk.gegc.kidsgptbackend.model.consent.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ConsentGrantServiceTest extends ConsentServiceBaseTest {

    @Test
    void grantConsent_Success_ShouldCreateConsentLedgerAndChildCoverage() {
        // Arrange
        ConsentLedger savedConsent = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/parental")
                .contentHash("abc123hash")
                .jurisdiction("GB")
                .region("ENGLAND")
                .locale("en-GB")
                .source(ConsentSource.WEB)
                .ipAddress(serverIp)
                .userAgent(serverUa)
                .lawfulBasis(LawfulBasis.CONSENT)
                .parentVerificationId(testVerificationId)
                .receiptJson("{}")
                .recordSignature(new byte[64])
                .retentionExpiresAt(LocalDateTime.now().plusYears(1))
                .build();

        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class))).thenReturn(savedConsent);
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        when(parentVerificationRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PARENTAL_CONSENT) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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

        // Verify consent ledger was saved with correct data (single save with complete data)
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
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
        ArgumentCaptor<ConsentLedger> consentCaptor = ArgumentCaptor.forClass(ConsentLedger.class);
        when(consentLedgerRepository.saveAndFlush(consentCaptor.capture())).thenAnswer(invocation -> {
            ConsentLedger captured = invocation.getArgument(0);
            return ConsentLedger.builder()
                    .consentId(captured.getConsentId() != null ? captured.getConsentId() : UUID.randomUUID())
                    .userId(captured.getUserId())
                    .consentType(captured.getConsentType())
                    .consentVersion(captured.getConsentVersion())
                    .consentStatus(captured.getConsentStatus())
                    .receiptJson(captured.getReceiptJson())
                    .recordSignature(captured.getRecordSignature())
                    .retentionExpiresAt(captured.getRetentionExpiresAt())
                    .build();
        });

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.empty());
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        consentService.grantConsent(validRequest);

        // Assert
        verify(consentLedgerRepository).saveAndFlush(any(ConsentLedger.class));
        
        List<ConsentLedger> capturedConsents = consentCaptor.getAllValues();
        assertTrue(capturedConsents.stream().anyMatch(consent -> {
            String receiptJson = consent.getReceiptJson();
            return receiptJson != null &&
                   receiptJson.contains("\"consent_type\":\"PRIVACY_POLICY\"") &&
                   receiptJson.contains("\"consent_version\":\"1.0.0\"") &&
                   receiptJson.contains("\"policy_url\":\"https://example.com/privacy\"") &&
                   receiptJson.contains("\"jurisdiction\":\"GB\"") &&
                   receiptJson.contains("\"lawful_basis\":\"CONSENT\"") &&
                   receiptJson.contains("\"source\":\"WEB\"") &&
                   receiptJson.contains("\"region\":\"ENGLAND\"") &&
                   receiptJson.contains("\"kids\":[");
        }));
    }

    @Test
    void grantConsent_ShouldGenerateHmacSignature() {
        // Arrange
        ArgumentCaptor<ConsentLedger> consentCaptor = ArgumentCaptor.forClass(ConsentLedger.class);
        when(consentLedgerRepository.saveAndFlush(consentCaptor.capture())).thenAnswer(invocation -> {
            ConsentLedger captured = invocation.getArgument(0);
            return ConsentLedger.builder()
                    .consentId(captured.getConsentId() != null ? captured.getConsentId() : UUID.randomUUID())
                    .userId(captured.getUserId())
                    .consentType(captured.getConsentType())
                    .consentVersion(captured.getConsentVersion())
                    .consentStatus(captured.getConsentStatus())
                    .receiptJson(captured.getReceiptJson())
                    .recordSignature(captured.getRecordSignature())
                    .retentionExpiresAt(captured.getRetentionExpiresAt())
                    .build();
        });

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.empty());
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        consentService.grantConsent(validRequest);

        // Assert
        verify(consentLedgerRepository).saveAndFlush(any(ConsentLedger.class));
        
        List<ConsentLedger> capturedConsents = consentCaptor.getAllValues();
        assertTrue(capturedConsents.stream().anyMatch(consent -> {
            byte[] signature = consent.getRecordSignature();
            return signature != null && signature.length > 0;
        }));
    }

    @Test
    void grantConsent_ShouldSetRetentionExpiryDate() {
        // Arrange
        ArgumentCaptor<ConsentLedger> consentCaptor = ArgumentCaptor.forClass(ConsentLedger.class);
        when(consentLedgerRepository.saveAndFlush(consentCaptor.capture())).thenAnswer(invocation -> {
            ConsentLedger captured = invocation.getArgument(0);
            return ConsentLedger.builder()
                    .consentId(captured.getConsentId() != null ? captured.getConsentId() : UUID.randomUUID())
                    .userId(captured.getUserId())
                    .consentType(captured.getConsentType())
                    .consentVersion(captured.getConsentVersion())
                    .consentStatus(captured.getConsentStatus())
                    .receiptJson(captured.getReceiptJson())
                    .recordSignature(captured.getRecordSignature())
                    .retentionExpiresAt(captured.getRetentionExpiresAt())
                    .build();
        });

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.empty());
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        LocalDateTime beforeCall = LocalDateTime.now();

        // Act
        consentService.grantConsent(validRequest);

        // Assert
        verify(consentLedgerRepository).saveAndFlush(any(ConsentLedger.class));
        
        List<ConsentLedger> capturedConsents = consentCaptor.getAllValues();
        assertTrue(capturedConsents.stream().anyMatch(consent -> {
            LocalDateTime retentionExpiresAt = consent.getRetentionExpiresAt();
            return retentionExpiresAt != null && 
                   retentionExpiresAt.isAfter(beforeCall.plusYears(4)) &&
                   retentionExpiresAt.isBefore(beforeCall.plusYears(6));
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
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.of(existingConsent))
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
            when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
        req.setAttribute("requestContext", new uk.gegc.kidsgptbackend.shared.util.RequestContext(serverIp,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ArgumentCaptor<ConsentLedger> consentCaptor = ArgumentCaptor.forClass(ConsentLedger.class);
        when(consentLedgerRepository.saveAndFlush(consentCaptor.capture())).thenAnswer(invocation -> {
            ConsentLedger captured = invocation.getArgument(0);
            return ConsentLedger.builder()
                    .consentId(captured.getConsentId() != null ? captured.getConsentId() : UUID.randomUUID())
                    .userId(captured.getUserId())
                    .consentType(captured.getConsentType())
                    .consentVersion(captured.getConsentVersion())
                    .consentStatus(captured.getConsentStatus())
                    .receiptJson(captured.getReceiptJson())
                    .recordSignature(captured.getRecordSignature())
                    .retentionExpiresAt(captured.getRetentionExpiresAt())
                    .build();
        });

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.empty());
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty());
            }
        }

        // Act
        consentService.grantConsent(requestWithSpecialChars);

        // Assert
        verify(consentLedgerRepository).saveAndFlush(any(ConsentLedger.class));
        
        List<ConsentLedger> capturedConsents = consentCaptor.getAllValues();
        assertTrue(capturedConsents.stream().anyMatch(consent -> {
            String receiptJson = consent.getReceiptJson();
            return receiptJson != null &&
                   receiptJson.contains("https://example.com/privacy?param=value&other=test") &&
                   receiptJson.contains("ENGLAND & WALES") &&
                   receiptJson.contains("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
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
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
        ArgumentCaptor<ConsentLedger> consentCaptor = ArgumentCaptor.forClass(ConsentLedger.class);
        when(consentLedgerRepository.saveAndFlush(consentCaptor.capture())).thenAnswer(invocation -> {
            ConsentLedger captured = invocation.getArgument(0);
            return ConsentLedger.builder()
                    .consentId(captured.getConsentId() != null ? captured.getConsentId() : UUID.randomUUID())
                    .userId(captured.getUserId())
                    .consentType(captured.getConsentType())
                    .consentVersion(captured.getConsentVersion())
                    .consentStatus(captured.getConsentStatus())
                    .receiptJson(captured.getReceiptJson())
                    .recordSignature(captured.getRecordSignature())
                    .retentionExpiresAt(captured.getRetentionExpiresAt())
                    .build();
        });

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.TERMS_OF_SERVICE) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.empty());
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
        verify(consentLedgerRepository).saveAndFlush(any(ConsentLedger.class));
        
        List<ConsentLedger> capturedConsents = consentCaptor.getAllValues();
        assertTrue(capturedConsents.stream().anyMatch(consent -> {
            LocalDateTime retentionExpiresAt = consent.getRetentionExpiresAt();
            if (retentionExpiresAt == null) return false;

            // Should be 6 years for UK TERMS_OF_SERVICE
            LocalDateTime expectedExpiry = LocalDateTime.now().plusYears(6);
            return retentionExpiresAt.isAfter(expectedExpiry.minusDays(1)) &&
                   retentionExpiresAt.isBefore(expectedExpiry.plusDays(1));
        }));
    }

    @Test
    void grantConsent_ShouldResolveVerificationMethod() {
        // Arrange
        uk.gegc.kidsgptbackend.model.consent.ParentVerification verification = uk.gegc.kidsgptbackend.model.consent.ParentVerification.builder()
                .verificationId(testVerificationId)
                .verificationMethod(VerificationMethod.EMAIL)
                .build();

        ArgumentCaptor<ConsentLedger> consentCaptor = ArgumentCaptor.forClass(ConsentLedger.class);
        when(consentLedgerRepository.saveAndFlush(consentCaptor.capture())).thenAnswer(invocation -> {
            ConsentLedger captured = invocation.getArgument(0);
            return ConsentLedger.builder()
                    .consentId(captured.getConsentId() != null ? captured.getConsentId() : UUID.randomUUID())
                    .userId(captured.getUserId())
                    .consentType(captured.getConsentType())
                    .consentVersion(captured.getConsentVersion())
                    .consentStatus(captured.getConsentStatus())
                    .receiptJson(captured.getReceiptJson())
                    .recordSignature(captured.getRecordSignature())
                    .retentionExpiresAt(captured.getRetentionExpiresAt())
                    .build();
        });
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        when(parentVerificationRepository.findById(testVerificationId)).thenReturn(Optional.of(verification));

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PARENTAL_CONSENT) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.empty());
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
        verify(consentLedgerRepository).saveAndFlush(any(ConsentLedger.class));
        
        List<ConsentLedger> capturedConsents = consentCaptor.getAllValues();
        assertTrue(capturedConsents.stream().anyMatch(consent -> {
            String receiptJson = consent.getReceiptJson();
            return receiptJson != null && receiptJson.contains("\"method\":\"EMAIL\"");
        }));
    }

    @Test
    void grantConsent_ShouldHandleUnknownVerificationMethod() {
        // Arrange
        ArgumentCaptor<ConsentLedger> consentCaptor = ArgumentCaptor.forClass(ConsentLedger.class);
        when(consentLedgerRepository.saveAndFlush(consentCaptor.capture())).thenAnswer(invocation -> {
            ConsentLedger captured = invocation.getArgument(0);
            return ConsentLedger.builder()
                    .consentId(captured.getConsentId() != null ? captured.getConsentId() : UUID.randomUUID())
                    .userId(captured.getUserId())
                    .consentType(captured.getConsentType())
                    .consentVersion(captured.getConsentVersion())
                    .consentStatus(captured.getConsentStatus())
                    .receiptJson(captured.getReceiptJson())
                    .recordSignature(captured.getRecordSignature())
                    .retentionExpiresAt(captured.getRetentionExpiresAt())
                    .build();
        });
        when(consentChildCoverageRepository.saveAll(anyList())).thenReturn(List.of());
        when(parentVerificationRepository.findById(testVerificationId)).thenReturn(Optional.empty());

        // Stub for all consent types that buildLatestConsentStatus might call
        for (ConsentType type : ConsentType.values()) {
            if (type == ConsentType.PARENTAL_CONSENT) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.empty());
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
        verify(consentLedgerRepository).saveAndFlush(any(ConsentLedger.class));
        
        List<ConsentLedger> capturedConsents = consentCaptor.getAllValues();
        assertTrue(capturedConsents.stream().anyMatch(consent -> {
            String receiptJson = consent.getReceiptJson();
            return receiptJson != null && receiptJson.contains("\"method\":\"unknown\"");
        }));
    }


    @Test
    void grantConsent_ShouldUseServerCapturedIpAndUa() {
        String capturedIp = "203.0.113.55";
        String capturedUa = "ServerUA/2.0";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("requestContext", new uk.gegc.kidsgptbackend.shared.util.RequestContext(capturedIp, capturedUa));
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
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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

        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PRIVACY_POLICY), eq(ConsentStatus.GRANTED)))
                .thenReturn(Optional.of(existing));
        for (ConsentType t : ConsentType.values()) {
            if (t != ConsentType.PRIVACY_POLICY) {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(t), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(saved));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                        eq(testUserId), eq(type), eq(ConsentStatus.GRANTED)))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.of(savedConsent));
            } else {
                when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
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
} 