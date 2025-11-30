package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AuthController Kid Registration Integration Tests")
class AuthControllerKidRegistrationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParentRepository parentRepository;

    private String parentToken;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        
        // Create and authenticate parent
        setupParentUser();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-kid → 201 & kid details for valid request")
    void registerKid_validRequest_returnsKidDetails() throws Exception {
        // Given
        KidRegistrationRequest request = new KidRegistrationRequest(
                "TestKid",
                "kidpassword123",
                AgeGroup.AGE_9_10
        );

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nickname").value("TestKid"))
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.ageGroup").value("AGE_9_10"))
                .andExpect(jsonPath("$.favoriteColor").isEmpty())
                .andExpect(jsonPath("$.avatarId").isEmpty())
                .andExpect(jsonPath("$.interests").isEmpty())
                .andReturn();

        // Verify the generated username format
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String username = response.get("username").asText();
        assertThat(username).startsWith("testkid");
        assertThat(username).contains("kid");

        // Verify the kid can authenticate with generated username
        AuthLoginRequest kidLogin = new AuthLoginRequest(username, "kidpassword123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-kid → 401 when no authentication")
    void registerKid_noAuthentication_returnsUnauthorized() throws Exception {
        // Given
        KidRegistrationRequest request = new KidRegistrationRequest(
                "TestKid",
                "password123",
                AgeGroup.AGE_6_8
        );

        // When & Then
        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-kid → 400 when invalid authentication (non-parent)")
    void registerKid_nonParentAuth_returnsBadRequest() throws Exception {
        // Given - Create and authenticate a child user
        String uniqueId = String.valueOf(System.currentTimeMillis()).substring(7);
        User childUser = new User();
        childUser.setUsername("c" + uniqueId);
        childUser.setEmail("child" + uniqueId + "@test.com");
        childUser.setHashedPassword(passwordEncoder.encode("password123"));
        childUser.setActive(true);
        childUser.setRoles(Set.of(roleRepository.findByRole(RoleName.ROLE_CHILD.name()).get()));
        userRepository.save(childUser);

        // Authenticate as child
        AuthLoginRequest childLogin = new AuthLoginRequest(childUser.getUsername(), "password123");
        MvcResult childAuthResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode childResponse = objectMapper.readTree(childAuthResult.getResponse().getContentAsString());
        String childToken = childResponse.get("accessToken").asText();

        KidRegistrationRequest request = new KidRegistrationRequest(
                "TestKid",
                "password123",
                AgeGroup.AGE_6_8
        );

        // When & Then
        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + childToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Only parents can create kid accounts"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-kid → 400 when missing required fields")
    void registerKid_missingFields_returnsBadRequest() throws Exception {
        // When & Then - Missing nickname
        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test123\",\"ageGroup\":\"AGE_6_8\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));

        // Missing password
        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"Test\",\"ageGroup\":\"AGE_6_8\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));

        // Missing ageGroup
        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"Test\",\"password\":\"test123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-kid → 400 when field validation fails")
    void registerKid_validationFails_returnsBadRequest() throws Exception {
        // Nickname too short
        KidRegistrationRequest shortNickname = new KidRegistrationRequest(
                "A", // Too short
                "password123",
                AgeGroup.AGE_6_8
        );

        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortNickname)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));

        // Password too short
        KidRegistrationRequest shortPassword = new KidRegistrationRequest(
                "TestKid",
                "123", // Too short
                AgeGroup.AGE_6_8
        );

        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));

        // Nickname too long
        KidRegistrationRequest longNickname = new KidRegistrationRequest(
                "A".repeat(51), // Too long
                "password123",
                AgeGroup.AGE_6_8
        );

        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longNickname)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-kid → 400 when malformed JSON")
    void registerKid_malformedJson_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed Request"));
    }

    @Test
    @DisplayName("Multiple kids can be created with unique usernames")
    void registerKid_multipleKids_generatesUniqueUsernames() throws Exception {
        String[] nicknames = {"Alice", "Bob", "Charlie"};
        String[] passwords = {"alice123", "bob123", "charlie123"};
        AgeGroup[] ageGroups = {AgeGroup.AGE_6_8, AgeGroup.AGE_9_10, AgeGroup.AGE_11_12};

        for (int i = 0; i < 3; i++) {
            KidRegistrationRequest request = new KidRegistrationRequest(
                    nicknames[i],
                    passwords[i],
                    ageGroups[i]
            );

            MvcResult result = mockMvc.perform(post("/api/v1/auth/register-kid")
                            .header("Authorization", "Bearer " + parentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nickname").value(nicknames[i]))
                    .andExpect(jsonPath("$.ageGroup").value(ageGroups[i].name()))
                    .andReturn();

            // Verify unique username generation
            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String username = response.get("username").asText();
            assertThat(username).contains(nicknames[i].toLowerCase());
            assertThat(username).contains("kid");

            // Verify login works with generated username
            AuthLoginRequest kidLogin = new AuthLoginRequest(username, passwords[i]);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(kidLogin)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Username collision handling generates incremental usernames")
    void registerKid_usernameCollision_generatesIncrementalUsernames() throws Exception {
        // Create multiple kids with same nickname
        for (int i = 0; i < 3; i++) {
            KidRegistrationRequest request = new KidRegistrationRequest(
                    "SameName",
                    "password" + i,
                    AgeGroup.AGE_6_8
            );

            MvcResult result = mockMvc.perform(post("/api/v1/auth/register-kid")
                            .header("Authorization", "Bearer " + parentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nickname").value("SameName"))
                    .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            String username = response.get("username").asText();
            
            // First should be "samename_kid", then "samename_kid1", etc.
            assertThat(username).startsWith("samename_kid");
            
            // Verify login works
            AuthLoginRequest kidLogin = new AuthLoginRequest(username, "password" + i);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(kidLogin)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/kids/{kidId} → 204 for valid parent request")
    void deleteKid_validParentRequest_returnsNoContent() throws Exception {
        // First create a kid
        KidRegistrationRequest createRequest = new KidRegistrationRequest(
                "KidToDelete",
                "password123",
                AgeGroup.AGE_9_10
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String kidId = createResponse.get("id").asText();
        String kidUsername = createResponse.get("username").asText();

        // Verify kid exists and can login
        AuthLoginRequest kidLogin = new AuthLoginRequest(kidUsername, "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidLogin)))
                .andExpect(status().isOk());

        // Delete the kid
        mockMvc.perform(delete("/api/v1/auth/kids/" + kidId)
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isNoContent());

        // Verify kid no longer exists
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidLogin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/kids/{kidId} → 400 when kid not found")
    void deleteKid_kidNotFound_returnsBadRequest() throws Exception {
        UUID nonExistentKidId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/auth/kids/" + nonExistentKidId)
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Kid not found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/kids/{kidId} → 401 when not authenticated")
    void deleteKid_notAuthenticated_returnsUnauthorized() throws Exception {
        UUID kidId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/auth/kids/" + kidId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/kids/{kidId} → 403 when user is not a parent")
    void deleteKid_userNotParent_returnsForbidden() throws Exception {
        // Given - Create and authenticate a child user
        String uniqueId = String.valueOf(System.currentTimeMillis()).substring(7);
        User childUser = new User();
        childUser.setUsername("c" + uniqueId);
        childUser.setEmail("child" + uniqueId + "@test.com");
        childUser.setHashedPassword(passwordEncoder.encode("password123"));
        childUser.setActive(true);
        childUser.setRoles(Set.of(roleRepository.findByRole(RoleName.ROLE_CHILD.name()).get()));
        userRepository.save(childUser);

        // Authenticate as child
        AuthLoginRequest childLogin = new AuthLoginRequest(childUser.getUsername(), "password123");
        MvcResult childAuthResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode childResponse = objectMapper.readTree(childAuthResult.getResponse().getContentAsString());
        String childToken = childResponse.get("accessToken").asText();

        UUID kidId = UUID.randomUUID();

        // When & Then - Spring Security blocks access before reaching service
        mockMvc.perform(delete("/api/v1/auth/kids/" + kidId)
                        .header("Authorization", "Bearer " + childToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/kids/{kidId} → 400 when trying to delete another parent's kid")
    void deleteKid_anotherParentKid_returnsBadRequest() throws Exception {
        // Create a second parent
        String uniqueId = String.valueOf(System.currentTimeMillis()).substring(7);
        String secondParentUsername = "p2" + uniqueId;
        String secondParentEmail = "parent2" + uniqueId + "@test.com";
        
        RegisterUserRequest secondParentRequest = new RegisterUserRequest(secondParentUsername, secondParentEmail, "parentpass123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondParentRequest)))
                .andExpect(status().isCreated());

        // Authenticate second parent
        AuthLoginRequest secondParentLogin = new AuthLoginRequest(secondParentUsername, "parentpass123");
        MvcResult secondParentAuthResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondParentLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode secondParentResponse = objectMapper.readTree(secondParentAuthResult.getResponse().getContentAsString());
        String secondParentToken = secondParentResponse.get("accessToken").asText();

        // Create a kid with the first parent
        KidRegistrationRequest createRequest = new KidRegistrationRequest(
                "KidToDelete",
                "password123",
                AgeGroup.AGE_9_10
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String kidId = createResponse.get("id").asText();

        // Try to delete with second parent (should fail)
        mockMvc.perform(delete("/api/v1/auth/kids/" + kidId)
                        .header("Authorization", "Bearer " + secondParentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("You can only delete your own kids' accounts"));
    }

    private void setupParentUser() throws Exception {
        // Register parent
        String uniqueId = String.valueOf(System.currentTimeMillis()).substring(7); // Last 6 digits
        String uniqueUsername = "tp" + uniqueId; // Keep under 20 chars
        String uniqueEmail = "parent" + uniqueId + "@test.com";
        RegisterUserRequest parentRequest = new RegisterUserRequest(
                uniqueUsername,
                uniqueEmail,
                "parentpass123"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentRequest)))
                .andExpect(status().isCreated());

        // Parent profile is now created automatically during registration
        // No need to manually create it

        // Authenticate parent
        AuthLoginRequest parentLogin = new AuthLoginRequest(uniqueUsername, "parentpass123");
        MvcResult parentAuthResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode parentResponse = objectMapper.readTree(parentAuthResult.getResponse().getContentAsString());
        parentToken = parentResponse.get("accessToken").asText();
    }
} 