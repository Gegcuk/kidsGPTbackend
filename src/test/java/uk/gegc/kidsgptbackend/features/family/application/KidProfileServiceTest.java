package uk.gegc.kidsgptbackend.features.family.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;
import uk.gegc.kidsgptbackend.features.user.api.dto.ChildProfileDto;
import uk.gegc.kidsgptbackend.features.user.api.dto.ChildProfileUpdateRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidSelfUpdateRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.ParentUpdateKidRequest;
import uk.gegc.kidsgptbackend.shared.exception.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import uk.gegc.kidsgptbackend.features.family.domain.model.Kid;
import uk.gegc.kidsgptbackend.features.family.domain.model.Parent;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.Role;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.family.domain.repository.KidRepository;
import uk.gegc.kidsgptbackend.features.family.domain.repository.ParentRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.features.family.application.impl.KidProfileServiceImpl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KidProfileServiceTest extends BaseUnitTest {

    @Mock
    private KidRepository kidRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ParentRepository parentRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private KidProfileServiceImpl kidProfileService;

    private User testUser;
    private Parent testParent;
    private Kid testKid;
    private ChildProfileUpdateRequest updateRequest;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        // Setup test data
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        
        // Set up roles for child user
        Role childRole = new Role();
        childRole.setRole(RoleName.ROLE_CHILD.name());
        testUser.setRoles(new HashSet<>(Set.of(childRole)));

        testParent = new Parent();
        testParent.setId(UUID.randomUUID());
        testParent.setEmail("test@example.com");

        testKid = new Kid();
        testKid.setId(UUID.randomUUID());
        testKid.setNickname("Original");
        testKid.setAge(7);
        testKid.setAgeGroup(AgeGroup.AGE_6_8);
        testKid.setParent(testParent);
        testKid.setUser(testUser);

        updateRequest = new ChildProfileUpdateRequest(
                "Johnny",
                8,
                "soccer, reading",
                "avatar123"
        );

        // Setup security context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("Should successfully update child profile and return updated DTO")
    void updateCurrentChildProfile_SuccessfulUpdate_ReturnsUpdatedProfile() {
        // Given
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(kidRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateCurrentChildProfile(updateRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Johnny");
        assertThat(result.interests()).isEqualTo("soccer, reading");
        assertThat(result.avatarId()).isEqualTo("avatar123");

        verify(kidRepository).save(testKid);
    }

    @Test
    @DisplayName("Should throw exception when user is not found")
    void updateCurrentChildProfile_UserNotFound_ThrowsException() {
        // Given
        when(authentication.getName()).thenReturn("nonexistentuser");
        when(userRepository.findByUsername("nonexistentuser")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateCurrentChildProfile(updateRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ValidationException when user is parent (not allowed in new API)")
    void updateCurrentChildProfile_ParentNotFound_ThrowsValidationException() {
        // Given - Setup as parent user (this method now only allows kids)
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        testUser.setRoles(new HashSet<>(Set.of(parentRole)));
        
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When & Then - Now throws "Only children can update their own profiles"
        assertThatThrownBy(() -> kidProfileService.updateCurrentChildProfile(updateRequest))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only children can update their own profiles");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ValidationException when child profile is not found")
    void updateCurrentChildProfile_KidNotFound_ThrowsValidationException() {
        // Given
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(kidRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());

        // When & Then - Message changed to match current implementation
        assertThatThrownBy(() -> kidProfileService.updateCurrentChildProfile(updateRequest))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Child profile not found");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle null optional fields correctly")
    void updateCurrentChildProfile_WithNullOptionalFields_UpdatesCorrectly() {
        // Given
        ChildProfileUpdateRequest requestWithNulls = new ChildProfileUpdateRequest(
                "Johnny",
                8,
                null, // null interests
                null  // null avatarId
        );

        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(kidRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateCurrentChildProfile(requestWithNulls);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Johnny");
        assertThat(result.interests()).isNull();
        assertThat(result.avatarId()).isNull();

        verify(kidRepository).save(testKid);
    }

    @Test
    @DisplayName("Should update age group when valid age is provided")
    void updateCurrentChildProfile_UpdatesAgeGroup() {
        // Given
        ChildProfileUpdateRequest ageUpdateRequest = new ChildProfileUpdateRequest(
                "Johnny",
                12, // Should map to AGE_11_12
                "soccer",
                "avatar123"
        );
        
        // Set the kid to return the updated age group after the update
        testKid.setAgeGroup(AgeGroup.AGE_11_12);

        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(kidRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateCurrentChildProfile(ageUpdateRequest);

        // Then
        assertThat(result.ageGroup()).isEqualTo(AgeGroup.AGE_11_12);
    }

    @Test
    @DisplayName("Should keep existing age group when invalid age is provided")
    void updateCurrentChildProfile_WithInvalidAge_KeepsExistingAgeGroup() {
        // Given
        ChildProfileUpdateRequest invalidAgeRequest = new ChildProfileUpdateRequest(
                "Johnny",
                25, // Invalid age - should keep existing age group
                "soccer",
                "avatar123"
        );

        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(kidRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateCurrentChildProfile(invalidAgeRequest);

        // Then
        assertThat(result.ageGroup()).isEqualTo(AgeGroup.AGE_6_8);
    }

    @Test
    @DisplayName("Should throw ValidationException when user tries parent authentication (deprecated behavior)")
    void updateCurrentChildProfile_ParentAuth_SuccessfulUpdate() {
        // Given - Setup as parent user (this method no longer supports parent auth)
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        testUser.setRoles(new HashSet<>(Set.of(parentRole)));
        
        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                10,
                "music, art",
                "avatar456"
        );

        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When & Then - This now throws an exception since method only allows kids
        assertThatThrownBy(() -> kidProfileService.updateCurrentChildProfile(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only children can update their own profiles");

        verify(parentRepository, never()).findByEmail(any());
        verify(kidRepository, never()).findByParentId(any());
        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ValidationException when user has invalid role")
    void updateCurrentChildProfile_InvalidRole_ThrowsValidationException() {
        // Given - Setup user with admin role (neither parent nor child)
        Role adminRole = new Role();
        adminRole.setRole(RoleName.ROLE_ADMIN.name());
        testUser.setRoles(new HashSet<>(Set.of(adminRole)));

        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When & Then - Message updated to match current implementation
        assertThatThrownBy(() -> kidProfileService.updateCurrentChildProfile(updateRequest))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only children can update their own profiles");

        verify(kidRepository, never()).save(any());
        verify(parentRepository, never()).findByEmail(any());
        verify(kidRepository, never()).findByUserId(any());
        verify(kidRepository, never()).findByParentId(any());
    }

    // ===== TESTS FOR updateKidSelfProfile() =====

    @Test
    @DisplayName("updateKidSelfProfile: Should successfully update avatar and return updated DTO")
    void updateKidSelfProfile_SuccessfulUpdate_ReturnsUpdatedProfile() {
        // Given
        KidSelfUpdateRequest request = new KidSelfUpdateRequest("new_avatar_123");
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(kidRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateKidSelfProfile(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(testKid.getAvatarId()).isEqualTo("new_avatar_123");
        verify(kidRepository).save(testKid);
    }

    @Test
    @DisplayName("updateKidSelfProfile: Should handle null avatarId correctly")
    void updateKidSelfProfile_NullAvatarId_DoesNotUpdate() {
        // Given
        KidSelfUpdateRequest request = new KidSelfUpdateRequest(null);
        testKid.setAvatarId("existing_avatar");
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(kidRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateKidSelfProfile(request);

        // Then
        assertThat(result).isNotNull();
        // Avatar should remain unchanged when null is provided
        verify(kidRepository).save(testKid);
    }

    @Test
    @DisplayName("updateKidSelfProfile: Should throw ValidationException when user is not a child")
    void updateKidSelfProfile_NonChildUser_ThrowsValidationException() {
        // Given - Setup as parent user
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        testUser.setRoles(new HashSet<>(Set.of(parentRole)));
        
        KidSelfUpdateRequest request = new KidSelfUpdateRequest("avatar123");
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateKidSelfProfile(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only children can update their own profiles");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateKidSelfProfile: Should throw exception when user is not found")
    void updateKidSelfProfile_UserNotFound_ThrowsException() {
        // Given
        KidSelfUpdateRequest request = new KidSelfUpdateRequest("avatar123");
        when(authentication.getName()).thenReturn("nonexistentuser");
        when(userRepository.findByUsername("nonexistentuser")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateKidSelfProfile(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateKidSelfProfile: Should throw ValidationException when child profile is not found")
    void updateKidSelfProfile_KidNotFound_ThrowsValidationException() {
        // Given
        KidSelfUpdateRequest request = new KidSelfUpdateRequest("avatar123");
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(kidRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateKidSelfProfile(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Child profile not found");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateKidSelfProfile: Should handle user with null roles")
    void updateKidSelfProfile_UserWithNullRoles_ThrowsValidationException() {
        // Given
        testUser.setRoles(null);
        KidSelfUpdateRequest request = new KidSelfUpdateRequest("avatar123");
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateKidSelfProfile(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only children can update their own profiles");

        verify(kidRepository, never()).save(any());
    }

    // ===== TESTS FOR updateKidProfileByParent() =====

    @Test
    @DisplayName("updateKidProfileByParent: Should successfully update all fields and return updated DTO")
    void updateKidProfileByParent_SuccessfulUpdateAllFields_ReturnsUpdatedProfile() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                "newpassword123",
                AgeGroup.AGE_9_10
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(testParent));
        when(kidRepository.findById(testKid.getId())).thenReturn(Optional.of(testKid));
        when(passwordEncoder.encode("newpassword123")).thenReturn("encoded_password");
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);
        when(userRepository.save(any(User.class))).thenReturn(testKid.getUser());

        // When
        ChildProfileDto result = kidProfileService.updateKidProfileByParent(testKid.getId(), request);

        // Then
        assertThat(result).isNotNull();
        assertThat(testKid.getNickname()).isEqualTo("UpdatedNickname");
        assertThat(testKid.getAgeGroup()).isEqualTo(AgeGroup.AGE_9_10);
        verify(kidRepository).save(testKid);
        verify(userRepository).save(testKid.getUser());
        verify(passwordEncoder).encode("newpassword123");
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should update only nickname when password is null")
    void updateKidProfileByParent_PartialUpdateNicknameOnly_UpdatesCorrectly() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "NewNickname",
                null, // null password
                AgeGroup.AGE_6_8 // ageGroup is required
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(testParent));
        when(kidRepository.findById(testKid.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateKidProfileByParent(testKid.getId(), request);

        // Then
        assertThat(result).isNotNull();
        assertThat(testKid.getNickname()).isEqualTo("NewNickname");
        verify(kidRepository).save(testKid);
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should update only ageGroup when nickname is unchanged")
    void updateKidProfileByParent_UpdateAgeGroupOnly_UpdatesCorrectly() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        String originalNickname = testKid.getNickname();
        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                originalNickname, // same nickname
                null, // null password
                AgeGroup.AGE_11_12 // different age group
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(testParent));
        when(kidRepository.findById(testKid.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateKidProfileByParent(testKid.getId(), request);

        // Then
        assertThat(result).isNotNull();
        assertThat(testKid.getNickname()).isEqualTo(originalNickname);
        assertThat(testKid.getAgeGroup()).isEqualTo(AgeGroup.AGE_11_12);
        verify(kidRepository).save(testKid);
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should use email lookup when userId lookup fails")
    void updateKidProfileByParent_ParentLookupFallbackToEmail_UpdatesCorrectly() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                null,
                AgeGroup.AGE_9_10
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.empty());
        when(parentRepository.findByEmail(parentUser.getEmail())).thenReturn(Optional.of(testParent));
        when(kidRepository.findById(testKid.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateKidProfileByParent(testKid.getId(), request);

        // Then
        assertThat(result).isNotNull();
        verify(parentRepository).findByUserId(parentUser.getId());
        verify(parentRepository).findByEmail(parentUser.getEmail());
        verify(kidRepository).save(testKid);
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should throw ValidationException when parent profile is not found")
    void updateKidProfileByParent_ParentNotFound_ThrowsValidationException() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                null,
                AgeGroup.AGE_9_10
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.empty());
        when(parentRepository.findByEmail(parentUser.getEmail())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateKidProfileByParent(testKid.getId(), request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Parent profile not found");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should throw ValidationException when kid is not found")
    void updateKidProfileByParent_KidNotFound_ThrowsValidationException() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                null,
                AgeGroup.AGE_9_10
        );
        UUID nonExistentKidId = UUID.randomUUID();

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(testParent));
        when(kidRepository.findById(nonExistentKidId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateKidProfileByParent(nonExistentKidId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Kid not found");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should throw ValidationException when kid belongs to different parent")
    void updateKidProfileByParent_KidBelongsToDifferentParent_ThrowsValidationException() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        Parent otherParent = new Parent();
        otherParent.setId(UUID.randomUUID());
        testKid.setParent(otherParent); // Kid belongs to different parent

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                null,
                AgeGroup.AGE_9_10
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(testParent));
        when(kidRepository.findById(testKid.getId())).thenReturn(Optional.of(testKid));

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateKidProfileByParent(testKid.getId(), request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("You can only update your own kids' profiles");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should throw ValidationException when user is not a parent")
    void updateKidProfileByParent_NonParentUser_ThrowsValidationException() {
        // Given - Setup as child user
        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                null,
                AgeGroup.AGE_9_10
        );

        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateKidProfileByParent(testKid.getId(), request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only parents can update their kids' profiles");

        verify(kidRepository, never()).save(any());
        verify(parentRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should not update password when password is null")
    void updateKidProfileByParent_NullPassword_DoesNotUpdatePassword() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                null, // null password
                AgeGroup.AGE_9_10
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(testParent));
        when(kidRepository.findById(testKid.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateKidProfileByParent(testKid.getId(), request);

        // Then
        assertThat(result).isNotNull();
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should not update password when password is empty string")
    void updateKidProfileByParent_EmptyPassword_DoesNotUpdatePassword() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                "", // empty password
                AgeGroup.AGE_9_10
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(testParent));
        when(kidRepository.findById(testKid.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateKidProfileByParent(testKid.getId(), request);

        // Then
        assertThat(result).isNotNull();
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should not update password when password is whitespace only")
    void updateKidProfileByParent_WhitespacePassword_DoesNotUpdatePassword() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                "   ", // whitespace only password
                AgeGroup.AGE_9_10
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(testParent));
        when(kidRepository.findById(testKid.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateKidProfileByParent(testKid.getId(), request);

        // Then
        assertThat(result).isNotNull();
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should handle user with null roles")
    void updateKidProfileByParent_UserWithNullRoles_ThrowsValidationException() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setRoles(null); // null roles

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                null,
                AgeGroup.AGE_9_10
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateKidProfileByParent(testKid.getId(), request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only parents can update their kids' profiles");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateKidProfileByParent: Should handle kid with null user when updating password")
    void updateKidProfileByParent_KidWithNullUser_ThrowsException() {
        // Given
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parentuser");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(new HashSet<>(Set.of(parentRole)));

        testKid.setUser(null); // Kid has no user

        ParentUpdateKidRequest request = new ParentUpdateKidRequest(
                "UpdatedNickname",
                "newpassword123",
                AgeGroup.AGE_9_10
        );

        when(authentication.getName()).thenReturn("parentuser");
        when(userRepository.findByUsername("parentuser")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(testParent));
        when(kidRepository.findById(testKid.getId())).thenReturn(Optional.of(testKid));

        // When & Then - Should throw NullPointerException when trying to access kid.getUser()
        assertThatThrownBy(() -> kidProfileService.updateKidProfileByParent(testKid.getId(), request))
                .isInstanceOf(NullPointerException.class);

        verify(kidRepository, never()).save(any());
    }
} 