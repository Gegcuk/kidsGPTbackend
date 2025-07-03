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
import uk.gegc.kidsgptbackend.dto.user.KidSelfUpdateRequest;
import uk.gegc.kidsgptbackend.dto.user.ParentUpdateKidRequest;
import uk.gegc.kidsgptbackend.dto.user.KidRegistrationRequest;
import uk.gegc.kidsgptbackend.dto.user.RegisterUserRequest;
import uk.gegc.kidsgptbackend.model.family.Kid;
import uk.gegc.kidsgptbackend.model.family.Parent;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.family.KidRepository;
import uk.gegc.kidsgptbackend.repository.family.ParentRepository;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
@DisplayName("Comprehensive User Lifecycle Integration Tests - Happy Paths")
class ComprehensiveUserLifecycleIntegrationTest {

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
    private KidRepository kidRepository;

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
    @DisplayName("Complete Happy Path: Admin → Parent → Multiple Kids → Profile Updates → Logout")
    void completeHappyPathFlow() throws Exception {
        // ===== ADMIN CREATION AND AUTHENTICATION =====
        User admin = createAdmin();
        String adminToken = authenticateUser("admin", "adminpass123");
        
        // Verify admin authentication state
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.email").value("admin@kidsgpt.com"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));

        // ===== PARENT REGISTRATION AND AUTHENTICATION =====
        String parentId = createUniqueId();
        String parentUsername = "parent" + parentId;
        String parentEmail = "parent" + parentId + "@test.com";
        
        // Register parent
        RegisterUserRequest parentRequest = new RegisterUserRequest(parentUsername, parentEmail, "parentpass123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(parentUsername))
                .andExpect(jsonPath("$.email").value(parentEmail))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_PARENT"))
                .andExpect(jsonPath("$.isActive").value(true));

        // Create parent profile (required for kid creation)
        createParentProfile(parentEmail);

        // Authenticate parent
        String parentToken = authenticateUser(parentUsername, "parentpass123");
        
        // Verify parent authentication state
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(parentUsername))
                .andExpect(jsonPath("$.role").value("ROLE_PARENT"));

        // ===== CREATE MULTIPLE KIDS WITH DIFFERENT AGE GROUPS =====
        String[] kidNames = {"Alice", "Bob", "Charlie"};
        AgeGroup[] ageGroups = {AgeGroup.AGE_6_8, AgeGroup.AGE_11_12, AgeGroup.AGE_15_16};
        String[] kidUsernames = new String[3];
        String[] kidTokens = new String[3];
        String[] kidIds = new String[3];

        for (int i = 0; i < 3; i++) {
            // Parent creates kid
            KidRegistrationRequest kidRequest = new KidRegistrationRequest(
                    kidNames[i], 
                    "kidpass" + i, 
                    ageGroups[i]
            );
            
            MvcResult kidResult = mockMvc.perform(post("/api/v1/auth/register-kid")
                            .header("Authorization", "Bearer " + parentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(kidRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nickname").value(kidNames[i]))
                    .andExpect(jsonPath("$.ageGroup").value(ageGroups[i].name()))
                    .andExpect(jsonPath("$.id").exists())
                    .andReturn();

            JsonNode kidResponse = objectMapper.readTree(kidResult.getResponse().getContentAsString());
            kidUsernames[i] = kidResponse.get("username").asText();
            kidIds[i] = kidResponse.get("id").asText();
            
            // Kid authenticates
            kidTokens[i] = authenticateUser(kidUsernames[i], "kidpass" + i);
            
            // Verify kid authentication state
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + kidTokens[i]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(kidUsernames[i]))
                    .andExpect(jsonPath("$.role").value("ROLE_CHILD"));
        }

        // ===== PROFILE UPDATE SCENARIOS =====
        // Kid updates their own profile (only avatar allowed)
        KidSelfUpdateRequest kidSelfUpdate = new KidSelfUpdateRequest("avatar_001");

        mockMvc.perform(patch("/api/v1/profile")
                        .header("Authorization", "Bearer " + kidTokens[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidSelfUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice")) // Name unchanged
                .andExpect(jsonPath("$.avatarId").value("avatar_001"));

        // Parent updates kid's profile (nickname, password, age group)
        ParentUpdateKidRequest parentUpdateKid = new ParentUpdateKidRequest(
                "Alice Updated", 
                "newpassword123", 
                AgeGroup.AGE_9_10
        );

        mockMvc.perform(patch("/api/v1/profile/kid/" + kidIds[0])
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentUpdateKid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Updated"))
                .andExpect(jsonPath("$.ageGroup").value("AGE_9_10"));

        // ===== LOGOUT SCENARIOS =====
        // Kid logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + kidTokens[0]))
                .andExpect(status().isOk());

        // Verify kid token is invalidated
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + kidTokens[0]))
                .andExpect(status().isUnauthorized());

        // Other kids can still access
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + kidTokens[1]))
                .andExpect(status().isOk());

        // Parent can still access
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Parent with Multiple Kids: Different Operations")
    void parentWithMultipleKidsOperations() throws Exception {
        // Setup parent
        String parentId = createUniqueId();
        String parentUsername = "parent" + parentId;
        String parentEmail = "parent" + parentId + "@test.com";
        
        RegisterUserRequest parentRequest = new RegisterUserRequest(parentUsername, parentEmail, "parentpass123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentRequest)))
                .andExpect(status().isCreated());

        createParentProfile(parentEmail);
        String parentToken = authenticateUser(parentUsername, "parentpass123");

        // Create two kids
        KidRegistrationRequest kid1 = new KidRegistrationRequest("Emma", "emmapass", AgeGroup.AGE_9_10);
        KidRegistrationRequest kid2 = new KidRegistrationRequest("Liam", "liampass", AgeGroup.AGE_13_14);
        
        // Create first kid
        MvcResult kid1Result = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kid1)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode kid1Response = objectMapper.readTree(kid1Result.getResponse().getContentAsString());
        String kid1Username = kid1Response.get("username").asText();
        String kid1Id = kid1Response.get("id").asText();

        // Create second kid
        MvcResult kid2Result = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kid2)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode kid2Response = objectMapper.readTree(kid2Result.getResponse().getContentAsString());
        String kid2Username = kid2Response.get("username").asText();
        String kid2Id = kid2Response.get("id").asText();
        
        // Verify both kids exist in database
        assertThat(kidRepository.count()).isGreaterThanOrEqualTo(2);
        
        // Both kids can authenticate independently
        String kid1Token = authenticateUser(kid1Username, "emmapass");
        String kid2Token = authenticateUser(kid2Username, "liampass");
        
        // Each kid can update their own profile (only avatar)
        KidSelfUpdateRequest kid1Update = new KidSelfUpdateRequest("avatar1");
        mockMvc.perform(patch("/api/v1/profile")
                        .header("Authorization", "Bearer " + kid1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kid1Update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Emma")) // Name unchanged
                .andExpect(jsonPath("$.avatarId").value("avatar1"));

        KidSelfUpdateRequest kid2Update = new KidSelfUpdateRequest("avatar2");
        mockMvc.perform(patch("/api/v1/profile")
                        .header("Authorization", "Bearer " + kid2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kid2Update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Liam")) // Name unchanged
                .andExpect(jsonPath("$.avatarId").value("avatar2"));

        // Parent can update both kids via parent endpoint
        ParentUpdateKidRequest parentUpdateKid1 = new ParentUpdateKidRequest("Emma Updated", null, AgeGroup.AGE_11_12);
        mockMvc.perform(patch("/api/v1/profile/kid/" + kid1Id)
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentUpdateKid1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Emma Updated"))
                .andExpect(jsonPath("$.ageGroup").value("AGE_11_12"));
    }

    @Test
    @DisplayName("Age Group Transitions: Kid Profile Updates")
    void ageGroupTransitions() throws Exception {
        // Setup parent and kid
        String parentId = createUniqueId();
        String parentUsername = "parent" + parentId;
        String parentEmail = "parent" + parentId + "@test.com";
        
        RegisterUserRequest parentRequest = new RegisterUserRequest(parentUsername, parentEmail, "parentpass123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentRequest)))
                .andExpect(status().isCreated());

        createParentProfile(parentEmail);
        String parentToken = authenticateUser(parentUsername, "parentpass123");

        // Create kid at AGE_6_8
        KidRegistrationRequest kidRequest = new KidRegistrationRequest("Sophie", "sophiepass", AgeGroup.AGE_6_8);
        MvcResult kidResult = mockMvc.perform(post("/api/v1/auth/register-kid")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode kidResponse = objectMapper.readTree(kidResult.getResponse().getContentAsString());
        String kidUsername = kidResponse.get("username").asText();
        String kidId = kidResponse.get("id").asText();
        String kidToken = authenticateUser(kidUsername, "sophiepass");

        // Kid updates their own avatar (kids can only update avatars)
        KidSelfUpdateRequest kidUpdate = new KidSelfUpdateRequest("avatar");
        mockMvc.perform(patch("/api/v1/profile")
                        .header("Authorization", "Bearer " + kidToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kidUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sophie")) // Name unchanged
                .andExpect(jsonPath("$.avatarId").value("avatar"));

        // Parent transitions kid to AGE_9_10 age group
        ParentUpdateKidRequest parentUpdate = new ParentUpdateKidRequest(
                "Sophie", 
                null, // Don't update password
                AgeGroup.AGE_9_10
        );
        mockMvc.perform(patch("/api/v1/profile/kid/" + kidId)
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ageGroup").value("AGE_9_10"));

        // Verify age group was updated in database
        Kid updatedKid = kidRepository.findByUserId(
                userRepository.findByUsername(kidUsername).get().getId()
        ).orElseThrow();
        assertThat(updatedKid.getAgeGroup()).isEqualTo(AgeGroup.AGE_9_10);
    }

    // Helper methods
    private User createAdmin() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@kidsgpt.com");
        admin.setHashedPassword(passwordEncoder.encode("adminpass123"));
        admin.setActive(true);
        admin.setRoles(new HashSet<>(Set.of(roleRepository.findByRole(RoleName.ROLE_ADMIN.name()).get())));
        return userRepository.save(admin);
    }

    private void createParentProfile(String email) {
        Parent parentProfile = new Parent();
        parentProfile.setFirstName("Test");
        parentProfile.setLastName("Parent");
        parentProfile.setEmail(email);
        parentRepository.save(parentProfile);
    }

    private String authenticateUser(String username, String password) throws Exception {
        AuthLoginRequest loginRequest = new AuthLoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private String createUniqueId() {
        return String.valueOf(System.currentTimeMillis()).substring(7);
    }

    private void ensureRoleExists(RoleName roleName) {
        roleRepository.findByRole(roleName.name()).orElseGet(() -> {
            Role role = new Role();
            role.setRole(roleName.name());
            return roleRepository.save(role);
        });
    }
} 