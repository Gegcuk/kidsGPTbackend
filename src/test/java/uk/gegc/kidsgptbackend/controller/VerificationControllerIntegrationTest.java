package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.consent.VerificationInitiateRequest;
import uk.gegc.kidsgptbackend.model.consent.VerificationMethod;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class VerificationControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    private User testParent;
    private UUID parentId;

    @BeforeEach
    void setUp() {
        // Create parent role if it doesn't exist
        roleRepository.findByRole("ROLE_PARENT").orElseGet(() -> {
            uk.gegc.kidsgptbackend.model.user.Role role = new uk.gegc.kidsgptbackend.model.user.Role();
            role.setRole("ROLE_PARENT");
            return roleRepository.save(role);
        });

        // Create a test parent user
        testParent = new User();
        testParent.setUsername("testparent");
        testParent.setEmail("testparent@example.com");
        testParent.setHashedPassword("hashedpassword");
        testParent.setActive(true);
        testParent.setRoles(java.util.Set.of(roleRepository.findByRole("ROLE_PARENT").get()));
        testParent = userRepository.save(testParent);
        parentId = testParent.getId();
    }

    @Test
    @DisplayName("EMAIL valid → passes: lowercase/uppercase address accepted; normalization to lowercase before hashing")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_emailValid_passes() throws Exception {
        // Test with lowercase email
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "test@example.com"
        );

        String response = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(jsonPath("$.verificationId").exists())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("\"verificationId\"");
        assertThat(response).contains("\"verificationStatus\":\"PENDING\"");

        // Test with uppercase email (should also pass)
        VerificationInitiateRequest requestUpper = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "TEST@EXAMPLE.COM"
        );

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestUpper)))
                .andExpect(status().isOk()) // Should return 200 for reuse of existing verification
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"));
    }

    @Test
    @DisplayName("SMS valid (E.164) → passes (even if SMS dispatch not implemented)")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_smsValidE164_passes() throws Exception {
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "+15551234567"
        );

        String response = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(jsonPath("$.verificationId").exists())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("\"verificationId\"");
        assertThat(response).contains("\"verificationStatus\":\"PENDING\"");
    }

    @Test
    @DisplayName("EMAIL with mixed case and whitespace → normalization works correctly")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_emailWithWhitespaceAndMixedCase_normalizationWorks() throws Exception {
        // Test with mixed case and whitespace
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "  Test.User@Example.COM  "
        );

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()));

        // Test with same email but different case/whitespace (should reuse existing)
        VerificationInitiateRequest request2 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "test.user@example.com"
        );

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk()) // Should return 200 for reuse
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    @DisplayName("SMS with E.164 format and whitespace → trimming works correctly")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_smsWithWhitespace_trimmingWorks() throws Exception {
        // Test with whitespace around phone number
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "  +15551234567  "
        );

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()));

        // Test with same phone but trimmed (should reuse existing)
        VerificationInitiateRequest request2 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "+15551234567"
        );

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk()) // Should return 200 for reuse
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()));
    }

    // Section 1.2: Invalid combinations tests
    @Test
    @DisplayName("Missing parentId → 400 with field error")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_missingParentId_returns400() throws Exception {
        String json = """
                {
                    "verificationMethod": "EMAIL",
                    "contactInfo": "test@example.com"
                }
                """;

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.hasItem("parentId: Parent ID is required")));
    }

    @Test
    @DisplayName("Missing verificationMethod → 400 with field error")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_missingVerificationMethod_returns400() throws Exception {
        String json = """
                {
                    "parentId": "%s",
                    "contactInfo": "test@example.com"
                }
                """.formatted(parentId);

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.hasItem("verificationMethod: Verification method is required")));
    }

    @Test
    @DisplayName("Missing contactInfo → 400 with field error")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_missingContactInfo_returns400() throws Exception {
        String json = """
                {
                    "parentId": "%s",
                    "verificationMethod": "EMAIL"
                }
                """.formatted(parentId);

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.hasItem("contactInfo: Contact information is required")));
    }

    @Test
    @DisplayName("EMAIL with invalid address → 400 (custom message)")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_emailWithInvalidAddress_returns400() throws Exception {
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "invalid-email"
        );

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.hasItem("contactInfo: contactInfo must be a valid email address when verificationMethod=EMAIL")));
    }

    @Test
    @DisplayName("SMS with non‑E.164 → 400 (custom message)")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_smsWithNonE164_returns400() throws Exception {
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "1234567890" // Missing + prefix
        );

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.hasItem("contactInfo: contactInfo must be an E.164 phone (e.g. +15551234567) when verificationMethod=SMS")));
    }

    @Test
    @DisplayName("Unsupported method value → 400 with message 'Unsupported verification method'")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_unsupportedMethod_returns400() throws Exception {
        String json = """
                {
                    "parentId": "%s",
                    "verificationMethod": "PHONE_CALL",
                    "contactInfo": "test@example.com"
                }
                """.formatted(parentId);

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.hasItem("verificationMethod: Unsupported verification method")));
    }

    @Test
    @DisplayName("Empty JSON body → 400 Bad Request")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_emptyJsonBody_returns400() throws Exception {
        String json = "{}";

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("Malformed JSON → 400 Bad Request")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_malformedJson_returns400() throws Exception {
        String json = "{ invalid json }";

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed JSON"));
    }

    // Section 1.3: Normalization properties tests
    @Test
    @DisplayName("EMAIL case-insensitivity: John.Doe@Example.com and john.doe@example.com produce identical contact hash")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_emailCaseInsensitivity_producesIdenticalHash() throws Exception {
        // First request with mixed case
        VerificationInitiateRequest request1 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "John.Doe@Example.com"
        );

        String response1 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        // Extract verification ID from first response
        String verificationId1 = objectMapper.readTree(response1).get("verificationId").asText();

        // Second request with different case (should reuse existing verification)
        VerificationInitiateRequest request2 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "john.doe@example.com"
        );

        String response2 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk()) // Should return 200 for reuse
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        // Extract verification ID from second response
        String verificationId2 = objectMapper.readTree(response2).get("verificationId").asText();

        // Both should return the same verification ID, indicating identical contact hash
        assertThat(verificationId1).isEqualTo(verificationId2);
    }

    @Test
    @DisplayName("Whitespace trimming: leading/trailing spaces are ignored before hashing")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_whitespaceTrimming_ignoredBeforeHashing() throws Exception {
        // First request with whitespace
        VerificationInitiateRequest request1 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "  test@example.com  "
        );

        String response1 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        // Extract verification ID from first response
        String verificationId1 = objectMapper.readTree(response1).get("verificationId").asText();

        // Second request without whitespace (should reuse existing verification)
        VerificationInitiateRequest request2 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "test@example.com"
        );

        String response2 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk()) // Should return 200 for reuse
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        // Extract verification ID from second response
        String verificationId2 = objectMapper.readTree(response2).get("verificationId").asText();

        // Both should return the same verification ID, indicating identical contact hash
        assertThat(verificationId1).isEqualTo(verificationId2);
    }

    @Test
    @DisplayName("Phone already E.164: trimmed string hashed identically")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_phoneE164Trimming_hashedIdentically() throws Exception {
        // First request with whitespace around E.164 phone
        VerificationInitiateRequest request1 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "  +15551234567  "
        );

        String response1 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        // Extract verification ID from first response
        String verificationId1 = objectMapper.readTree(response1).get("verificationId").asText();

        // Second request with trimmed E.164 phone (should reuse existing verification)
        VerificationInitiateRequest request2 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "+15551234567"
        );

        String response2 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk()) // Should return 200 for reuse
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        // Extract verification ID from second response
        String verificationId2 = objectMapper.readTree(response2).get("verificationId").asText();

        // Both should return the same verification ID, indicating identical contact hash
        assertThat(verificationId1).isEqualTo(verificationId2);
    }

    @Test
    @DisplayName("Combined normalization: email with case and whitespace variations produce identical hash")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_combinedNormalization_producesIdenticalHash() throws Exception {
        // First request with mixed case and whitespace
        VerificationInitiateRequest request1 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "  Test.User@Example.COM  "
        );

        String response1 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        // Extract verification ID from first response
        String verificationId1 = objectMapper.readTree(response1).get("verificationId").asText();

        // Second request with normalized form (should reuse existing verification)
        VerificationInitiateRequest request2 = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "test.user@example.com"
        );

        String response2 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk()) // Should return 200 for reuse
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        // Extract verification ID from second response
        String verificationId2 = objectMapper.readTree(response2).get("verificationId").asText();

        // Both should return the same verification ID, indicating identical contact hash
        assertThat(verificationId1).isEqualTo(verificationId2);
    }
} 