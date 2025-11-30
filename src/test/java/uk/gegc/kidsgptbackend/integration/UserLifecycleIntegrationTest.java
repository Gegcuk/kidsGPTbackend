package uk.gegc.kidsgptbackend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.dto.user.KidRegistrationRequest;
import uk.gegc.kidsgptbackend.dto.user.RegisterUserRequest;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.family.ParentRepository;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("User Lifecycle Integration Tests")
class UserLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure roles exist
        ensureRoleExists(RoleName.ROLE_ADMIN);
        ensureRoleExists(RoleName.ROLE_PARENT);
        ensureRoleExists(RoleName.ROLE_CHILD);
    }

    @Test
    @DisplayName("Basic Integration: Parent registers kid and both can authenticate")
    void basicParentKidFlow() throws Exception {
        // Create parent
        String uniqueId = String.valueOf(System.currentTimeMillis()).substring(7); // Last 6 digits
        String parentUsername = "p" + uniqueId; // Keep under 20 chars
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
        String parentToken = parentResponse.get("accessToken").asText();

        // Parent creates kid
        KidRegistrationRequest kidRequest = new KidRegistrationRequest("TestKid", "kidpass123", AgeGroup.AGE_9_10);
        MvcResult kidResult = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("TestKid"))
                .andReturn();

        JsonNode kidResponse = objectMapper.readTree(kidResult.getResponse().getContentAsString());
        String kidUsername = kidResponse.get("username").asText();

        // Kid can authenticate
        AuthLoginRequest kidLogin = new AuthLoginRequest(kidUsername, "kidpass123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("Role-based access control validation")  
    void testRoleBasedAccessValidation() throws Exception {
        // Create a child user directly
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

        // Child should NOT be able to register kids
        KidRegistrationRequest unauthorizedKidRequest = new KidRegistrationRequest(
                "Unauthorized",
                "password123",
                AgeGroup.AGE_6_8
        );

        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + childToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unauthorizedKidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Only parents can create kid accounts"));
    }

    @Test
    @DisplayName("Error Scenarios: Invalid credentials and malformed requests")
    void testErrorScenarios() throws Exception {
        // Invalid login credentials
        AuthLoginRequest invalidLogin = new AuthLoginRequest("nonexistent", "wrongpassword");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLogin)))
                .andExpect(status().isUnauthorized());

        // Kid registration without authentication
        KidRegistrationRequest kidRequest = new KidRegistrationRequest(
                "Johnny",
                "password123",
                AgeGroup.AGE_6_8
        );

        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidRequest)))
                .andExpect(status().isUnauthorized());
    }

    private void ensureRoleExists(RoleName roleName) {
        roleRepository.findByRole(roleName.name()).orElseGet(() -> {
            Role role = new Role();
            role.setRole(roleName.name());
            return roleRepository.save(role);
        });
    }
} 