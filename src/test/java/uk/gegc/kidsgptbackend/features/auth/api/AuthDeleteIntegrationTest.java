package uk.gegc.kidsgptbackend.features.auth.api;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gegc.kidsgptbackend.features.auth.api.dto.AuthLoginRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidRegistrationRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.RegisterUserRequest;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.Role;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.user.domain.repository.RoleRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthDeleteIntegrationTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private String parentToken;
    private String parentUsername;
    private String kidId;
    private String kidUsername;

    @BeforeEach
    void setupTestData() throws Exception {
        // Create roles
        Role parentRole = roleRepository.findByRole(RoleName.ROLE_PARENT.name())
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setRole(RoleName.ROLE_PARENT.name());
                    return roleRepository.save(role);
                });

        Role childRole = roleRepository.findByRole(RoleName.ROLE_CHILD.name())
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setRole(RoleName.ROLE_CHILD.name());
                    return roleRepository.save(role);
                });

        // Create parent
        String uniqueId = String.valueOf(System.currentTimeMillis()).substring(7);
        parentUsername = "parent" + uniqueId;
        String parentEmail = "parent" + uniqueId + "@test.com";

        RegisterUserRequest parentRequest = new RegisterUserRequest(parentUsername, parentEmail, "parentpass123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentRequest)))
                .andExpect(status().isCreated());

        // Authenticate parent
        AuthLoginRequest parentLogin = new AuthLoginRequest(parentUsername, "parentpass123");
        MvcResult parentAuthResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentLogin)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode parentResponse = objectMapper.readTree(parentAuthResult.getResponse().getContentAsString());
        parentToken = parentResponse.get("accessToken").asText();

        // Create kid
        KidRegistrationRequest kidRequest = new KidRegistrationRequest("TestKid", "kidpass123", AgeGroup.AGE_9_10);
        MvcResult kidResult = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode kidResponse = objectMapper.readTree(kidResult.getResponse().getContentAsString());
        kidId = kidResponse.get("id").asText();
        kidUsername = kidResponse.get("username").asText();
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/kids/{kidId} → 204 for valid parent request")
    void deleteKid_validParentRequest_returnsNoContent() throws Exception {
        // Verify kid exists and can login
        AuthLoginRequest kidLogin = new AuthLoginRequest(kidUsername, "kidpass123");
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
        // Create and authenticate a child user
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

        // Spring Security blocks access before reaching service
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

        // Try to delete with second parent (should fail)
        mockMvc.perform(delete("/api/v1/auth/kids/" + kidId)
                        .header("Authorization", "Bearer " + secondParentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("You can only delete your own kids' accounts"));
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/account → 204 for valid parent request with no kids")
    void deleteParentAccount_validParentRequest_returnsNoContent() throws Exception {
        // First delete the kid
        mockMvc.perform(delete("/api/v1/auth/kids/" + kidId)
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isNoContent());

        // Verify parent can still login
        AuthLoginRequest parentLogin = new AuthLoginRequest(parentUsername, "parentpass123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentLogin)))
                .andExpect(status().isOk());

        // Delete parent account
        mockMvc.perform(delete("/api/v1/auth/account")
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isNoContent());

        // Verify parent no longer exists
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentLogin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/account → 400 when parent has kids")
    void deleteParentAccount_parentWithKids_returnsBadRequest() throws Exception {
        // Try to delete parent account while kid still exists
        mockMvc.perform(delete("/api/v1/auth/account")
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Cannot delete parent account with existing kids. Please delete all kids first."));
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/account → 401 when not authenticated")
    void deleteParentAccount_notAuthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/account"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/account → 403 when user is not a parent")
    void deleteParentAccount_userNotParent_returnsForbidden() throws Exception {
        // Create and authenticate a child user
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

        // Spring Security blocks access before reaching service
        mockMvc.perform(delete("/api/v1/auth/account")
                        .header("Authorization", "Bearer " + childToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    @DisplayName("Complete lifecycle: Create parent → Create kid → Delete kid → Delete parent")
    void completeDeleteLifecycle() throws Exception {
        // Verify initial state
        assertThat(kidId).isNotNull();
        assertThat(parentToken).isNotNull();

        // Verify kid can login
        AuthLoginRequest kidLogin = new AuthLoginRequest(kidUsername, "kidpass123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidLogin)))
                .andExpect(status().isOk());

        // Verify parent can login
        AuthLoginRequest parentLogin = new AuthLoginRequest(parentUsername, "parentpass123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentLogin)))
                .andExpect(status().isOk());

        // Step 1: Delete kid
        mockMvc.perform(delete("/api/v1/auth/kids/" + kidId)
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isNoContent());

        // Verify kid no longer exists
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidLogin)))
                .andExpect(status().isUnauthorized());

        // Step 2: Delete parent
        mockMvc.perform(delete("/api/v1/auth/account")
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isNoContent());

        // Verify parent no longer exists
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentLogin)))
                .andExpect(status().isUnauthorized());
    }
} 