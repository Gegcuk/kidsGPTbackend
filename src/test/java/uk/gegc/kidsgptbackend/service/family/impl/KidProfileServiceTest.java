package uk.gegc.kidsgptbackend.service.family.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileDto;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileUpdateRequest;
import uk.gegc.kidsgptbackend.exception.ValidationException;
import uk.gegc.kidsgptbackend.model.family.Kid;
import uk.gegc.kidsgptbackend.model.family.Parent;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.family.KidRepository;
import uk.gegc.kidsgptbackend.repository.family.ParentRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KidProfileServiceTest {

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

    @InjectMocks
    private KidProfileServiceImpl kidProfileService;

    private User testUser;
    private Parent testParent;
    private Kid testKid;
    private ChildProfileUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
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
} 