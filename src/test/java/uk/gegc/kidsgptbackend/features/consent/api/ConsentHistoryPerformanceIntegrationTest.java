package uk.gegc.kidsgptbackend.features.consent.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gegc.kidsgptbackend.features.consent.domain.model.*;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentLedgerRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class ConsentHistoryPerformanceIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ConsentLedgerRepository consentLedgerRepository;

    @Autowired
    private ConsentChildCoverageRepository consentChildCoverageRepository;

    private MockMvc mockMvc;
    private UUID testUserId;
    private List<UUID> testKidIds;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        testUserId = UUID.randomUUID();
        testKidIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        
        // Create realistic test data with typical volumes
        createTestData();
    }

    @Test
    @DisplayName("Reasonable latency for large page - size=100 responds within acceptable SLA")
    void reasonableLatencyForLargePage_size100_respondsWithinAcceptableSLA() throws Exception {
        // Define acceptable SLA threshold (adjust based on your project requirements)
        // For a typical web application, 2 seconds is a reasonable threshold for large pages
        long acceptableLatencyMs = 2000; // 2 seconds
        
        long startTime = System.currentTimeMillis();
        
        // Make request with size=100 (large page)
        mockMvc.perform(get("/api/v1/consent/history/{userId}?size=100", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(testUserId.toString())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(testUserId.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.hasNext").isBoolean())
                .andExpect(jsonPath("$.hasPrevious").isBoolean())
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(100)); // Should return exactly 100 entries
        
        long endTime = System.currentTimeMillis();
        long actualLatencyMs = endTime - startTime;
        
        // Assert that the response time is within acceptable SLA
        assertThat(actualLatencyMs)
                .as("Response time for size=100 should be within %d ms SLA", acceptableLatencyMs)
                .isLessThanOrEqualTo(acceptableLatencyMs);
        
        // Additional performance assertions
        assertThat(actualLatencyMs)
                .as("Response time should be reasonable (less than 1 second for typical data)")
                .isLessThan(1000); // Should be much faster than SLA threshold
    }

    @Test
    @DisplayName("Reasonable latency for large page - size=100 with coverage data responds within acceptable SLA")
    void reasonableLatencyForLargePage_size100WithCoverage_respondsWithinAcceptableSLA() throws Exception {
        // Define acceptable SLA threshold
        long acceptableLatencyMs = 2000; // 2 seconds
        
        // Create additional coverage data to make the query more complex
        createCoverageData();
        
        long startTime = System.currentTimeMillis();
        
        // Make request with size=100 (large page)
        mockMvc.perform(get("/api/v1/consent/history/{userId}?size=100", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(testUserId.toString())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(testUserId.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(100));
        
        long endTime = System.currentTimeMillis();
        long actualLatencyMs = endTime - startTime;
        
        // Assert that the response time is within acceptable SLA
        assertThat(actualLatencyMs)
                .as("Response time for size=100 with coverage data should be within %d ms SLA", acceptableLatencyMs)
                .isLessThanOrEqualTo(acceptableLatencyMs);
    }

    @Test
    @DisplayName("Reasonable latency for large page - multiple pages maintain consistent performance")
    void reasonableLatencyForLargePage_multiplePages_maintainConsistentPerformance() throws Exception {
        // Define acceptable SLA threshold
        long acceptableLatencyMs = 2000; // 2 seconds
        
        List<Long> responseTimes = new ArrayList<>();
        
        // Test multiple pages to ensure consistent performance
        for (int page = 0; page < 3; page++) {
            long startTime = System.currentTimeMillis();
            
            mockMvc.perform(get("/api/v1/consent/history/{userId}?page={page}&size=100", testUserId, page)
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(user(testUserId.toString())))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(100));
            
            long endTime = System.currentTimeMillis();
            long actualLatencyMs = endTime - startTime;
            responseTimes.add(actualLatencyMs);
            
            // Each page should be within SLA
            assertThat(actualLatencyMs)
                    .as("Response time for page %d should be within %d ms SLA", page, acceptableLatencyMs)
                    .isLessThanOrEqualTo(acceptableLatencyMs);
        }
        
        // Performance should be consistent across pages (no significant degradation)
        long maxResponseTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long minResponseTime = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long performanceVariation = maxResponseTime - minResponseTime;
        
        assertThat(performanceVariation)
                .as("Performance variation across pages should be reasonable (less than 500ms)")
                .isLessThan(500);
    }

    private void createTestData() {
        LocalDateTime baseTime = LocalDateTime.now();
        List<ConsentLedger> consentLedgers = new ArrayList<>();
        
        // Create 150 consent records (more than the page size of 100)
        for (int i = 0; i < 150; i++) {
            ConsentLedger consentLedger = ConsentLedger.builder().consentId(UUID.randomUUID()).userId(testUserId)                    .consentType(ConsentType.values()[i % ConsentType.values().length])
                    .consentVersion("1." + (i % 5) + ".0")
                    .consentStatus(ConsentStatus.GRANTED)
                    .policyUrl("https://example.com/policy" + i)
                    .contentHash("hash" + i)
                    .jurisdiction("GB")
                    .region("England")
                    .locale("en-GB")
                    .lawfulBasis(LawfulBasis.CONSENT)
                    .source(ConsentSource.WEB)
                    .ipAddress("192.168.1." + (i % 255))
                    .userAgent("Mozilla/5.0 (Test Browser " + i + ")")
                    .consentTimestamp(baseTime.minusDays(i))
                    .retentionExpiresAt(baseTime.plusYears(7))
                    .receiptJson("{\"test\": \"data" + i + "\"}")
                    .recordSignature(("signature" + i).getBytes())
                    .build();
            
            consentLedgers.add(consentLedger);
        }
        
        consentLedgerRepository.saveAll(consentLedgers);
    }

    private void createCoverageData() {
        List<ConsentChildCoverage> coverages = new ArrayList<>();
        
        // Get all consent IDs for this user
        List<UUID> consentIds = consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(
                testUserId, org.springframework.data.domain.PageRequest.of(0, 150))
                .getContent()
                .stream()
                .map(ConsentLedger::getConsentId)
                .toList();
        
        // Create coverage data for some consents (not all to make it realistic)
        for (int i = 0; i < consentIds.size(); i += 3) { // Every 3rd consent has coverage
            UUID consentId = consentIds.get(i);
            
            // Add 1-3 kids per consent
            int numKids = (i % 3) + 1;
            for (int j = 0; j < numKids; j++) {
                ConsentChildCoverage coverage = ConsentChildCoverage.builder()
                        .consentId(consentId)
                        .kidId(testKidIds.get(j % testKidIds.size()))
                        .build();
                coverages.add(coverage);
            }
        }
        
        consentChildCoverageRepository.saveAll(coverages);
    }
} 