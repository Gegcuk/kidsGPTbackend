package uk.gegc.kidsgptbackend.controller;

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
import uk.gegc.kidsgptbackend.model.consent.*;
import uk.gegc.kidsgptbackend.repository.consent.ConsentLedgerRepository;

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
class ConsentHistoryNullFieldsIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ConsentLedgerRepository consentLedgerRepository;

    private MockMvc mockMvc;
    private UUID testUserId;
    private LocalDateTime baseTimestamp;
    private UUID consentWithNullFieldsId;
    private UUID consentWithAllFieldsId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        testUserId = UUID.randomUUID();
        baseTimestamp = LocalDateTime.now().minusDays(1);
        
        // Create test data with null optional fields
        createTestDataWithNullOptionalFields();
    }

    @Test
    @DisplayName("Null optional fields - rows without parentVerificationId serialize with nulls")
    void nullOptionalFields_rowsWithoutParentVerificationId_serializeWithNulls() throws Exception {
        // Get consent history
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=10", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(testUserId.toString())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(testUserId.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(2)) // Should have 2 entries
                .andExpect(jsonPath("$.entries[0].parentVerificationId").exists()) // First entry should have parentVerificationId
                .andExpect(jsonPath("$.entries[1].parentVerificationId").isEmpty()); // Second entry should have null parentVerificationId
    }

    @Test
    @DisplayName("Null optional fields - rows without locale/region serialize with nulls")
    void nullOptionalFields_rowsWithoutLocaleRegion_serializeWithNulls() throws Exception {
        // Get consent history
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=10", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(testUserId.toString())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.entries[0].locale").value("en-GB")) // First entry should have locale
                .andExpect(jsonPath("$.entries[0].region").value("England")) // First entry should have region
                .andExpect(jsonPath("$.entries[1].locale").isEmpty()) // Second entry should have null locale
                .andExpect(jsonPath("$.entries[1].region").isEmpty()); // Second entry should have null region
    }

    @Test
    @DisplayName("Null optional fields - service doesn't throw exceptions")
    void nullOptionalFields_serviceDoesntThrowExceptions() throws Exception {
        // Get consent history multiple times to ensure no exceptions are thrown
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=10", testUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(user(testUserId.toString())))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.entries").isArray())
                    .andExpect(jsonPath("$.entries.length()").value(2));
        }
    }

    @Test
    @DisplayName("Null optional fields - all required fields are present")
    void nullOptionalFields_allRequiredFieldsArePresent() throws Exception {
        // Get consent history
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=10", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(testUserId.toString())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.entries[0].consentId").exists()) // Required field
                .andExpect(jsonPath("$.entries[0].consentType").exists()) // Required field
                .andExpect(jsonPath("$.entries[0].consentStatus").exists()) // Required field
                .andExpect(jsonPath("$.entries[0].consentTimestamp").exists()) // Required field
                .andExpect(jsonPath("$.entries[0].createdAt").exists()) // Required field
                .andExpect(jsonPath("$.entries[1].consentId").exists()) // Required field
                .andExpect(jsonPath("$.entries[1].consentType").exists()) // Required field
                .andExpect(jsonPath("$.entries[1].consentStatus").exists()) // Required field
                .andExpect(jsonPath("$.entries[1].consentTimestamp").exists()) // Required field
                .andExpect(jsonPath("$.entries[1].createdAt").exists()); // Required field
    }

    @Test
    @DisplayName("Null optional fields - JSON structure is valid")
    void nullOptionalFields_jsonStructureIsValid() throws Exception {
        // Get consent history
        String response = mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=10", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(testUserId.toString())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verify that the response contains the expected structure
        verifyJsonStructure(response);
    }

    private void createTestDataWithNullOptionalFields() {
        List<ConsentLedger> consentLedgers = new ArrayList<>();
        
        // Create consent with null optional fields (no parentVerificationId, locale, region)
        consentWithNullFieldsId = UUID.randomUUID();
        ConsentLedger consentWithNullFields = ConsentLedger.builder()
                .consentId(consentWithNullFieldsId)
                .userId(testUserId)
                .consentType(ConsentType.PRIVACY_POLICY)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/policy")
                .contentHash("null-fields-hash")
                .jurisdiction("GB")
                .region(null) // Null optional field
                .locale(null) // Null optional field
                .parentVerificationId(null) // Null optional field
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0 (Test Browser)")
                .consentTimestamp(baseTimestamp.minusHours(2))
                .retentionExpiresAt(baseTimestamp.plusYears(7))
                .receiptJson("{\"nullFields\": \"data\"}")
                .recordSignature("null-fields-signature".getBytes())
                .build();
        
        // Create consent with all fields populated
        consentWithAllFieldsId = UUID.randomUUID();
        ConsentLedger consentWithAllFields = ConsentLedger.builder()
                .consentId(consentWithAllFieldsId)
                .userId(testUserId)
                .consentType(ConsentType.TERMS_OF_SERVICE)
                .consentVersion("1.0.0")
                .consentStatus(ConsentStatus.GRANTED)
                .policyUrl("https://example.com/terms")
                .contentHash("all-fields-hash")
                .jurisdiction("GB")
                .region("England") // Populated optional field
                .locale("en-GB") // Populated optional field
                .parentVerificationId(UUID.randomUUID()) // Populated optional field
                .lawfulBasis(LawfulBasis.CONSENT)
                .source(ConsentSource.WEB)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0 (Test Browser)")
                .consentTimestamp(baseTimestamp.minusHours(1))
                .retentionExpiresAt(baseTimestamp.plusYears(7))
                .receiptJson("{\"allFields\": \"data\"}")
                .recordSignature("all-fields-signature".getBytes())
                .build();
        
        consentLedgers.add(consentWithNullFields);
        consentLedgers.add(consentWithAllFields);
        
        consentLedgerRepository.saveAll(consentLedgers);
    }

    private void verifyJsonStructure(String response) {
        // Verify that the response contains the expected JSON structure
        assertThat(response).contains("userId");
        assertThat(response).contains("page");
        assertThat(response).contains("size");
        assertThat(response).contains("total");
        assertThat(response).contains("totalPages");
        assertThat(response).contains("hasNext");
        assertThat(response).contains("hasPrevious");
        assertThat(response).contains("entries");
        
        // Verify that entries contain the expected fields
        assertThat(response).contains("consentId");
        assertThat(response).contains("consentType");
        assertThat(response).contains("consentStatus");
        assertThat(response).contains("consentTimestamp");
        assertThat(response).contains("createdAt");
        
        // Verify that the response is valid JSON and contains the test user ID
        assertThat(response).contains(testUserId.toString());
        
        // Verify that null values are properly serialized (this is expected behavior)
        assertThat(response).contains("null");
    }
} 