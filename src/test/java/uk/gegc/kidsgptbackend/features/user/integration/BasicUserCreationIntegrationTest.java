package uk.gegc.kidsgptbackend.features.user.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidRegistrationRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.RegisterUserRequest;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.family.domain.repository.ParentRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Basic User Creation Integration Tests")
class BasicUserCreationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Test
    @DisplayName("Basic Flow: Create admin, parent, and kid")
    void basicUserCreationFlow() throws Exception {
        // Step 1: Create admin manually
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@kidsgpt.com");
        admin.setHashedPassword(passwordEncoder.encode("adminpass123"));
        admin.setActive(true);
        admin.setRoles(Set.of(roleRepository.findByRole(RoleName.ROLE_ADMIN.name()).get()));
        userRepository.save(admin);

        // Step 2: Register parent
        RegisterUserRequest parentRequest = new RegisterUserRequest(
                "testparent",
                "parent@example.com",
                "parentpass123"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("testparent"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_PARENT"));

        // Step 4: Authenticate parent
        AuthLoginRequest parentLogin = new AuthLoginRequest("testparent", "parentpass123");
        MvcResult parentAuthResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        JsonNode parentResponse = objectMapper.readTree(parentAuthResult.getResponse().getContentAsString());
        String parentToken = parentResponse.get("accessToken").asText();
        
        assertThat(parentToken).isNotEmpty();

        // Step 5: Parent creates kid
        KidRegistrationRequest kidRequest = new KidRegistrationRequest(
                "Johnny",
                "kidpass123",
                AgeGroup.AGE_9_10
        );

        MvcResult kidCreationResult = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("Johnny"))
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.ageGroup").value("AGE_9_10"))
                .andReturn();

        JsonNode kidResponse = objectMapper.readTree(kidCreationResult.getResponse().getContentAsString());
        String kidUsername = kidResponse.get("username").asText();
        
        assertThat(kidUsername).startsWith("johnny");
        assertThat(kidUsername).contains("kid");

        // Step 6: Authenticate kid
        AuthLoginRequest kidLogin = new AuthLoginRequest(kidUsername, "kidpass123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("Kid registration validation")
    void kidRegistrationValidation() throws Exception {
        // Setup parent
        String parentToken = setupParentAndGetToken();

        // Test invalid requests
        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // Test unauthorized access
        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new KidRegistrationRequest("Test", "password", AgeGroup.AGE_6_8))))
                .andExpect(status().isUnauthorized());
    }

    private String setupParentAndGetToken() throws Exception {
        // Register parent
        RegisterUserRequest parentRequest = new RegisterUserRequest(
                "testparent2",
                "parent2@example.com",
                "parentpass123"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentRequest)))
                .andExpect(status().isCreated());

        // Authenticate parent
        AuthLoginRequest parentLogin = new AuthLoginRequest("testparent2", "parentpass123");
        MvcResult parentAuthResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode parentResponse = objectMapper.readTree(parentAuthResult.getResponse().getContentAsString());
        return parentResponse.get("accessToken").asText();
    }

} 