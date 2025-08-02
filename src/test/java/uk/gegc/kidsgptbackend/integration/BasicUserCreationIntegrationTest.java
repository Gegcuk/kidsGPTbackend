package uk.gegc.kidsgptbackend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
@DisplayName("Basic User Creation Integration Tests")
class BasicUserCreationIntegrationTest {

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
    void setUp() {
        // Ensure roles exist
        ensureRoleExists(RoleName.ROLE_ADMIN);
        ensureRoleExists(RoleName.ROLE_PARENT);
        ensureRoleExists(RoleName.ROLE_CHILD);
    }

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

    private void ensureRoleExists(RoleName roleName) {
        roleRepository.findByRole(roleName.name()).orElseGet(() -> {
            Role role = new Role();
            role.setRole(roleName.name());
            return roleRepository.save(role);
        });
    }
} 