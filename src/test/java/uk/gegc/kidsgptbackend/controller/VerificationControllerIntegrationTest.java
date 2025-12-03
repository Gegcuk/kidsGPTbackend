package uk.gegc.kidsgptbackend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationInitiateRequest;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VerificationControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    UserRepository userRepository;

    private User testParent;
    private UUID parentId;

    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp(); // Ensure roles are created
        // Create parent role if it doesn't exist
        roleRepository.findByRole("ROLE_PARENT").orElseGet(() -> {
            uk.gegc.kidsgptbackend.features.user.domain.model.Role role = new uk.gegc.kidsgptbackend.features.user.domain.model.Role();
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
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("parentId: Parent ID is required")));
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
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("verificationMethod: Verification method is required")));
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
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("contactInfo: Contact information is required")));
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
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("contactInfo: contactInfo must be a valid email address when verificationMethod=EMAIL")));
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
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("contactInfo: contactInfo must be an E.164 phone (e.g. +15551234567) when verificationMethod=SMS")));
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
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("verificationMethod: Unsupported verification method")));
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
                .andExpect(jsonPath("$.title").value("Validation Failed"));
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
                .andExpect(jsonPath("$.title").value("Malformed Request"));
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

    // Section 2.1: Success responses tests
    @Test
    @DisplayName("201 Created for new verification with proper headers and Location")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_newVerification_returns201WithProperHeaders() throws Exception {
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "newverification@example.com"
        );

        String response = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/status/")))
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.verificationId").exists())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        // Verify response body contains expected fields
        assertThat(response).contains("\"verificationId\"");
        assertThat(response).contains("\"verificationStatus\":\"PENDING\"");
        
        // Extract verification ID from response and verify Location header format
        String verificationId = objectMapper.readTree(response).get("verificationId").asText();
        assertThat(verificationId).isNotNull();
        assertThat(verificationId).isNotEmpty();
    }

    @Test
    @DisplayName("200 OK for reuse of existing pending verification (idempotent)")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_reuseExistingVerification_returns200WithSameHeaders() throws Exception {
        // First request - should create new verification
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "reuse@example.com"
        );

        String response1 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/status/")))
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String verificationId1 = objectMapper.readTree(response1).get("verificationId").asText();

        // Second request with same data - should reuse existing verification
        String response2 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.verificationId").exists())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        String verificationId2 = objectMapper.readTree(response2).get("verificationId").asText();

        // Both should return the same verification ID (idempotent behavior)
        assertThat(verificationId1).isEqualTo(verificationId2);
        
        // Verify response body contains expected fields
        assertThat(response2).contains("\"verificationId\"");
        assertThat(response2).contains("\"verificationStatus\":\"PENDING\"");
    }

    @Test
    @DisplayName("201 Created for SMS verification with proper headers (even if SMS not implemented)")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_smsVerification_returns201WithProperHeaders() throws Exception {
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "+15551234567"
        );

        String response = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/status/")))
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.verificationId").exists())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        // Verify response body contains expected fields
        assertThat(response).contains("\"verificationId\"");
        assertThat(response).contains("\"verificationStatus\":\"PENDING\"");
        
        // Extract verification ID from response and verify Location header format
        String verificationId = objectMapper.readTree(response).get("verificationId").asText();
        assertThat(verificationId).isNotNull();
        assertThat(verificationId).isNotEmpty();
    }

    @Test
    @DisplayName("200 OK for SMS verification reuse with proper headers")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_smsVerificationReuse_returns200WithSameHeaders() throws Exception {
        // First request - should create new SMS verification
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "+15559876543"
        );

        String response1 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/status/")))
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String verificationId1 = objectMapper.readTree(response1).get("verificationId").asText();

        // Second request with same data - should reuse existing SMS verification
        String response2 = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(header().string("X-Verification-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.verificationId").exists())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        String verificationId2 = objectMapper.readTree(response2).get("verificationId").asText();

        // Both should return the same verification ID (idempotent behavior)
        assertThat(verificationId1).isEqualTo(verificationId2);
        
        // Verify response body contains expected fields
        assertThat(response2).contains("\"verificationId\"");
                 assertThat(response2).contains("\"verificationStatus\":\"PENDING\"");
     }

    // Section 2.2: Validation failures → 400 tests
    @Test
    @DisplayName("Validation failures return 400 with proper ProblemDetail format (RFC 7807)")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_validationFailures_return400WithErrorResponse() throws Exception {
        // Test missing parentId returns 400 with ProblemDetail
        String jsonMissingParentId = """
                {
                    "verificationMethod": "EMAIL",
                    "contactInfo": "test@example.com"
                }
                """;

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMissingParentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("parentId: Parent ID is required")))
                .andExpect(jsonPath("$.timestamp").exists());

        // Test missing verificationMethod returns 400 with ProblemDetail
        String jsonMissingMethod = """
                {
                    "parentId": "%s",
                    "contactInfo": "test@example.com"
                }
                """.formatted(parentId);

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMissingMethod))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("verificationMethod: Verification method is required")))
                .andExpect(jsonPath("$.timestamp").exists());

        // Test missing contactInfo returns 400 with ProblemDetail
        String jsonMissingContact = """
                {
                    "parentId": "%s",
                    "verificationMethod": "EMAIL"
                }
                """.formatted(parentId);

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMissingContact))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("contactInfo: Contact information is required")))
                .andExpect(jsonPath("$.timestamp").exists());

        // Test invalid email format returns 400 with ProblemDetail
        VerificationInitiateRequest invalidEmailRequest = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "invalid-email"
        );

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidEmailRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("contactInfo: contactInfo must be a valid email address when verificationMethod=EMAIL")))
                .andExpect(jsonPath("$.timestamp").exists());

        // Test invalid SMS format returns 400 with ProblemDetail
        VerificationInitiateRequest invalidSmsRequest = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "1234567890" // Missing + prefix
        );

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSmsRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("contactInfo: contactInfo must be an E.164 phone (e.g. +15551234567) when verificationMethod=SMS")))
                .andExpect(jsonPath("$.timestamp").exists());

        // Test unsupported method returns 400 with ProblemDetail
        String jsonUnsupportedMethod = """
                {
                    "parentId": "%s",
                    "verificationMethod": "PHONE_CALL",
                    "contactInfo": "test@example.com"
                }
                """.formatted(parentId);

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonUnsupportedMethod))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("verificationMethod: Unsupported verification method")))
                .andExpect(jsonPath("$.timestamp").exists());

        // Test empty JSON body returns 400 with ProblemDetail
        String emptyJson = "{}";

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.timestamp").exists());

        // Test malformed JSON returns 400 with ProblemDetail
        String malformedJson = "{ invalid json }";

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed Request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Multiple validation errors return 400 with all error details")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_multipleValidationErrors_return400WithAllDetails() throws Exception {
        // Test with multiple missing fields
        String jsonMultipleErrors = """
                {
                    "verificationMethod": "PHONE_CALL"
                }
                """;

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMultipleErrors))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("parentId: Parent ID is required")))
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasItem("contactInfo: Contact information is required")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("ProblemDetail structure validation for validation failures (RFC 7807)")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_errorResponseStructure_validationFailures() throws Exception {
        String json = """
                {
                    "verificationMethod": "EMAIL",
                    "contactInfo": "test@example.com"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();

        // Verify ProblemDetail structure (RFC 7807)
        assertThat(response).contains("\"title\":\"Validation Failed\"");
        assertThat(response).contains("\"type\"");
        assertThat(response).contains("\"status\"");
        assertThat(response).contains("\"timestamp\"");
        
        // Verify that errors is an array in extensions
        assertThat(response).contains("\"errors\":[");
        assertThat(response).contains("parentId: Parent ID is required");
     }

    // Section 2.3: Parent not found → 404 tests
    @Test
    @DisplayName("Nonexistent parentId returns 404 with ResponseStatusException")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_nonexistentParentId_returns404() throws Exception {
        // Create a non-existent parent ID
        UUID nonexistentParentId = UUID.randomUUID();
        
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                nonexistentParentId,
                VerificationMethod.EMAIL,
                "test@example.com"
        );

        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Parent not found with ID")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("404 response structure validation for nonexistent parent")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_404ResponseStructure_nonexistentParent() throws Exception {
        // Create a non-existent parent ID
        UUID nonexistentParentId = UUID.randomUUID();
        
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                nonexistentParentId,
                VerificationMethod.EMAIL,
                "test@example.com"
        );

        String response = mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();

        // Verify ProblemDetail structure for 404 (RFC 7807)
        assertThat(response).contains("\"title\":\"Resource Not Found\"");
        assertThat(response).contains("\"detail\"").contains("Parent not found with ID");
        assertThat(response).contains("\"timestamp\"");
        assertThat(response).contains("\"status\":404");
    }

    // Section 2.5: No PII leakage in logs test
    @Test
    @DisplayName("No PII leakage in logs - Verify that application logs mask email domain")
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    void initiateVerification_noPiiLeakageInLogs_masksEmailDomain() throws Exception {
        // Create a test appender to capture logs
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("uk.gegc.kidsgptbackend");
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            // Test with various email addresses that should be masked
            String[] testEmails = {
                "user@example.com",
                "john.doe@test.org",
                "admin@company.co.uk",
                "test@subdomain.example.net"
            };

            for (String email : testEmails) {
                VerificationInitiateRequest request = new VerificationInitiateRequest(
                        parentId,
                        VerificationMethod.EMAIL,
                        email
                );

                // Clear previous logs
                listAppender.list.clear();

                // Perform the request
                mockMvc.perform(post("/api/v1/verification/initiate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());

                // Get all logged messages
                String allLogs = listAppender.list.stream()
                        .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                        .collect(java.util.stream.Collectors.joining(" "));

                // Verify that the full email address is NOT logged
                assertThat(allLogs).doesNotContain(email);
                
                // Verify that masked email format is used (***@domain)
                String expectedMaskedFormat = "***@" + email.substring(email.indexOf('@') + 1);
                assertThat(allLogs).contains(expectedMaskedFormat);
            }

            // Test with phone numbers (SMS verification)
            String testPhone = "+1234567890";
            VerificationInitiateRequest phoneRequest = new VerificationInitiateRequest(
                    parentId,
                    VerificationMethod.SMS,
                    testPhone
            );

            // Clear previous logs
            listAppender.list.clear();

            // Perform the request
            mockMvc.perform(post("/api/v1/verification/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(phoneRequest)))
                    .andExpect(status().isCreated());

            // Get all logged messages
            String allLogs = listAppender.list.stream()
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining(" "));

            // Verify that the full phone number is NOT logged
            assertThat(allLogs).doesNotContain(testPhone);
            
            // Verify that phone number is masked (show only last 2 digits)
            String expectedMaskedPhone = "***" + testPhone.substring(testPhone.length() - 2);
            assertThat(allLogs).contains(expectedMaskedPhone);

        } finally {
            // Clean up
            logger.detachAppender(listAppender);
        }
    }
} 