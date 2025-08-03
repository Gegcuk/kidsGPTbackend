package uk.gegc.kidsgptbackend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gegc.kidsgptbackend.dto.consent.ConsentHistoryResponse;
import uk.gegc.kidsgptbackend.model.consent.*;
import uk.gegc.kidsgptbackend.repository.consent.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentPoliciesRepository;
import uk.gegc.kidsgptbackend.repository.consent.ParentVerificationRepository;
import uk.gegc.kidsgptbackend.service.consent.impl.ConsentServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentHistoryResponseSerializationTest {

    private ObjectMapper objectMapper;
    
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
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Use constructor injection as the service uses @RequiredArgsConstructor
        consentService = new ConsentServiceImpl(
            consentLedgerRepository,
            consentChildCoverageRepository,
            parentVerificationRepository,
            consentPoliciesRepository,
            java.time.Clock.systemUTC()
        );
    }

    @Test
    @DisplayName("JSON serialization shape - PaginatedConsentHistoryResponse with one entry")
    void jsonSerializationShape_paginatedConsentHistoryResponseWithOneEntry() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        UUID parentVerificationId = UUID.randomUUID();
        UUID withdrawnConsentId = UUID.randomUUID();
        LocalDateTime consentTimestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        LocalDateTime retentionExpiresAt = LocalDateTime.of(2031, 1, 15, 10, 30, 0);
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 29, 0);

        ConsentHistoryResponse.ConsentHistoryEntry entry = new ConsentHistoryResponse.ConsentHistoryEntry(
                consentId.toString(),
                ConsentType.PRIVACY_POLICY,
                "2.0.0",
                ConsentStatus.GRANTED,
                "https://example.com/privacy-v2",
                "def456hash",
                "GB",
                "England",
                "en-GB",
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                "192.168.1.100",
                "Mozilla/5.0 (Test Browser)",
                consentTimestamp,
                parentVerificationId.toString(),
                retentionExpiresAt,
                createdAt,
                List.of("kid1", "kid2"),
                withdrawnConsentId.toString()
        );

        ConsentHistoryResponse.PaginatedConsentHistoryResponse response = new ConsentHistoryResponse.PaginatedConsentHistoryResponse(
                userId.toString(),
                List.of(entry),
                0,
                20,
                1L,
                1,
                false,
                false
        );

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertThat(json).isNotNull();
        
        // Verify the JSON structure and property names
        assertThat(json).contains("\"userId\":\"" + userId + "\"");
        assertThat(json).contains("\"entries\":[");
        assertThat(json).contains("\"page\":0");
        assertThat(json).contains("\"size\":20");
        assertThat(json).contains("\"total\":1");
        assertThat(json).contains("\"totalPages\":1");
        assertThat(json).contains("\"hasNext\":false");
        assertThat(json).contains("\"hasPrevious\":false");

        // Verify entry properties
        assertThat(json).contains("\"consentId\":\"" + consentId + "\"");
        assertThat(json).contains("\"consentType\":\"PRIVACY_POLICY\"");
        assertThat(json).contains("\"consentVersion\":\"2.0.0\"");
        assertThat(json).contains("\"consentStatus\":\"GRANTED\"");
        assertThat(json).contains("\"policyUrl\":\"https://example.com/privacy-v2\"");
        assertThat(json).contains("\"contentHash\":\"def456hash\"");
        assertThat(json).contains("\"jurisdiction\":\"GB\"");
        assertThat(json).contains("\"region\":\"England\"");
        assertThat(json).contains("\"locale\":\"en-GB\"");
        assertThat(json).contains("\"lawfulBasis\":\"CONSENT\"");
        assertThat(json).contains("\"source\":\"WEB\"");
        assertThat(json).contains("\"ipAddress\":\"192.168.1.100\"");
        assertThat(json).contains("\"userAgent\":\"Mozilla/5.0 (Test Browser)\"");
        assertThat(json).contains("\"parentVerificationId\":\"" + parentVerificationId + "\"");
        assertThat(json).contains("\"withdrawnConsentId\":\"" + withdrawnConsentId + "\"");
        assertThat(json).contains("\"coveredKids\":[\"kid1\",\"kid2\"]");

        // Verify timestamp format (ISO format)
        assertThat(json).contains("\"consentTimestamp\":\"2024-01-15T10:30:00\"");
        assertThat(json).contains("\"retentionExpiresAt\":\"2031-01-15T10:30:00\"");
        assertThat(json).contains("\"createdAt\":\"2024-01-15T10:29:00\"");

        // Verify enum values are serialized as strings (not ordinals)
        assertThat(json).doesNotContain("\"consentType\":0");
        assertThat(json).doesNotContain("\"consentStatus\":0");
        assertThat(json).doesNotContain("\"lawfulBasis\":0");
        assertThat(json).doesNotContain("\"source\":0");
    }

    @Test
    @DisplayName("JSON serialization shape - PaginatedConsentHistoryResponse with null optional fields")
    void jsonSerializationShape_paginatedConsentHistoryResponseWithNullOptionalFields() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        LocalDateTime consentTimestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        LocalDateTime retentionExpiresAt = LocalDateTime.of(2031, 1, 15, 10, 30, 0);
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 29, 0);

        ConsentHistoryResponse.ConsentHistoryEntry entry = new ConsentHistoryResponse.ConsentHistoryEntry(
                consentId.toString(),
                ConsentType.PRIVACY_POLICY,
                "2.0.0",
                ConsentStatus.GRANTED,
                "https://example.com/privacy-v2",
                "def456hash",
                "GB",
                null, // null region
                null, // null locale
                LawfulBasis.CONSENT,
                ConsentSource.WEB,
                "192.168.1.100",
                "Mozilla/5.0 (Test Browser)",
                consentTimestamp,
                null, // null parentVerificationId
                retentionExpiresAt,
                createdAt,
                List.of(), // empty coveredKids
                null // null withdrawnConsentId
        );

        ConsentHistoryResponse.PaginatedConsentHistoryResponse response = new ConsentHistoryResponse.PaginatedConsentHistoryResponse(
                userId.toString(),
                List.of(entry),
                0,
                20,
                1L,
                1,
                false,
                false
        );

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertThat(json).isNotNull();
        
        // Verify null fields are included in JSON
        assertThat(json).contains("\"region\":null");
        assertThat(json).contains("\"locale\":null");
        assertThat(json).contains("\"parentVerificationId\":null");
        assertThat(json).contains("\"withdrawnConsentId\":null");
        assertThat(json).contains("\"coveredKids\":[]");

        // Verify non-null fields are present
        assertThat(json).contains("\"consentId\":\"" + consentId + "\"");
        assertThat(json).contains("\"consentType\":\"PRIVACY_POLICY\"");
        assertThat(json).contains("\"consentStatus\":\"GRANTED\"");
        assertThat(json).contains("\"lawfulBasis\":\"CONSENT\"");
        assertThat(json).contains("\"source\":\"WEB\"");
    }

    @Test
    @DisplayName("Deterministic coveredKids representation - Given unsorted input, serialized JSON shows sorted order")
    void deterministicCoveredKidsRepresentation_unsortedInput_showsSortedOrder() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        UUID kid1 = UUID.randomUUID();
        UUID kid2 = UUID.randomUUID();
        UUID kid3 = UUID.randomUUID();
        UUID kid4 = UUID.randomUUID();
        LocalDateTime consentTimestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        LocalDateTime retentionExpiresAt = LocalDateTime.of(2031, 1, 15, 10, 30, 0);
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 29, 0);

        // Create consent ledger
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
                .consentTimestamp(consentTimestamp)
                .retentionExpiresAt(retentionExpiresAt)
                .createdAt(createdAt)
                .build();

        // Create coverage data with unsorted kid IDs
        // Input order: kid3, kid1, kid2, kid4 (deliberately unsorted)
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
                .kidId(kid4) // kid4 fourth
                .build();

        List<ConsentChildCoverage> coverages = List.of(coverage1, coverage2, coverage3, coverage4);

        // Mock repository responses
        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(List.of(consentLedger), PageRequest.of(0, 20), 1L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(coverages);

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse response = 
                consentService.getConsentHistory(userId.toString(), 0, 20);
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertThat(json).isNotNull();
        
        // Get the expected sorted order (alphabetically by UUID string)
        List<String> expectedSortedKids = List.of(
                kid1.toString(),
                kid2.toString(),
                kid3.toString(),
                kid4.toString()
        ).stream().sorted().collect(java.util.stream.Collectors.toList());
        
        // Verify that the coveredKids array in JSON is sorted
        String expectedCoveredKidsJson = "\"coveredKids\":[" + 
            expectedSortedKids.stream()
                .map(kid -> "\"" + kid + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + 
            "]";
        assertThat(json).contains(expectedCoveredKidsJson);
        
        // Verify the entry's coveredKids list is actually sorted
        ConsentHistoryResponse.ConsentHistoryEntry entry = response.entries().get(0);
        assertThat(entry.coveredKids()).isEqualTo(expectedSortedKids);
        
        // Verify the list is deterministically sorted (same result on multiple serializations)
        String json2 = objectMapper.writeValueAsString(response);
        assertThat(json2).isEqualTo(json);
    }

    @Test
    @DisplayName("Deterministic coveredKids representation - With duplicates, serialized JSON shows unique sorted order")
    void deterministicCoveredKidsRepresentation_withDuplicates_showsUniqueSortedOrder() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        UUID kid1 = UUID.randomUUID();
        UUID kid2 = UUID.randomUUID();
        UUID kid3 = UUID.randomUUID();
        UUID kid4 = UUID.randomUUID();
        LocalDateTime consentTimestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        LocalDateTime retentionExpiresAt = LocalDateTime.of(2031, 1, 15, 10, 30, 0);
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 29, 0);

        // Create consent ledger
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
                .consentTimestamp(consentTimestamp)
                .retentionExpiresAt(retentionExpiresAt)
                .createdAt(createdAt)
                .build();

        // Create coverage data with duplicates and unsorted order
        // Input: kid3, kid1, kid2, kid1 (duplicate), kid4, kid2 (duplicate)
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

        // Mock repository responses
        Page<ConsentLedger> consentLedgerPage = new PageImpl<>(List.of(consentLedger), PageRequest.of(0, 20), 1L);
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(consentLedgerPage);
        when(consentChildCoverageRepository.findByConsentIds(anyList()))
                .thenReturn(coverages);

        // When
        ConsentHistoryResponse.PaginatedConsentHistoryResponse response = 
                consentService.getConsentHistory(userId.toString(), 0, 20);
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertThat(json).isNotNull();
        
        // Get the expected unique sorted order
        List<String> expectedUniqueSortedKids = List.of(
                kid1.toString(),
                kid2.toString(),
                kid3.toString(),
                kid4.toString()
        ).stream().sorted().collect(java.util.stream.Collectors.toList());
        
        // Verify that the coveredKids array in JSON is unique and sorted
        String expectedCoveredKidsJson = "\"coveredKids\":[" + 
            expectedUniqueSortedKids.stream()
                .map(kid -> "\"" + kid + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + 
            "]";
        assertThat(json).contains(expectedCoveredKidsJson);
        
        // Verify the entry's coveredKids list is actually unique and sorted
        ConsentHistoryResponse.ConsentHistoryEntry entry = response.entries().get(0);
        assertThat(entry.coveredKids()).isEqualTo(expectedUniqueSortedKids);
        
        // Verify no duplicates exist in the final list
        assertThat(entry.coveredKids().size()).isEqualTo(entry.coveredKids().stream().distinct().count());
        
        // Verify the list is deterministically sorted (same result on multiple serializations)
        String json2 = objectMapper.writeValueAsString(response);
        assertThat(json2).isEqualTo(json);
    }
} 