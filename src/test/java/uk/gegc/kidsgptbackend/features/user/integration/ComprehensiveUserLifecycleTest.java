package uk.gegc.kidsgptbackend.features.user.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidRegistrationRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidSelfUpdateRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.ParentUpdateKidRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.RegisterUserRequest;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.family.domain.repository.KidRepository;
import uk.gegc.kidsgptbackend.features.family.domain.repository.ParentRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Comprehensive User Lifecycle Tests - Missing Happy Paths")
class ComprehensiveUserLifecycleTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private KidRepository kidRepository;

    @Test
    @DisplayName("Complete Happy Path: Admin creation, /me endpoint, logout")
    void adminLifecycle() throws Exception {
        // Create admin
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@kidsgpt.com");
        admin.setHashedPassword(passwordEncoder.encode("adminpass123"));
        admin.setActive(true);
        admin.setRoles(Set.of(roleRepository.findByRole(RoleName.ROLE_ADMIN.name()).get()));
        userRepository.save(admin);

        // Authenticate admin
        AuthLoginRequest adminLogin = new AuthLoginRequest("admin", "adminpass123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String adminToken = response.get("accessToken").asText();

        // Check /me endpoint
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.email").value("admin@kidsgpt.com"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Verify token is invalidated
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Parent creates multiple kids and updates their profiles")
    void parentMultipleKidsWithUpdates() throws Exception {
        // Create parent
        String uniqueId = String.valueOf(System.currentTimeMillis()).substring(7);
        String parentUsername = "p" + uniqueId;
        String parentEmail = "parent" + uniqueId + "@test.com";
        
        RegisterUserRequest parentRequest = new RegisterUserRequest(parentUsername, parentEmail, "parentpass123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentRequest)))
                .andExpect(status().isCreated());

        AuthLoginRequest parentLogin = new AuthLoginRequest(parentUsername, "parentpass123");
        MvcResult parentResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode parentResponse = objectMapper.readTree(parentResult.getResponse().getContentAsString());
        String parentToken = parentResponse.get("accessToken").asText();

        // Create first kid
        KidRegistrationRequest kid1 = new KidRegistrationRequest("Emma", "emmapass", AgeGroup.AGE_9_10);
        MvcResult kid1Result = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kid1)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode kid1Response = objectMapper.readTree(kid1Result.getResponse().getContentAsString());
        String kid1Username = kid1Response.get("username").asText();

        // Create second kid
        KidRegistrationRequest kid2 = new KidRegistrationRequest("Liam", "liampass", AgeGroup.AGE_13_14);
        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kid2)))
                .andExpect(status().isCreated());

        // Parent updates first kid's profile
        String kid1Id = kid1Response.get("id").asText();
        ParentUpdateKidRequest update = new ParentUpdateKidRequest("Emma Updated", null, AgeGroup.AGE_9_10);
        mockMvc.perform(patch("/api/v1/profile/kid/" + kid1Id)
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Emma Updated"));
    }

    @Test
    @DisplayName("Kid updates own profile with age group transition")
    void kidProfileUpdateWithAgeTransition() throws Exception {
        // Setup parent and kid
        String uniqueId = String.valueOf(System.currentTimeMillis()).substring(7);
        String parentUsername = "p" + uniqueId;
        String parentEmail = "parent" + uniqueId + "@test.com";
        
        RegisterUserRequest parentRequest = new RegisterUserRequest(parentUsername, parentEmail, "parentpass123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentRequest)))
                .andExpect(status().isCreated());

        AuthLoginRequest parentLogin = new AuthLoginRequest(parentUsername, "parentpass123");
        MvcResult parentResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode parentResponse = objectMapper.readTree(parentResult.getResponse().getContentAsString());
        String parentToken = parentResponse.get("accessToken").asText();

        // Create kid at age boundary
        KidRegistrationRequest kidRequest = new KidRegistrationRequest("Sophie", "sophiepass", AgeGroup.AGE_6_8);
        MvcResult kidResult = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode kidResponse = objectMapper.readTree(kidResult.getResponse().getContentAsString());
        String kidUsername = kidResponse.get("username").asText();

        // Kid authenticates
        AuthLoginRequest kidLogin = new AuthLoginRequest(kidUsername, "sophiepass");
        MvcResult kidAuthResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode kidAuthResponse = objectMapper.readTree(kidAuthResult.getResponse().getContentAsString());
        String kidToken = kidAuthResponse.get("accessToken").asText();

        // Kid updates profile with new avatar
        KidSelfUpdateRequest kidUpdate = new KidSelfUpdateRequest("princess_avatar");
        mockMvc.perform(patch("/api/v1/profile")
                        .header("Authorization", "Bearer " + kidToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarId").value("princess_avatar"));
    }

} 