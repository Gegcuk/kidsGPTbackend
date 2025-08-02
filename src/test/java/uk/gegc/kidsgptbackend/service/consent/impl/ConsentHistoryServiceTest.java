package uk.gegc.kidsgptbackend.service.consent.impl;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.consent.ConsentGrantRequest;
import uk.gegc.kidsgptbackend.dto.consent.ConsentHistoryResponse;
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
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentHistoryServiceTest extends ConsentServiceBaseTest {

    @Test
    @DisplayName("page < 0 -> 400 BAD_REQUEST")
    void pageNegative_throwsBadRequest() {
        String userId = UUID.randomUUID().toString();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> consentService.getConsentHistory(userId, -1, 20)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("Page number must be non-negative", ex.getReason());
        verifyNoInteractions(consentLedgerRepository, consentChildCoverageRepository);
    }

    @Test
    @DisplayName("size <= 0 -> 400 BAD_REQUEST")
    void sizeZeroOrLess_throwsBadRequest() {
        String userId = UUID.randomUUID().toString();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> consentService.getConsentHistory(userId, 0, 0)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("Page size must be between 1 and 100", ex.getReason());
        verifyNoInteractions(consentLedgerRepository, consentChildCoverageRepository);
    }

    @Test
    @DisplayName("size > 100 -> 400 BAD_REQUEST")
    void sizeGreaterThan100_throwsBadRequest() {
        String userId = UUID.randomUUID().toString();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> consentService.getConsentHistory(userId, 0, 101)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("Page size must be between 1 and 100", ex.getReason());
        verifyNoInteractions(consentLedgerRepository, consentChildCoverageRepository);
    }

    // ---------- 2) Invalid userId UUID ----------
    @Test
    @DisplayName("Non-UUID userId -> 400 BAD_REQUEST (Invalid user ID format)")
    void invalidUuidUserId_throwsBadRequest() {
        String notAUuid = "not-a-uuid";

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> consentService.getConsentHistory(notAUuid, 0, 20)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("Invalid user ID format", ex.getReason());
        verifyNoInteractions(consentLedgerRepository, consentChildCoverageRepository);
    }

    // ---------- 3) Empty page result handling ----------
    @Test
    @DisplayName("Empty Page -> entries=[], total=0, correct paging metadata")
    void emptyPage_returnsEmptyPayloadAndZeroTotals() {
        // Given
        String userId = UUID.randomUUID().toString();
        UUID userUuid = UUID.fromString(userId);
        int page = 0;
        int size = 20;

        // The service builds PageRequest with Sort: consentTimestamp DESC, createdAt DESC
        // Return an empty page with the same pageable (so metadata matches)
        // We capture the pageable to verify sorting and paging.
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(consentLedgerRepository.findByUserId(eq(userUuid), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable p = inv.getArgument(1);
                    // Return an empty Page with that pageable
                    return Page.empty(p);
                });

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse resp =
                consentService.getConsentHistory(userId, page, size);

        // Then: response metadata
        assertNotNull(resp);
        assertEquals(userId, resp.userId());
        assertNotNull(resp.entries());
        assertTrue(resp.entries().isEmpty());
        assertEquals(page, resp.page());
        assertEquals(size, resp.size());
        assertEquals(0L, resp.total());
        assertEquals(0, resp.totalPages());
        assertFalse(resp.hasNext());
        assertFalse(resp.hasPrevious());

        // Verify repo called once with expected pageable (page=0,size=20, sort by consentTimestamp DESC, createdAt DESC)
        verify(consentLedgerRepository).findByUserId(eq(userUuid), pageableCaptor.capture());
        Pageable used = pageableCaptor.getValue();
        assertEquals(page, used.getPageNumber());
        assertEquals(size, used.getPageSize());

        // Verify sort orders
        Sort.Order first = used.getSort().getOrderFor("consentTimestamp");
        Sort.Order second = used.getSort().getOrderFor("createdAt");
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(Sort.Direction.DESC, first.getDirection());
        assertEquals(Sort.Direction.DESC, second.getDirection());

        // Coverage repo should NOT be called for empty page
        verifyNoInteractions(consentChildCoverageRepository);
    }

    // ---------- 4) Batch coverage fetch (no N+1) ----------
    @Test
    @DisplayName("Batch coverage fetch (no N+1) - service calls findByConsentIds once with all relevant IDs")
    void batchCoverageFetch_noN1_queries() {
        // Given: N ledger rows with different consent IDs
        String userId = UUID.randomUUID().toString();
        UUID userUuid = UUID.fromString(userId);
        int page = 0;
        int size = 3;

        // Create 3 consent ledgers with different IDs
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();

        ConsentLedger ledger1 = ConsentLedger.builder()
                .consentId(consentId1)
                .userId(userUuid)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy1")
                .contentHash("hash1")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(3))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .receiptJson("{\"test\":\"ledger1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        ConsentLedger ledger2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(userUuid)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy2")
                .contentHash("hash2")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONTRACT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(2))
                .retentionExpiresAt(LocalDateTime.now().plusYears(6))
                .receiptJson("{\"test\":\"ledger2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        ConsentLedger ledger3 = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(userUuid)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("3.0.0")
                .consentStatus(ConsentStatus.WITHDRAWN)
                .policyUrl("https://example.com/policy3")
                .contentHash("hash3")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.LEGITIMATE_INTEREST)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(5))
                .receiptJson("{\"test\":\"ledger3\"}")
                .recordSignature(new byte[]{7, 8, 9})
                .withdrawnConsentId(consentId2)
                .build();

        List<ConsentLedger> ledgers = List.of(ledger1, ledger2, ledger3);

        // Mock the ledger repository to return the 3 ledgers
        when(consentLedgerRepository.findByUserId(eq(userUuid), any(Pageable.class)))
                .thenReturn(new PageImpl<>(ledgers, PageRequest.of(page, size), 3L));

        // Create coverage data for the consents
        UUID kid1 = UUID.randomUUID();
        UUID kid2 = UUID.randomUUID();
        UUID kid3 = UUID.randomUUID();

        ConsentChildCoverage coverage1 = ConsentChildCoverage.builder()
                .consentId(consentId1)
                .kidId(kid1)
                .build();

        ConsentChildCoverage coverage2a = ConsentChildCoverage.builder()
                .consentId(consentId2)
                .kidId(kid2)
                .build();

        ConsentChildCoverage coverage2b = ConsentChildCoverage.builder()
                .consentId(consentId2)
                .kidId(kid3)
                .build();

        List<ConsentChildCoverage> coverages = List.of(coverage1, coverage2a, coverage2b);

        // Mock the coverage repository to return coverage data
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(coverages);

        // When: service is called
        ConsentHistoryResponse.PaginatedConsentHistoryResponse response =
                consentService.getConsentHistory(userId, page, size);

        // Then: verify the response structure
        assertNotNull(response);
        assertEquals(userId, response.userId());
        assertEquals(3, response.entries().size());
        assertEquals(3L, response.total());

        // Verify ledger repository was called once with correct parameters
        verify(consentLedgerRepository).findByUserId(eq(userUuid), any(Pageable.class));

        // Verify coverage repository was called ONCE with ALL consent IDs
        ArgumentCaptor<List<UUID>> consentIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(consentChildCoverageRepository).findByConsentIds(consentIdsCaptor.capture());

        List<UUID> capturedConsentIds = consentIdsCaptor.getValue();
        assertEquals(3, capturedConsentIds.size());
        assertTrue(capturedConsentIds.contains(consentId1));
        assertTrue(capturedConsentIds.contains(consentId2));
        assertTrue(capturedConsentIds.contains(consentId3));

        // Verify no additional calls to coverage repository methods
        verifyNoMoreInteractions(consentChildCoverageRepository);

        // Verify the entries have correct coverage data
        ConsentHistoryResponse.ConsentHistoryEntry entry1 = response.entries().get(0);
        assertEquals(consentId1.toString(), entry1.consentId());
        assertEquals(1, entry1.coveredKids().size());
        assertTrue(entry1.coveredKids().contains(kid1.toString()));

        ConsentHistoryResponse.ConsentHistoryEntry entry2 = response.entries().get(1);
        assertEquals(consentId2.toString(), entry2.consentId());
        assertEquals(2, entry2.coveredKids().size());
        assertTrue(entry2.coveredKids().contains(kid2.toString()));
        assertTrue(entry2.coveredKids().contains(kid3.toString()));

        ConsentHistoryResponse.ConsentHistoryEntry entry3 = response.entries().get(2);
        assertEquals(consentId3.toString(), entry3.consentId());
        assertEquals(0, entry3.coveredKids().size()); // No coverage for this consent
    }

    @Test
    @DisplayName("Batch coverage fetch (no N+1) - no coverage records for any consent IDs")
    void batchCoverageFetch_noCoverageRecords_skipsCoverageQuery() {
        // Given: ledger rows with valid consent IDs but no coverage records exist
        String userId = UUID.randomUUID().toString();
        UUID userUuid = UUID.fromString(userId);
        int page = 0;
        int size = 2;

        // Create 2 consent ledgers with valid IDs
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();

        ConsentLedger ledger1 = ConsentLedger.builder()
                .consentId(consentId1)
                .userId(userUuid)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy1")
                .contentHash("hash1")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONTRACT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(6))
                .receiptJson("{\"test\":\"ledger1\"}")
                .recordSignature(new byte[]{1, 2, 3})
                .build();

        ConsentLedger ledger2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(userUuid)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy2")
                .contentHash("hash2")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.LEGITIMATE_INTEREST)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now())
                .retentionExpiresAt(LocalDateTime.now().plusYears(5))
                .receiptJson("{\"test\":\"ledger2\"}")
                .recordSignature(new byte[]{4, 5, 6})
                .build();

        List<ConsentLedger> ledgers = List.of(ledger1, ledger2);

        // Mock the ledger repository to return the ledgers
        when(consentLedgerRepository.findByUserId(eq(userUuid), any(Pageable.class)))
                .thenReturn(new PageImpl<>(ledgers, PageRequest.of(page, size), 2L));

        // Mock the coverage repository to return empty list (no coverage records exist)
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(Collections.emptyList());

        // When: service is called
        ConsentHistoryResponse.PaginatedConsentHistoryResponse response =
                consentService.getConsentHistory(userId, page, size);

        // Then: verify the response structure
        assertNotNull(response);
        assertEquals(userId, response.userId());
        assertEquals(2, response.entries().size());
        assertEquals(2L, response.total());

        // Verify ledger repository was called once
        verify(consentLedgerRepository).findByUserId(eq(userUuid), any(Pageable.class));

        // Verify coverage repository was called ONCE with both consent IDs
        ArgumentCaptor<List<UUID>> consentIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(consentChildCoverageRepository).findByConsentIds(consentIdsCaptor.capture());

        List<UUID> capturedConsentIds = consentIdsCaptor.getValue();
        assertEquals(2, capturedConsentIds.size());
        assertTrue(capturedConsentIds.contains(consentId1));
        assertTrue(capturedConsentIds.contains(consentId2));

        // Verify no additional calls to coverage repository methods
        verifyNoMoreInteractions(consentChildCoverageRepository);

        // Verify both entries have empty coverage lists
        ConsentHistoryResponse.ConsentHistoryEntry entry1 = response.entries().get(0);
        assertEquals(consentId1.toString(), entry1.consentId());
        assertEquals(0, entry1.coveredKids().size());

        ConsentHistoryResponse.ConsentHistoryEntry entry2 = response.entries().get(1);
        assertEquals(consentId2.toString(), entry2.consentId());
        assertEquals(0, entry2.coveredKids().size());
    }
} 