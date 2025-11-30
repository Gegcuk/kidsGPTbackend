package uk.gegc.kidsgptbackend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidSelfUpdateRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.ParentUpdateKidRequest;
import uk.gegc.kidsgptbackend.features.family.domain.model.Kid;
import uk.gegc.kidsgptbackend.features.family.domain.model.Parent;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.Role;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.family.domain.repository.KidRepository;
import uk.gegc.kidsgptbackend.features.family.domain.repository.ParentRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private KidRepository kidRepository;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User testKidUser;
    private User testParentUser;
    private Parent testParent;
    private Kid testKid;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        setupTestData();
    }

    private void setupTestData() {
        // Get roles (already created by BaseIntegrationTest.setUp())
        Role childRole = roleRepository.findByRole(RoleName.ROLE_CHILD.name()).orElseThrow();
        Role parentRole = roleRepository.findByRole(RoleName.ROLE_PARENT.name()).orElseThrow();

        // Create parent user
        testParentUser = new User();
        testParentUser.setUsername("testparent");
        testParentUser.setEmail("parent@example.com");
        testParentUser.setHashedPassword("hashedPassword");
        testParentUser.setRoles(new HashSet<>(Set.of(parentRole)));
        testParentUser.setActive(true);
        testParentUser = userRepository.save(testParentUser);

        // Create parent profile
        testParent = new Parent();
        testParent.setFirstName("Test");
        testParent.setLastName("Parent");
        testParent.setEmail("parent@example.com");
        testParent = parentRepository.save(testParent);

        // Create kid user
        testKidUser = new User();
        testKidUser.setUsername("testkid");
        testKidUser.setEmail("testkid@kid.local");
        testKidUser.setHashedPassword("hashedPassword");
        testKidUser.setRoles(new HashSet<>(Set.of(childRole)));
        testKidUser.setActive(true);
        testKidUser = userRepository.save(testKidUser);

        // Create kid
        testKid = new Kid();
        testKid.setNickname("TestKid");
        testKid.setAge(7);
        testKid.setAgeGroup(AgeGroup.AGE_6_8);
        testKid.setParent(testParent);
        testKid.setUser(testKidUser);
        testKid = kidRepository.save(testKid);
    }

    // ===== TESTS FOR KIDS UPDATING THEIR OWN AVATAR =====

    @Test
    @WithMockUser(username = "testkid", roles = {"CHILD"})
    @DisplayName("Should successfully update kid's own avatar")
    void updateOwnProfile_ValidAvatarUpdate_ReturnsUpdatedProfile() throws Exception {
        KidSelfUpdateRequest request = new KidSelfUpdateRequest("new_avatar_123");

        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarId").value("new_avatar_123"))
                .andExpect(jsonPath("$.name").value("TestKid")) // Name should remain unchanged
                .andExpect(jsonPath("$.age").value(7)); // Age should remain unchanged
    }

    @Test
    @WithMockUser(username = "testkid", roles = {"CHILD"})
    @DisplayName("Should successfully update avatar to null")
    void updateOwnProfile_NullAvatar_ReturnsUpdatedProfile() throws Exception {
        KidSelfUpdateRequest request = new KidSelfUpdateRequest(null);

        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarId").isEmpty());
    }

    @Test
    @WithMockUser(username = "nonexistentkid", roles = {"CHILD"})
    @DisplayName("Should return error when kid user not found")
    void updateOwnProfile_KidUserNotFound_ReturnsError() throws Exception {
        KidSelfUpdateRequest request = new KidSelfUpdateRequest("avatar123");

        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    @DisplayName("Should return 403 when parent tries to use kid endpoint")
    void updateOwnProfile_ParentUser_Returns403() throws Exception {
        KidSelfUpdateRequest request = new KidSelfUpdateRequest("avatar123");

        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 401 when no authentication provided")
    void updateOwnProfile_NoAuth_Returns401() throws Exception {
        KidSelfUpdateRequest request = new KidSelfUpdateRequest("avatar123");

        mockMvc.perform(patch("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ===== TESTS FOR PARENTS UPDATING KID PROFILES =====

    @Test
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    @DisplayName("Should successfully update kid profile by parent")
    void updateKidProfile_ValidParentUpdate_ReturnsUpdatedProfile() throws Exception {
        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedKidName", 
                "newpassword123", 
                AgeGroup.AGE_9_10
        );

        mockMvc.perform(patch("/api/v1/profile/kid/" + testKid.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("UpdatedKidName"))
                .andExpect(jsonPath("$.ageGroup").value("AGE_9_10"));
    }

    @Test
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    @DisplayName("Should return 400 when nickname is too short")
    void updateKidProfile_NicknameTooShort_Returns400() throws Exception {
        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "A", // Too short (min 2 characters)
                null, 
                AgeGroup.AGE_9_10
        );

        mockMvc.perform(patch("/api/v1/profile/kid/" + testKid.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    @DisplayName("Should return 400 when nickname is too long")
    void updateKidProfile_NicknameTooLong_Returns400() throws Exception {
        String longNickname = "A".repeat(51); // Too long (max 50 characters)
        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                longNickname,
                null, 
                AgeGroup.AGE_9_10
        );

        mockMvc.perform(patch("/api/v1/profile/kid/" + testKid.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    @DisplayName("Should return 400 when password is too short")
    void updateKidProfile_PasswordTooShort_Returns400() throws Exception {
        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "ValidName",
                "123", // Too short (min 6 characters)
                AgeGroup.AGE_9_10
        );

        mockMvc.perform(patch("/api/v1/profile/kid/" + testKid.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @WithMockUser(username = "testkid", roles = {"CHILD"})
    @DisplayName("Should return 403 when kid tries to use parent endpoint")
    void updateKidProfile_KidUser_Returns403() throws Exception {
        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedName", 
                null, 
                AgeGroup.AGE_9_10
        );

        mockMvc.perform(patch("/api/v1/profile/kid/" + testKid.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testparent", roles = {"PARENT"})
    @DisplayName("Should return 400 when kid ID is invalid")
    void updateKidProfile_InvalidKidId_Returns400() throws Exception {
        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedName", 
                null, 
                AgeGroup.AGE_9_10
        );

        mockMvc.perform(patch("/api/v1/profile/kid/00000000-0000-0000-0000-000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
} 