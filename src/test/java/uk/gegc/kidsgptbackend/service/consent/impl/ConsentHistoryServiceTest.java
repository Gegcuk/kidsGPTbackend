package uk.gegc.kidsgptbackend.service.consent.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.consent.ConsentHistoryResponse;
import uk.gegc.kidsgptbackend.model.consent.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentHistoryServiceTest extends ConsentServiceBaseTest {

    @Test
    @DisplayName("Mapping: all fields copied correctly - verify each ConsentHistoryEntry field mirrors ConsentLedger values")
    void mapping_allFieldsCopiedCorrectly() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        UUID parentVerificationId = UUID.randomUUID();
        UUID withdrawnConsentId = UUID.randomUUID();
        LocalDateTime consentTimestamp = LocalDateTime.now().minusDays(1);
        LocalDateTime retentionExpiresAt = LocalDateTime.now().plusYears(7);
        LocalDateTime createdAt = LocalDateTime.now().minusDays(2);

        ConsentLedger consentLedger = ConsentLedger.builder()
                .consentId(consentId)
                .userId(userId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/privacy-v2")
                .contentHash("def456hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0 (Test Browser)")
                .consentTimestamp(consentTimestamp)
                .parentVerificationId(parentVerificationId)
                .retentionExpiresAt(retentionExpiresAt)
                .withdrawnConsentId(withdrawnConsentId)
                .createdAt(createdAt)
                .build();

        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(List.of(consentLedger));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);

        // No child coverage for this test
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userId.toString(), 0, 20);

        // Then
        assertNotNull(result);
        assertEquals(1, result.entries().size());
        
        ConsentHistoryResponse.ConsentHistoryEntry entry = result.entries().get(0);
        
        // Verify all fields are correctly mapped
        assertEquals(consentId.toString(), entry.consentId());
        assertEquals(consentLedger.getConsentType(), entry.consentType());
        assertEquals(consentLedger.getConsentVersion(), entry.consentVersion());
        assertEquals(consentLedger.getConsentStatus(), entry.consentStatus());
        assertEquals(consentLedger.getPolicyUrl(), entry.policyUrl());
        assertEquals(consentLedger.getContentHash(), entry.contentHash());
        assertEquals(consentLedger.getJurisdiction(), entry.jurisdiction());
        assertEquals(consentLedger.getRegion(), entry.region());
        assertEquals(consentLedger.getLocale(), entry.locale());
        assertEquals(consentLedger.getLawfulBasis(), entry.lawfulBasis());
        assertEquals(consentLedger.getSource(), entry.source());
        assertEquals(consentLedger.getIpAddress(), entry.ipAddress());
        assertEquals(consentLedger.getUserAgent(), entry.userAgent());
        assertEquals(consentLedger.getConsentTimestamp(), entry.consentTimestamp());
        assertEquals(consentLedger.getRetentionExpiresAt(), entry.retentionExpiresAt());
        assertEquals(consentLedger.getCreatedAt(), entry.createdAt());
        
        // Verify UUID fields are stringified
        assertEquals(parentVerificationId.toString(), entry.parentVerificationId());
        assertEquals(withdrawnConsentId.toString(), entry.withdrawnConsentId());
        
        // Verify coveredKids is empty when no coverage exists
        assertTrue(entry.coveredKids().isEmpty());
    }

    @Test
    @DisplayName("Mapping: null UUID fields handled correctly")
    void mapping_nullUuidFieldsHandledCorrectly() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        LocalDateTime consentTimestamp = LocalDateTime.now().minusDays(1);
        LocalDateTime retentionExpiresAt = LocalDateTime.now().plusYears(7);
        LocalDateTime createdAt = LocalDateTime.now().minusDays(2);

        ConsentLedger consentLedger = ConsentLedger.builder()
                .consentId(consentId)
                .userId(userId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/terms")
                .contentHash("abc123hash")
                .jurisdiction("US")
                .region("California")
                .locale("en-US")
                .lawfulBasis(LawfulBasis.CONTRACT)
                .source(ConsentSource.ANDROID)
                .ipAddress("10.0.0.1")
                .userAgent("Mobile App v1.0")
                .consentTimestamp(consentTimestamp)
                .parentVerificationId(null) // Null parent verification ID
                .retentionExpiresAt(retentionExpiresAt)
                .withdrawnConsentId(null) // Null withdrawn consent ID
                .createdAt(createdAt)
                .build();

        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(List.of(consentLedger));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);

        // No child coverage for this test
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userId.toString(), 0, 20);

        // Then
        assertNotNull(result);
        assertEquals(1, result.entries().size());
        
        ConsentHistoryResponse.ConsentHistoryEntry entry = result.entries().get(0);
        
        // Verify null UUID fields remain null in the response
        assertNull(entry.parentVerificationId());
        assertNull(entry.withdrawnConsentId());
        
        // Verify other fields are still correctly mapped
        assertEquals(consentId.toString(), entry.consentId());
        assertEquals(consentLedger.getConsentType(), entry.consentType());
        assertEquals(consentLedger.getConsentVersion(), entry.consentVersion());
        assertEquals(consentLedger.getConsentStatus(), entry.consentStatus());
        assertEquals(consentLedger.getPolicyUrl(), entry.policyUrl());
        assertEquals(consentLedger.getContentHash(), entry.contentHash());
        assertEquals(consentLedger.getJurisdiction(), entry.jurisdiction());
        assertEquals(consentLedger.getRegion(), entry.region());
        assertEquals(consentLedger.getLocale(), entry.locale());
        assertEquals(consentLedger.getLawfulBasis(), entry.lawfulBasis());
        assertEquals(consentLedger.getSource(), entry.source());
        assertEquals(consentLedger.getIpAddress(), entry.ipAddress());
        assertEquals(consentLedger.getUserAgent(), entry.userAgent());
        assertEquals(consentLedger.getConsentTimestamp(), entry.consentTimestamp());
        assertEquals(consentLedger.getRetentionExpiresAt(), entry.retentionExpiresAt());
        assertEquals(consentLedger.getCreatedAt(), entry.createdAt());
    }

    @Test
    @DisplayName("coveredKids distinct + sorted - input coverage includes duplicates/unordered => output is unique and sorted")
    void coveredKids_distinctAndSorted() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        UUID kid1 = UUID.randomUUID();
        UUID kid2 = UUID.randomUUID();
        UUID kid3 = UUID.randomUUID();
        UUID kid4 = UUID.randomUUID();

        ConsentLedger consentLedger = ConsentLedger.builder()
                .consentId(consentId)
                .userId(userId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/parental")
                .contentHash("parental123hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0 (Test Browser)")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(7))
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();

        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(List.of(consentLedger));
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);

        // Create coverage data with duplicates and unordered kid IDs
        // Input order: kid3, kid1, kid2, kid1 (duplicate), kid4, kid2 (duplicate)
        // Expected output: [kid1, kid2, kid3, kid4] (sorted and distinct)
        ConsentChildCoverage coverage1 = ConsentChildCoverage.builder()
                .consentId(consentId)
                .kidId(kid3) // kid3 first
                .build();
        ConsentChildCoverage coverage2 = ConsentChildCoverage.builder()
                .consentId(consentId)
                .kidId(kid1) // kid1 second
                .build();
        ConsentChildCoverage coverage3 = ConsentChildCoverage.builder()
                .consentId(consentId)
                .kidId(kid2) // kid2 third
                .build();
        ConsentChildCoverage coverage4 = ConsentChildCoverage.builder()
                .consentId(consentId)
                .kidId(kid1) // kid1 duplicate
                .build();
        ConsentChildCoverage coverage5 = ConsentChildCoverage.builder()
                .consentId(consentId)
                .kidId(kid4) // kid4 fifth
                .build();
        ConsentChildCoverage coverage6 = ConsentChildCoverage.builder()
                .consentId(consentId)
                .kidId(kid2) // kid2 duplicate
                .build();

        List<ConsentChildCoverage> coverages = List.of(coverage1, coverage2, coverage3, coverage4, coverage5, coverage6);
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(coverages);

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userId.toString(), 0, 20);

        // Then
        assertNotNull(result);
        assertEquals(1, result.entries().size());
        
        ConsentHistoryResponse.ConsentHistoryEntry entry = result.entries().get(0);
        List<String> coveredKids = entry.coveredKids();
        
        // Verify coveredKids is distinct and sorted
        assertEquals(4, coveredKids.size()); // Should have 4 unique kids (duplicates removed)
        
        // Verify the order is sorted (alphabetically by UUID string)
        List<String> expectedOrder = List.of(
                kid1.toString(),
                kid2.toString(), 
                kid3.toString(),
                kid4.toString()
        ).stream().sorted().collect(Collectors.toList());
        assertEquals(expectedOrder, coveredKids);
        
        // Verify no duplicates exist
        assertEquals(coveredKids.size(), coveredKids.stream().distinct().count());
        
        // Verify the list is actually sorted
        List<String> sortedCopy = new ArrayList<>(coveredKids);
        Collections.sort(sortedCopy);
        assertEquals(sortedCopy, coveredKids);
    }

    @Test
    @DisplayName("Ordering not overridden in service - service honors repository/page ordering (no resorting)")
    void ordering_notOverriddenInService() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        
        LocalDateTime baseTime = LocalDateTime.now().minusDays(1);
        
        // Create 3 consent ledgers with specific ordering that should be preserved
        // Order: consentId3 (newest), consentId1 (middle), consentId2 (oldest)
        ConsentLedger ledger1 = ConsentLedger.builder()
                .consentId(consentId1)
                .userId(userId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/privacy")
                .contentHash("hash1")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(baseTime.plusHours(2)) // Middle timestamp
                .retentionExpiresAt(baseTime.plusYears(7))
                .createdAt(baseTime.plusHours(2))
                .build();

        ConsentLedger ledger2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(userId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/terms")
                .contentHash("hash2")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONTRACT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(baseTime) // Oldest timestamp
                .retentionExpiresAt(baseTime.plusYears(6))
                .createdAt(baseTime)
                .build();

        ConsentLedger ledger3 = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(userId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("3.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/parental")
                .contentHash("hash3")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(baseTime.plusHours(4)) // Newest timestamp
                .retentionExpiresAt(baseTime.plusYears(8))
                .createdAt(baseTime.plusHours(4))
                .build();

        // Create the list in the order that the repository should return them
        // (sorted by consentTimestamp DESC, createdAt DESC)
        List<ConsentLedger> ledgersInOrder = List.of(ledger3, ledger1, ledger2);
        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(ledgersInOrder, PageRequest.of(0, 20), 3L);
        
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);

        // No child coverage for this test
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userId.toString(), 0, 20);

        // Then
        assertNotNull(result);
        assertEquals(3, result.entries().size());
        
        // Verify the order is preserved exactly as returned by the repository
        // Expected order: ledger3 (newest), ledger1 (middle), ledger2 (oldest)
        List<ConsentHistoryResponse.ConsentHistoryEntry> entries = result.entries();
        
        assertEquals(consentId3.toString(), entries.get(0).consentId());
        assertEquals(ConsentType.PARENTAL_CONSENT, entries.get(0).consentType());
        assertEquals("3.0.0", entries.get(0).consentVersion());
        
        assertEquals(consentId1.toString(), entries.get(1).consentId());
        assertEquals(ConsentType.PRIVACY_POLICY, entries.get(1).consentType());
        assertEquals("1.0.0", entries.get(1).consentVersion());
        
        assertEquals(consentId2.toString(), entries.get(2).consentId());
        assertEquals(ConsentType.TERMS_OF_SERVICE, entries.get(2).consentType());
        assertEquals("2.0.0", entries.get(2).consentVersion());
        
        // Verify the timestamps confirm the ordering
        assertTrue(entries.get(0).consentTimestamp().isAfter(entries.get(1).consentTimestamp()));
        assertTrue(entries.get(1).consentTimestamp().isAfter(entries.get(2).consentTimestamp()));
        
        // Verify that the service called the repository with the correct Pageable
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), pageableCaptor.capture());
        
        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(0, capturedPageable.getPageNumber());
        assertEquals(20, capturedPageable.getPageSize());
        
        // Verify the sort order is correct
        Sort sort = capturedPageable.getSort();
        Sort.Order firstOrder = sort.getOrderFor("consentTimestamp");
        Sort.Order secondOrder = sort.getOrderFor("createdAt");
        
        assertNotNull(firstOrder);
        assertNotNull(secondOrder);
        assertEquals(Sort.Direction.DESC, firstOrder.getDirection());
        assertEquals(Sort.Direction.DESC, secondOrder.getDirection());
    }

    @Test
    @DisplayName("Repository exception surfaces as 500 - consentLedgerRepository throws exception")
    void repositoryException_consentLedgerRepository_throws500() {
        // Given
        UUID userId = UUID.randomUUID();
        String userIdString = userId.toString();
        
        // Mock consentLedgerRepository to throw a runtime exception
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            consentService.getConsentHistory(userIdString, 0, 20);
        });

        // Verify the exception details
        assertEquals(500, exception.getStatusCode().value());
        assertEquals("Failed to retrieve consent history", exception.getReason());
        
        // Verify the repository was called
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class));
        
        // Verify coverage repository was not called (since ledger repository failed first)
        verifyNoInteractions(consentChildCoverageRepository);
    }

    @Test
    @DisplayName("Repository exception surfaces as 500 - consentChildCoverageRepository throws exception")
    void repositoryException_consentChildCoverageRepository_throws500() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        String userIdString = userId.toString();
        
        // Create a valid consent ledger
        ConsentLedger consentLedger = ConsentLedger.builder()
                .consentId(consentId)
                .userId(userId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/privacy")
                .contentHash("hash123")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(7))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(List.of(consentLedger));
        
        // Mock consentLedgerRepository to return valid data
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);

        // Mock consentChildCoverageRepository to throw a runtime exception
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenThrow(new RuntimeException("Coverage query failed"));

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            consentService.getConsentHistory(userIdString, 0, 20);
        });

        // Verify the exception details
        assertEquals(500, exception.getStatusCode().value());
        assertEquals("Failed to retrieve consent history", exception.getReason());
        
        // Verify both repositories were called
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class));
        verify(consentChildCoverageRepository).findByConsentIds(List.of(consentId));
    }

    @Test
    @DisplayName("Repository exception surfaces as 500 - DataIntegrityViolationException from ledger repository")
    void repositoryException_dataIntegrityViolation_throws500() {
        // Given
        UUID userId = UUID.randomUUID();
        String userIdString = userId.toString();
        
        // Mock consentLedgerRepository to throw DataIntegrityViolationException
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenThrow(new DataIntegrityViolationException("Database constraint violation"));

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            consentService.getConsentHistory(userIdString, 0, 20);
        });

        // Verify the exception details
        assertEquals(500, exception.getStatusCode().value());
        assertEquals("Failed to retrieve consent history", exception.getReason());
        
        // Verify the repository was called
        verify(consentLedgerRepository).findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class));
        
        // Verify coverage repository was not called
        verifyNoInteractions(consentChildCoverageRepository);
    }

    @Test
    @DisplayName("Pagination metadata computation - total=0, size=20")
    void paginationMetadata_total0_size20() {
        // Given
        UUID userId = UUID.randomUUID();
        String userIdString = userId.toString();
        
        // Mock empty page result
        Page<ConsentLedger> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(emptyPage);

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userIdString, 0, 20);

        // Then
        assertNotNull(result);
        assertEquals(0, result.entries().size());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(0, result.total());
        assertEquals(0, result.totalPages());
        assertFalse(result.hasNext());
        assertFalse(result.hasPrevious());
    }

    @Test
    @DisplayName("Pagination metadata computation - total=1, size=20")
    void paginationMetadata_total1_size20() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        String userIdString = userId.toString();
        
        // Create one consent ledger
        ConsentLedger consentLedger = ConsentLedger.builder()
                .consentId(consentId)
                .userId(userId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/privacy")
                .contentHash("hash123")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(7))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        Page<ConsentLedger> page = new PageImpl<>(List.of(consentLedger), PageRequest.of(0, 20), 1L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);

        // No child coverage for this test
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userIdString, 0, 20);

        // Then
        assertNotNull(result);
        assertEquals(1, result.entries().size());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1, result.total());
        assertEquals(1, result.totalPages());
        assertFalse(result.hasNext());
        assertFalse(result.hasPrevious());
    }

    @Test
    @DisplayName("Pagination metadata computation - total=20, size=20")
    void paginationMetadata_total20_size20() {
        // Given
        UUID userId = UUID.randomUUID();
        String userIdString = userId.toString();
        
        // Create 20 consent ledgers
        List<ConsentLedger> ledgers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ConsentLedger ledger = ConsentLedger.builder()
                    .consentId(UUID.randomUUID())
                    .userId(userId)
                    .consentType(ConsentType.PRIVACY_POLICY)
                    .consentVersion("1.0.0")
                    .consentStatus(ConsentStatus.GRANTED)
                    .policyUrl("https://example.com/privacy")
                    .contentHash("hash" + i)
                    .jurisdiction("GB")
                    .region("England")
                    .locale("en-GB")
                    .lawfulBasis(LawfulBasis.CONSENT)
                    .source(ConsentSource.WEB)
                    .ipAddress("192.168.1.1")
                    .userAgent("Mozilla/5.0")
                    .consentTimestamp(LocalDateTime.now().minusDays(i))
                    .retentionExpiresAt(LocalDateTime.now().plusYears(7))
                    .createdAt(LocalDateTime.now().minusDays(i))
                    .build();
            ledgers.add(ledger);
        }

        Page<ConsentLedger> page = new PageImpl<>(ledgers, PageRequest.of(0, 20), 20L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);

        // No child coverage for this test
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userIdString, 0, 20);

        // Then
        assertNotNull(result);
        assertEquals(20, result.entries().size());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(20, result.total());
        assertEquals(1, result.totalPages());
        assertFalse(result.hasNext());
        assertFalse(result.hasPrevious());
    }

    @Test
    @DisplayName("Pagination metadata computation - total=21, size=20, page=0")
    void paginationMetadata_total21_size20_page0() {
        // Given
        UUID userId = UUID.randomUUID();
        String userIdString = userId.toString();
        
        // Create 20 consent ledgers (first page)
        List<ConsentLedger> ledgers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ConsentLedger ledger = ConsentLedger.builder()
                    .consentId(UUID.randomUUID())
                    .userId(userId)
                    .consentType(ConsentType.PRIVACY_POLICY)
                    .consentVersion("1.0.0")
                    .consentStatus(ConsentStatus.GRANTED)
                    .policyUrl("https://example.com/privacy")
                    .contentHash("hash" + i)
                    .jurisdiction("GB")
                    .region("England")
                    .locale("en-GB")
                    .lawfulBasis(LawfulBasis.CONSENT)
                    .source(ConsentSource.WEB)
                    .ipAddress("192.168.1.1")
                    .userAgent("Mozilla/5.0")
                    .consentTimestamp(LocalDateTime.now().minusDays(i))
                    .retentionExpiresAt(LocalDateTime.now().plusYears(7))
                    .createdAt(LocalDateTime.now().minusDays(i))
                    .build();
            ledgers.add(ledger);
        }

        Page<ConsentLedger> page = new PageImpl<>(ledgers, PageRequest.of(0, 20), 21L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);

        // No child coverage for this test
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userIdString, 0, 20);

        // Then
        assertNotNull(result);
        assertEquals(20, result.entries().size());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(21, result.total());
        assertEquals(2, result.totalPages());
        assertTrue(result.hasNext());
        assertFalse(result.hasPrevious());
    }

    @Test
    @DisplayName("Pagination metadata computation - total=21, size=20, page=1")
    void paginationMetadata_total21_size20_page1() {
        // Given
        UUID userId = UUID.randomUUID();
        String userIdString = userId.toString();
        
        // Create 1 consent ledger (second page)
        ConsentLedger ledger = ConsentLedger.builder()
                .consentId(UUID.randomUUID())
                .userId(userId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/privacy")
                .contentHash("hash20")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(20))
                .retentionExpiresAt(LocalDateTime.now().plusYears(7))
                .createdAt(LocalDateTime.now().minusDays(20))
                .build();

        Page<ConsentLedger> page = new PageImpl<>(List.of(ledger), PageRequest.of(1, 20), 21L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(page);

        // No child coverage for this test
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(Collections.emptyList());

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userIdString, 1, 20);

        // Then
        assertNotNull(result);
        assertEquals(1, result.entries().size());
        assertEquals(1, result.page());
        assertEquals(20, result.size());
        assertEquals(21, result.total());
        assertEquals(2, result.totalPages());
        assertFalse(result.hasNext());
        assertTrue(result.hasPrevious());
    }

    @Test
    @DisplayName("Coverage absent for some consents - some consent IDs missing in coverageMap => their coveredKids=[]")
    void coverageAbsent_forSomeConsents_coveredKidsEmpty() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId1 = UUID.randomUUID(); // Has coverage
        UUID consentId2 = UUID.randomUUID(); // Missing from coverage
        UUID consentId3 = UUID.randomUUID(); // Has coverage
        UUID kid1 = UUID.randomUUID();
        UUID kid2 = UUID.randomUUID();
        String userIdString = userId.toString();
        
        // Create 3 consent ledgers
        ConsentLedger ledger1 = ConsentLedger.builder()
                .consentId(consentId1)
                .userId(userId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/privacy")
                .contentHash("hash1")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(1))
                .retentionExpiresAt(LocalDateTime.now().plusYears(7))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        ConsentLedger ledger2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(userId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/terms")
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
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();

        ConsentLedger ledger3 = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(userId)
                .consentType(ConsentType.PARENTAL_CONSENT)
                .consentVersion("3.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/parental")
                .contentHash("hash3")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .consentTimestamp(LocalDateTime.now().minusDays(3))
                .retentionExpiresAt(LocalDateTime.now().plusYears(8))
                .createdAt(LocalDateTime.now().minusDays(3))
                .build();

        List<ConsentLedger> ledgers = List.of(ledger1, ledger2, ledger3);
        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(ledgers, PageRequest.of(0, 20), 3L);
        
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);

        // Create coverage data where consentId2 is missing from coverage
        // Only consentId1 and consentId3 have coverage
        ConsentChildCoverage coverage1 = ConsentChildCoverage.builder()
                .consentId(consentId1)
                .kidId(kid1)
                .build();
        ConsentChildCoverage coverage3 = ConsentChildCoverage.builder()
                .consentId(consentId3)
                .kidId(kid2)
                .build();

        List<ConsentChildCoverage> coverages = List.of(coverage1, coverage3);
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(coverages);

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userIdString, 0, 20);

        // Then
        assertNotNull(result);
        assertEquals(3, result.entries().size());
        
        List<ConsentHistoryResponse.ConsentHistoryEntry> entries = result.entries();
        
        // Verify consentId1 has coverage
        ConsentHistoryResponse.ConsentHistoryEntry entry1 = entries.get(0);
        assertEquals(consentId1.toString(), entry1.consentId());
        assertEquals(1, entry1.coveredKids().size());
        assertEquals(kid1.toString(), entry1.coveredKids().get(0));
        
        // Verify consentId2 has no coverage (missing from coverageMap)
        ConsentHistoryResponse.ConsentHistoryEntry entry2 = entries.get(1);
        assertEquals(consentId2.toString(), entry2.consentId());
        assertTrue(entry2.coveredKids().isEmpty());
        
        // Verify consentId3 has coverage
        ConsentHistoryResponse.ConsentHistoryEntry entry3 = entries.get(2);
        assertEquals(consentId3.toString(), entry3.consentId());
        assertEquals(1, entry3.coveredKids().size());
        assertEquals(kid2.toString(), entry3.coveredKids().get(0));
        
        // Verify the repository was called with the correct consent IDs
        verify(consentChildCoverageRepository).findByConsentIds(List.of(consentId1, consentId2, consentId3));
    }

    // History and query tests will be moved here
} 
