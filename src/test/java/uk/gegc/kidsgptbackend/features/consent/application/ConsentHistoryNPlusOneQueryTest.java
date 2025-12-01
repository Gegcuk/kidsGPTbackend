package uk.gegc.kidsgptbackend.features.consent.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentHistoryResponse;
import uk.gegc.kidsgptbackend.features.consent.domain.model.*;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentPoliciesRepository;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ParentVerificationRepository;
import uk.gegc.kidsgptbackend.features.consent.application.impl.ConsentServiceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentHistoryNPlusOneQueryTest {

    @Mock
    private ConsentLedgerRepository consentLedgerRepository;

    @Mock
    private ConsentChildCoverageRepository consentChildCoverageRepository;

    @Mock
    private ParentVerificationRepository parentVerificationRepository;
    
    @Mock
    private ConsentPoliciesRepository consentPoliciesRepository;

    private ConsentServiceImpl consentService;

    @BeforeEach
    void setUp() {
        consentService = new ConsentServiceImpl(
            consentLedgerRepository,
            consentChildCoverageRepository,
            parentVerificationRepository,
            consentPoliciesRepository,
            java.time.Clock.systemUTC()
        );
    }

    @Test
    @DisplayName("Coverage retrieved in a single query - N entries result in 1 coverage query, not N queries")
    void coverageRetrievedInSingleQuery_nEntries_resultInOneCoverageQuery() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId1 = UUID.randomUUID();
        UUID consentId2 = UUID.randomUUID();
        UUID consentId3 = UUID.randomUUID();
        UUID kid1 = UUID.randomUUID();
        UUID kid2 = UUID.randomUUID();
        UUID kid3 = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Create multiple consent ledgers (N entries)
        // Note: Service returns entries in descending order by consentTimestamp, then by createdAt
        // consentId3: most recent consentTimestamp (now-1), createdAt (now-2)
        // consentId2: middle consentTimestamp (now-2), createdAt (now-3)  
        // consentId1: oldest consentTimestamp (now-3), createdAt (now-4)
        ConsentLedger consentLedger1 = ConsentLedger.builder()
                .consentId(consentId1)
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
                .consentTimestamp(now.minusDays(3))
                .retentionExpiresAt(now.plusYears(7))
                .createdAt(now.minusDays(4))
                .build();

        ConsentLedger consentLedger2 = ConsentLedger.builder()
                .consentId(consentId2)
                .userId(userId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("2.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/privacy")
                .contentHash("privacy456hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0 (Test Browser)")
                .consentTimestamp(now.minusDays(2))
                .retentionExpiresAt(now.plusYears(7))
                .createdAt(now.minusDays(3))
                .build();

        ConsentLedger consentLedger3 = ConsentLedger.builder()
                .consentId(consentId3)
                .userId(userId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.5.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/terms")
                .contentHash("terms789hash")
                .jurisdiction("GB")
                .region("England")
                .locale("en-GB")
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0 (Test Browser)")
                .consentTimestamp(now.minusDays(1))
                .retentionExpiresAt(now.plusYears(7))
                .createdAt(now.minusDays(2))
                .build();

        // Create the list in the order that the service will return them (most recent first)
        List<ConsentLedger> consentLedgers = List.of(consentLedger3, consentLedger2, consentLedger1);

        // Create coverage data for all consents
        ConsentChildCoverage coverage1 = ConsentChildCoverage.builder()
                .consentId(consentId1)
                .kidId(kid1)
                .build();
        ConsentChildCoverage coverage2 = ConsentChildCoverage.builder()
                .consentId(consentId1)
                .kidId(kid2)
                .build();
        ConsentChildCoverage coverage3 = ConsentChildCoverage.builder()
                .consentId(consentId2)
                .kidId(kid3)
                .build();
        // consentId3 has no coverage (empty list)

        List<ConsentChildCoverage> allCoverages = List.of(coverage1, coverage2, coverage3);

        // Mock repository responses
        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(consentLedgers, PageRequest.of(0, 20), 3L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(allCoverages);

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userId.toString(), 0, 20);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.entries()).hasSize(3);

        // Verify that findByConsentIds was called exactly once with all consent IDs
        ArgumentCaptor<List<UUID>> consentIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(consentChildCoverageRepository, times(1)).findByConsentIds(consentIdsCaptor.capture());

        List<UUID> capturedConsentIds = consentIdsCaptor.getValue();
        assertThat(capturedConsentIds).containsExactlyInAnyOrder(consentId1, consentId2, consentId3);
        assertThat(capturedConsentIds).hasSize(3);

        // Verify that the service didn't make individual queries for each consent
        // (this would be N queries instead of 1)
        verify(consentChildCoverageRepository, never()).findByConsentId(any(UUID.class));

        // Verify the results are correct (entries are returned in descending order by consentTimestamp)
        ConsentHistoryResponse.ConsentHistoryEntry entry1 = result.entries().get(0); // Most recent first (consentId3)
        assertThat(entry1.consentId()).isEqualTo(consentId3.toString());
        assertThat(entry1.coveredKids()).isEmpty(); // consentId3 has no coverage

        ConsentHistoryResponse.ConsentHistoryEntry entry2 = result.entries().get(1); // Second most recent (consentId2)
        assertThat(entry2.consentId()).isEqualTo(consentId2.toString());
        assertThat(entry2.coveredKids()).containsExactly(kid3.toString());

        ConsentHistoryResponse.ConsentHistoryEntry entry3 = result.entries().get(2); // Oldest (consentId1)
        assertThat(entry3.consentId()).isEqualTo(consentId1.toString());
        assertThat(entry3.coveredKids()).containsExactlyInAnyOrder(kid1.toString(), kid2.toString());
    }

    @Test
    @DisplayName("Coverage retrieved in a single query - Empty consent list results in no coverage query")
    void coverageRetrievedInSingleQuery_emptyConsentList_resultsInNoCoverageQuery() {
        // Given
        UUID userId = UUID.randomUUID();

        // Mock empty consent ledger page
        Page<ConsentLedger> emptyConsentLedgerPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(emptyConsentLedgerPage);

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userId.toString(), 0, 20);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.entries()).isEmpty();

        // Verify that findByConsentIds was never called (since there are no consent IDs)
        verify(consentChildCoverageRepository, never()).findByConsentIds(anyList());
    }

    @Test
    @DisplayName("Coverage retrieved in a single query - Single consent with multiple kids results in one coverage query")
    void coverageRetrievedInSingleQuery_singleConsentWithMultipleKids_resultsInOneCoverageQuery() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        UUID kid1 = UUID.randomUUID();
        UUID kid2 = UUID.randomUUID();
        UUID kid3 = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Create single consent ledger
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
                .consentTimestamp(now.minusDays(1))
                .retentionExpiresAt(now.plusYears(7))
                .createdAt(now.minusDays(2))
                .build();

        // Create multiple coverage entries for the same consent
        ConsentChildCoverage coverage1 = ConsentChildCoverage.builder()
                .consentId(consentId)
                .kidId(kid1)
                .build();
        ConsentChildCoverage coverage2 = ConsentChildCoverage.builder()
                .consentId(consentId)
                .kidId(kid2)
                .build();
        ConsentChildCoverage coverage3 = ConsentChildCoverage.builder()
                .consentId(consentId)
                .kidId(kid3)
                .build();

        List<ConsentChildCoverage> coverages = List.of(coverage1, coverage2, coverage3);

        // Mock repository responses
        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(List.of(consentLedger), PageRequest.of(0, 20), 1L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(coverages);

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userId.toString(), 0, 20);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.entries()).hasSize(1);

        // Verify that findByConsentIds was called exactly once
        verify(consentChildCoverageRepository, times(1)).findByConsentIds(anyList());

        // Verify the result contains all kids
        ConsentHistoryResponse.ConsentHistoryEntry entry = result.entries().get(0);
        assertThat(entry.consentId()).isEqualTo(consentId.toString());
        assertThat(entry.coveredKids()).containsExactlyInAnyOrder(
                kid1.toString(), 
                kid2.toString(), 
                kid3.toString()
        );
    }

    @Test
    @DisplayName("Coverage retrieved in a single query - Large page size still results in single coverage query")
    void coverageRetrievedInSingleQuery_largePageSize_stillResultsInSingleCoverageQuery() {
        // Given
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Create many consent ledgers (simulating large page)
        List<ConsentLedger> consentLedgers = new ArrayList<>();
        List<UUID> consentIds = new ArrayList<>();
        List<ConsentChildCoverage> allCoverages = new ArrayList<>();

        // Create 50 consent ledgers (large page)
        for (int i = 0; i < 50; i++) {
            UUID consentId = UUID.randomUUID();
            UUID kidId = UUID.randomUUID();
            
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
                    .consentTimestamp(now.minusDays(i))
                    .retentionExpiresAt(now.plusYears(7))
                    .createdAt(now.minusDays(i + 1))
                    .build();

            consentLedgers.add(consentLedger);
            consentIds.add(consentId);

            // Create coverage for each consent
            ConsentChildCoverage coverage = ConsentChildCoverage.builder()
                    .consentId(consentId)
                    .kidId(kidId)
                    .build();
            allCoverages.add(coverage);
        }

        // Mock repository responses
        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(consentLedgers, PageRequest.of(0, 100), 50L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(allCoverages);

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse result = 
                consentService.getConsentHistory(userId.toString(), 0, 100);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.entries()).hasSize(50);

        // Verify that findByConsentIds was called exactly once with all 50 consent IDs
        ArgumentCaptor<List<UUID>> consentIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(consentChildCoverageRepository, times(1)).findByConsentIds(consentIdsCaptor.capture());

        List<UUID> capturedConsentIds = consentIdsCaptor.getValue();
        assertThat(capturedConsentIds).hasSize(50);
        assertThat(capturedConsentIds).containsExactlyInAnyOrderElementsOf(consentIds);

        // Verify that no individual queries were made
        verify(consentChildCoverageRepository, never()).findByConsentId(any(UUID.class));
    }
} 