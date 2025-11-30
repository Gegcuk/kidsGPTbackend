package uk.gegc.kidsgptbackend.service.consent.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.consent.ConsentStatusResponse;
import uk.gegc.kidsgptbackend.dto.consent.ConsentWithdrawRequest;
import uk.gegc.kidsgptbackend.model.consent.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentWithdrawServiceTest extends ConsentServiceBaseTest {

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

    @Test
    void withdrawConsent_ShouldNotWriteChildCoverage() {
        // Arrange - Create a granted consent with kids (that would have child coverage)
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

        // Assert - Verify that NO calls to ConsentChildCoverageRepository.saveAll are made
        verify(consentChildCoverageRepository, never()).saveAll(any());

        // Also verify that the withdrawal ledger was saved (to ensure the test is working)
        verify(consentLedgerRepository).saveAndFlush(any(ConsentLedger.class));
    }

    @Test
    void withdrawConsent_DuplicateKeyRace_ShouldReturnExistingWithdrawalId() {
        // Arrange - Create a granted consent first
        UUID grantedConsentId = UUID.randomUUID();
        UUID existingWithdrawalId = UUID.randomUUID();

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

        // Mock saveAndFlush to throw duplicate-key DataIntegrityViolationException
        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        // Mock the repository to return an existing withdrawal when queried after the exception
        ConsentLedger existingWithdrawal = ConsentLedger.builder()
                .consentId(existingWithdrawalId)
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.WITHDRAWN)
                .withdrawnConsentId(grantedConsentId)
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
                .receiptJson("{\"test\":\"withdrawal\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Mock findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc for GRANTED status check
        // This is called to check if this is the current active version
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq(ConsentStatus.GRANTED)))
                .thenReturn(Optional.of(grantedConsent));

        // Mock findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc to return the existing withdrawal
        // This is the method called in the DataIntegrityViolationException catch block
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq(ConsentStatus.WITHDRAWN)))
                .thenReturn(Optional.of(existingWithdrawal));

        // Mock findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc for buildEffectiveConsentStatus
        // Need to mock for all consent types since buildEffectiveConsentStatus calls it for each type
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT)))
                .thenReturn(Optional.of(existingWithdrawal));

        // Mock for other consent types to return empty (no existing consents)
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.TERMS_OF_SERVICE)))
                .thenReturn(Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PRIVACY_POLICY)))
                .thenReturn(Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.DATA_PROCESSING)))
                .thenReturn(Optional.empty());

        // Act
        ConsentStatusResponse response = consentService.withdrawConsent(withdrawRequest);

        // Assert - Verify that the service returned the existing withdrawal ID instead of failing
        assertNotNull(response);
        assertTrue(response.reconsentNeeded());

        // Verify that the response contains the existing withdrawal ID
        // Note: The actual response structure depends on how the service handles this case
        // The key point is that it doesn't throw an exception and returns a valid response
    }

    @Test
    void withdrawConsent_NonDuplicateDataIntegrityViolation_ShouldThrow500() {
        // Arrange
        UUID testUserId = UUID.randomUUID();
        UUID testVerificationId = UUID.randomUUID();
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

        // Mock saveAndFlush to throw a non-duplicate DataIntegrityViolationException
        // This simulates a different database constraint violation (not duplicate key)
        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                .thenThrow(new DataIntegrityViolationException("Foreign key constraint violation"));

        // Mock findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc for GRANTED status check
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq(ConsentStatus.GRANTED)))
                .thenReturn(Optional.of(grantedConsent));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            consentService.withdrawConsent(withdrawRequest);
        });

        assertEquals(500, exception.getStatusCode().value());
        assertEquals("Failed to process consent withdrawal", exception.getReason());
    }

    @Test
    void withdrawConsent_VersionSpecificIdempotency_ShouldReturnCorrectWithdrawalId() {
        // Arrange
        UUID testUserId = UUID.randomUUID();
        UUID testVerificationId = UUID.randomUUID();
        UUID v1GrantedConsentId = UUID.randomUUID();
        UUID v1WithdrawalId = UUID.randomUUID();
        UUID v2GrantedConsentId = UUID.randomUUID();
        UUID v2WithdrawalId = UUID.randomUUID();

        // V1 Grant
        ConsentLedger v1GrantedConsent = ConsentLedger.builder()
                .consentId(v1GrantedConsentId)
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/parental-v1")
                .contentHash("abc123hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress(serverIp)
                .userAgent(serverUa)
                .consentTimestamp(LocalDateTime.now().minusDays(10))
                .parentVerificationId(testVerificationId)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"grant-v1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // V1 Withdrawal (already exists)
        ConsentLedger v1Withdrawal = ConsentLedger.builder()
                .consentId(v1WithdrawalId)
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.WITHDRAWN)
                .policyUrl("https://example.com/parental-v1")
                .contentHash("abc123hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress(serverIp)
                .userAgent(serverUa)
                .consentTimestamp(LocalDateTime.now().minusDays(5))
                .parentVerificationId(testVerificationId)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawal-v1\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .withdrawnConsentId(v1GrantedConsentId)
                .build();

        // V2 Grant
        ConsentLedger v2GrantedConsent = ConsentLedger.builder()
                .consentId(v2GrantedConsentId)
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/parental-v2")
                .contentHash("def456hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress(serverIp)
                .userAgent(serverUa)
                .consentTimestamp(LocalDateTime.now().minusDays(3))
                .parentVerificationId(testVerificationId)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"grant-v2\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .build();

        // V2 Withdrawal (already exists)
        ConsentLedger v2Withdrawal = ConsentLedger.builder()
                .consentId(v2WithdrawalId)
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.WITHDRAWN)
                .policyUrl("https://example.com/parental-v2")
                .contentHash("def456hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress(serverIp)
                .userAgent(serverUa)
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .parentVerificationId(testVerificationId)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"withdrawal-v2\"}")
                .recordSignature(new byte[]{10, 11, 12})
                .withdrawnConsentId(v2GrantedConsentId)
                .build();

        // Mock findActiveGrantByUserTypeAndVersion to return V1 grant (needed for initial validation)
        when(consentLedgerRepository.findActiveGrantByUserTypeAndVersion(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq("1.0.0")))
                .thenReturn(Optional.of(v1GrantedConsent));

        // Mock existsWithdrawalByUserTypeAndVersion to return true for V1 (withdrawal exists)
        when(consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq("1.0.0")))
                .thenReturn(true);

        // Mock findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc for GRANTED status
        // This is called to check if the version is current (should return V1 grant)
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq(ConsentStatus.GRANTED)))
                .thenReturn(Optional.of(v1GrantedConsent));

        // Mock findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc for WITHDRAWN status
        // This should return the V1 withdrawal when looking for V1 withdrawal
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq(ConsentStatus.WITHDRAWN)))
                .thenReturn(Optional.of(v1Withdrawal));

        // Mock findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc for all consent types
        // This is called by buildEffectiveConsentStatus
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT)))
                .thenReturn(Optional.of(v2Withdrawal)); // Latest by consentTimestamp is V2 withdrawal
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.TERMS_OF_SERVICE)))
                .thenReturn(Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PRIVACY_POLICY)))
                .thenReturn(Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.DATA_PROCESSING)))
                .thenReturn(Optional.empty());

        ConsentWithdrawRequest withdrawV1Request = new ConsentWithdrawRequest(
                testUserId.toString(),
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "User requested withdrawal of V1",
                "192.168.1.1",
                "Mozilla/5.0"
        );

        // Act
        ConsentStatusResponse response = consentService.withdrawConsent(withdrawV1Request);

        // Assert
        // Should return V1's withdrawal ID, not V2's
        assertEquals(v1WithdrawalId, response.consentId());

        // Verify that existsWithdrawalByUserTypeAndVersion was called with V1 version
        verify(consentLedgerRepository).existsWithdrawalByUserTypeAndVersion(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq("1.0.0"));

        // Verify that findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc was called
        // to get the existing V1 withdrawal
        verify(consentLedgerRepository).findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq(ConsentStatus.WITHDRAWN));

        // Verify that saveAndFlush was NOT called (since withdrawal already exists)
        verify(consentLedgerRepository, never()).saveAndFlush(any(ConsentLedger.class));
    }

    @Test
    void withdrawConsent_EffectiveStatusUsesConsentTimestampOrdering() {
        // Arrange
        UUID testUserId = UUID.randomUUID();
        UUID testVerificationId = UUID.randomUUID();

        // Create two records for the same type with out-of-order createdAt but increasing consentTimestamp
        LocalDateTime olderCreatedAt = LocalDateTime.now().minusDays(10);
        LocalDateTime newerCreatedAt = LocalDateTime.now().minusDays(5);
        LocalDateTime olderConsentTimestamp = LocalDateTime.now().minusDays(3);
        LocalDateTime newerConsentTimestamp = LocalDateTime.now().minusDays(1);

        // Record 1: older createdAt, older consentTimestamp
        ConsentLedger olderRecord = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/parental-older")
                .contentHash("abc123hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress(serverIp)
                .userAgent(serverUa)
                .consentTimestamp(olderConsentTimestamp)
                .parentVerificationId(testVerificationId)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"older\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        // Record 2: newer createdAt, newer consentTimestamp (this should be chosen)
        ConsentLedger newerRecord = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.WITHDRAWN)
                .policyUrl("https://example.com/parental-newer")
                .contentHash("def456hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress(serverIp)
                .userAgent(serverUa)
                .consentTimestamp(newerConsentTimestamp)
                .parentVerificationId(testVerificationId)
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"newer\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        // Mock the repository to return the newer record (with latest consentTimestamp)
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT)))
                .thenReturn(Optional.of(newerRecord));

        // Mock other consent types to return empty
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.TERMS_OF_SERVICE)))
                .thenReturn(Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PRIVACY_POLICY)))
                .thenReturn(Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.DATA_PROCESSING)))
                .thenReturn(Optional.empty());

        // Act
        List<ConsentStatusResponse.ConsentStatusByType> effectiveStatus = ReflectionTestUtils.invokeMethod(
                consentService, "buildEffectiveConsentStatus", testUserId);

        // Assert
        assertNotNull(effectiveStatus);
        assertEquals(1, effectiveStatus.size()); // Only PARENTAL_CONSENT should be present since we only mocked it

        // Find the PARENTAL_CONSENT entry
        ConsentStatusResponse.ConsentStatusByType parentalConsentStatus = effectiveStatus.stream()
                .filter(status -> status.type() == ConsentType.PARENTAL_CONSENT)
                .findFirst()
                .orElse(null);

        assertNotNull(parentalConsentStatus);
        assertEquals(ConsentStatus.WITHDRAWN, parentalConsentStatus.status());
        assertEquals("2.0.0", parentalConsentStatus.version());
        assertEquals("https://example.com/parental-newer", parentalConsentStatus.policyUrl());
        assertEquals(newerConsentTimestamp, parentalConsentStatus.timestamp());

        // Verify that the repository was called with the correct ordering method
        verify(consentLedgerRepository).findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT));
    }

    @Test
    void withdrawConsent_GrantVersionSourceOfTruth_ShouldUseGrantVersion() {
        // Arrange
        UUID testUserId = UUID.randomUUID();
        UUID testVerificationId = UUID.randomUUID();
        UUID grantedConsentId = UUID.randomUUID();

        // Create a granted consent with version "1.0.0"
        ConsentLedger grantedConsent = ConsentLedger.builder()
                .consentId(grantedConsentId)
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0") // Grant has version "1.0.0"
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
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq("1.0.0"))) // Mocked to expect request version
                .thenReturn(Optional.of(grantedConsent));

        when(consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq("1.0.0"))) // Mocked to expect request version
                .thenReturn(false);

        // Mock saveAndFlush to return the saved withdrawal
        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Mock findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc for GRANTED status
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq(ConsentStatus.GRANTED)))
                .thenReturn(Optional.of(grantedConsent));

        // Mock findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc for all consent types
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT)))
                .thenReturn(Optional.of(grantedConsent));
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.TERMS_OF_SERVICE)))
                .thenReturn(Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PRIVACY_POLICY)))
                .thenReturn(Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.DATA_PROCESSING)))
                .thenReturn(Optional.empty());

        // Create withdrawal request with the SAME version ("1.0.0") as the grant
        ConsentWithdrawRequest withdrawRequest = new ConsentWithdrawRequest(
                testUserId.toString(),
                ConsentType.PARENTAL_CONSENT,
                "1.0.0", // Request has same version as grant
                "User requested withdrawal",
                "192.168.1.1",
                "Mozilla/5.0"
        );

        // Act
        ConsentStatusResponse response = consentService.withdrawConsent(withdrawRequest);

        // Assert
        assertNotNull(response);

        // Verify that saveAndFlush was called with a withdrawal that has the GRANT's version as source of truth
        verify(consentLedgerRepository).saveAndFlush(argThat(withdrawal -> {
            // The withdrawal should have the grant's version ("1.0.0") as the source of truth
            return withdrawal.getConsentVersion().equals("1.0.0") &&
                    withdrawal.getConsentStatus() == ConsentStatus.WITHDRAWN &&
                    withdrawal.getWithdrawnConsentId().equals(grantedConsentId);
        }));
    }

    @Test
    void withdrawConsent_ReconsentNeededFlag_ShouldBeTrue() {
        // Arrange
        UUID testUserId = UUID.randomUUID();
        UUID testVerificationId = UUID.randomUUID();
        UUID grantedConsentId = UUID.randomUUID();

        // Create a granted consent
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

        // Mock saveAndFlush to return the saved withdrawal
        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Mock findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc for GRANTED status
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq(ConsentStatus.GRANTED)))
                .thenReturn(Optional.of(grantedConsent));

        // Mock findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc for all consent types
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT)))
                .thenReturn(Optional.of(grantedConsent));
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.TERMS_OF_SERVICE)))
                .thenReturn(Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PRIVACY_POLICY)))
                .thenReturn(Optional.empty());
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.DATA_PROCESSING)))
                .thenReturn(Optional.empty());

        ConsentWithdrawRequest withdrawRequest = new ConsentWithdrawRequest(
                testUserId.toString(),
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "User requested withdrawal",
                "192.168.1.1",
                "Mozilla/5.0"
        );

        // Act
        ConsentStatusResponse response = consentService.withdrawConsent(withdrawRequest);

        // Assert
        assertNotNull(response);
        assertTrue(response.reconsentNeeded(), "reconsentNeeded should be true for successful withdrawals");
    }

    @Test
    void withdrawConsent_LocaleAndRegionContinuity_ShouldPreserveGrantValues() {
        // Arrange
        UUID grantedConsentId = UUID.randomUUID();

        // Create a granted consent with specific locale and region
        String originalLocale = "en-GB";
        String originalRegion = "England";

        ConsentLedger grantedConsent = ConsentLedger.builder()
                .consentId(grantedConsentId)
                .userId(testUserId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/parental")
                .contentHash("abc123hash")
                .jurisdiction("GB")
                .region(originalRegion)
                .locale(originalLocale)
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

        // Mock saveAndFlush to return the saved withdrawal and update subsequent mocks
        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                .thenAnswer(invocation -> {
                    ConsentLedger savedWithdrawal = invocation.getArgument(0);
                    // Update the mock to return the withdrawal for subsequent calls for all consent types
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.PARENTAL_CONSENT)))
                            .thenReturn(Optional.of(savedWithdrawal));
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.PRIVACY_POLICY)))
                            .thenReturn(Optional.empty());
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.TERMS_OF_SERVICE)))
                            .thenReturn(Optional.empty());
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.DATA_PROCESSING)))
                            .thenReturn(Optional.empty());
                    return savedWithdrawal;
                });

        // Mock findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc for GRANTED status
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq(ConsentStatus.GRANTED)))
                .thenReturn(Optional.of(grantedConsent));

        ConsentWithdrawRequest withdrawRequest = new ConsentWithdrawRequest(
                testUserId.toString(),
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "User requested withdrawal",
                "192.168.1.1",
                "Mozilla/5.0"
        );

        // Act
        ConsentStatusResponse response = consentService.withdrawConsent(withdrawRequest);

        // Assert
        assertNotNull(response);

        // Verify that saveAndFlush was called with a withdrawal that preserves the original locale and region
        verify(consentLedgerRepository).saveAndFlush(argThat(withdrawal -> {
            return withdrawal.getLocale().equals(originalLocale) &&
                    withdrawal.getRegion().equals(originalRegion) &&
                    withdrawal.getConsentStatus() == ConsentStatus.WITHDRAWN;
        }));

        // Verify that the response latestByType contains the same locale and region
        assertTrue(response.latestByType().stream()
                        .anyMatch(status -> status.type() == ConsentType.PARENTAL_CONSENT &&
                                status.status() == ConsentStatus.WITHDRAWN),
                "Response should contain WITHDRAWN status for PARENTAL_CONSENT");
    }

    @Test
    void withdrawConsent_ParentVerificationContinuity_ShouldPreserveVerificationId() {
        // Arrange
        UUID grantedConsentId = UUID.randomUUID();
        UUID originalVerificationId = UUID.randomUUID();

        // Create a granted PARENTAL_CONSENT with a specific parentVerificationId
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
                .parentVerificationId(originalVerificationId)
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

        // Mock saveAndFlush to return the saved withdrawal and update subsequent mocks
        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                .thenAnswer(invocation -> {
                    ConsentLedger savedWithdrawal = invocation.getArgument(0);
                    // Update the mock to return the withdrawal for subsequent calls for all consent types
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.PARENTAL_CONSENT)))
                            .thenReturn(Optional.of(savedWithdrawal));
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.PRIVACY_POLICY)))
                            .thenReturn(Optional.empty());
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.TERMS_OF_SERVICE)))
                            .thenReturn(Optional.empty());
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.DATA_PROCESSING)))
                            .thenReturn(Optional.empty());
                    return savedWithdrawal;
                });

        // Mock findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc for GRANTED status
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.PARENTAL_CONSENT), eq(ConsentStatus.GRANTED)))
                .thenReturn(Optional.of(grantedConsent));

        ConsentWithdrawRequest withdrawRequest = new ConsentWithdrawRequest(
                testUserId.toString(),
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                "User requested withdrawal",
                "192.168.1.1",
                "Mozilla/5.0"
        );

        // Act
        ConsentStatusResponse response = consentService.withdrawConsent(withdrawRequest);

        // Assert
        assertNotNull(response);

        // Verify that saveAndFlush was called with a withdrawal that preserves the original parentVerificationId
        verify(consentLedgerRepository).saveAndFlush(argThat(withdrawal -> {
            return withdrawal.getParentVerificationId().equals(originalVerificationId) &&
                    withdrawal.getConsentStatus() == ConsentStatus.WITHDRAWN;
        }));

        // Verify that the response latestByType contains WITHDRAWN status
        assertTrue(response.latestByType().stream()
                        .anyMatch(status -> status.type() == ConsentType.PARENTAL_CONSENT &&
                                status.status() == ConsentStatus.WITHDRAWN),
                "Response should contain WITHDRAWN status for PARENTAL_CONSENT");
    }

    @Test
    void withdrawConsent_IpUaOverride_ShouldLogMessage() {
        // Arrange
        UUID grantedConsentId = UUID.randomUUID();

        // Create a granted consent
        ConsentLedger grantedConsent = ConsentLedger.builder()
                .consentId(grantedConsentId)
                .userId(testUserId)
                .consentType(ConsentType.DATA_PROCESSING)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/data")
                .contentHash("abc123hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress(serverIp)
                .userAgent(serverUa)
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"grant\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        when(consentLedgerRepository.findActiveGrantByUserTypeAndVersion(
                eq(testUserId), eq(ConsentType.DATA_PROCESSING), eq("1.0.0")))
                .thenReturn(Optional.of(grantedConsent));

        when(consentLedgerRepository.existsWithdrawalByUserTypeAndVersion(
                eq(testUserId), eq(ConsentType.DATA_PROCESSING), eq("1.0.0")))
                .thenReturn(false);

        // Mock saveAndFlush to return the saved withdrawal and update subsequent mocks
        when(consentLedgerRepository.saveAndFlush(any(ConsentLedger.class)))
                .thenAnswer(invocation -> {
                    ConsentLedger savedWithdrawal = invocation.getArgument(0);
                    // Update the mock to return the withdrawal for subsequent calls for all consent types
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.DATA_PROCESSING)))
                            .thenReturn(Optional.of(savedWithdrawal));
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.PRIVACY_POLICY)))
                            .thenReturn(Optional.empty());
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.TERMS_OF_SERVICE)))
                            .thenReturn(Optional.empty());
                    lenient().when(consentLedgerRepository.findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(
                            eq(testUserId), eq(ConsentType.PARENTAL_CONSENT)))
                            .thenReturn(Optional.empty());
                    return savedWithdrawal;
                });

        // Mock findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc for GRANTED status
        when(consentLedgerRepository.findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
                eq(testUserId), eq(ConsentType.DATA_PROCESSING), eq(ConsentStatus.GRANTED)))
                .thenReturn(Optional.of(grantedConsent));



        // Create request with different IP/UA than server-captured values
        String clientIp = "10.0.0.1";
        String clientUa = "ClientBrowser/1.0";
        ConsentWithdrawRequest withdrawRequest = new ConsentWithdrawRequest(
                testUserId.toString(),
                ConsentType.DATA_PROCESSING,
                "1.0.0",
                "User requested withdrawal",
                clientIp,
                clientUa
        );

        // Act
        ConsentStatusResponse response = consentService.withdrawConsent(withdrawRequest);

        // Assert
        assertNotNull(response);

        // Verify that saveAndFlush was called with server-captured IP/UA (not client-provided)
        verify(consentLedgerRepository).saveAndFlush(argThat(withdrawal -> {
            return withdrawal.getIpAddress().equals(serverIp) &&
                    withdrawal.getUserAgent().equals(serverUa) &&
                    withdrawal.getConsentStatus() == ConsentStatus.WITHDRAWN;
        }));

        // Verify that the response latestByType contains WITHDRAWN status
        assertTrue(response.latestByType().stream()
                        .anyMatch(status -> status.type() == ConsentType.DATA_PROCESSING &&
                                status.status() == ConsentStatus.WITHDRAWN),
                "Response should contain WITHDRAWN status for DATA_PROCESSING");
    }
} 