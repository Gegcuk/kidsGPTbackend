package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileUpdateRequest;
import uk.gegc.kidsgptbackend.model.family.Kid;
import uk.gegc.kidsgptbackend.model.family.Parent;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.family.KidRepository;
import uk.gegc.kidsgptbackend.repository.family.ParentRepository;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private KidRepository kidRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private User testUser;
    private Parent testParent;
    private Kid testKid;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Create test data
        setupTestData();
    }

    private void setupTestData() {
        // Create role
        Role userRole = roleRepository.findByRole(RoleName.ROLE_CHILD.name())
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setRole(RoleName.ROLE_CHILD.name());
                    return roleRepository.save(role);
                });

        // Create user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setHashedPassword("hashedPassword");
        testUser.setRoles(Set.of(userRole));
        testUser.setActive(true);
        testUser = userRepository.save(testUser);

        // Create parent with matching email
        testParent = new Parent();
        testParent.setFirstName("Test");
        testParent.setLastName("Parent");
        testParent.setEmail("test@example.com"); // Same email as user
        testParent = parentRepository.save(testParent);

        // Create kid
        testKid = new Kid();
        testKid.setFirstName("Test");
        testKid.setLastName("Kid");
        testKid.setBirthDate(LocalDate.of(2015, 1, 1));
        testKid.setParent(testParent);
        testKid = kidRepository.save(testKid);
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should successfully update child profile with valid request")
    void updateProfile_ValidRequest_ReturnsUpdatedProfile() throws Exception {
        // Given
        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                8,
                "soccer, reading",
                "avatar123"
        );

        // When & Then
        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Johnny"))
                .andExpect(jsonPath("$.age").value(8))
                .andExpect(jsonPath("$.interests").value("soccer, reading"))
                .andExpect(jsonPath("$.avatarId").value("avatar123"));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should return 400 when age is too young (less than 3)")
    void updateProfile_AgeTooYoung_Returns400() throws Exception {
        // Given
        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                2, // Too young
                "soccer",
                "avatar123"
        );

        // When & Then
        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details[0]").value("age: Age must be at least 3"));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should return 400 when age is too old (more than 16)")
    void updateProfile_AgeTooOld_Returns400() throws Exception {
        // Given
        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                17, // Too old
                "soccer",
                "avatar123"
        );

        // When & Then
        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details[0]").value("age: Age must be at most 16"));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should return 400 when name is too long (more than 50 characters)")
    void updateProfile_NameTooLong_Returns400() throws Exception {
        // Given
        String longName = "A".repeat(51); // 51 characters, max is 50
        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                longName,
                8,
                "soccer",
                "avatar123"
        );

        // When & Then
        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details[0]").value("name: Name must be at most 50 characters"));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should return 400 when name is empty")
    void updateProfile_EmptyName_Returns400() throws Exception {
        // Given
        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "", // Empty name
                8,
                "soccer",
                "avatar123"
        );

        // When & Then
        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details[0]").value("name: Name must not be blank"));
    }

    @Test
    @DisplayName("Should return 401 when no authentication is provided")
    void updateProfile_NoAuthentication_Returns401() throws Exception {
        // Given
        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                8,
                "soccer",
                "avatar123"
        );

        // When & Then
        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should successfully update profile with null optional fields")
    void updateProfile_WithNullOptionalFields_ReturnsUpdatedProfile() throws Exception {
        // Given
        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                8,
                null, // null interests
                null  // null avatarId
        );

        // When & Then
        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Johnny"))
                .andExpect(jsonPath("$.age").value(8))
                .andExpect(jsonPath("$.interests").isEmpty())
                .andExpect(jsonPath("$.avatarId").isEmpty());
    }

    @Test
    @WithMockUser(username = "nonexistentuser")
    @DisplayName("Should return 401 when user is not found in database")
    void updateProfile_UserNotFound_Returns401() throws Exception {
        // Given
        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                8,
                "soccer",
                "avatar123"
        );

        // When & Then
        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.details[0]").value("User not found"));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should return 400 when request contains invalid JSON")
    void updateProfile_InvalidJson_Returns400() throws Exception {
        // Given
        String invalidJson = "{ invalid json }";

        // When & Then
        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
} 