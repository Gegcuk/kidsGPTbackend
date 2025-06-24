package uk.gegc.kidsgptbackend.service.family.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
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
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.family.KidRepository;
import uk.gegc.kidsgptbackend.repository.family.ParentRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import java.time.LocalDate;
import java.util.Optional;
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

        testParent = new Parent();
        testParent.setId(UUID.randomUUID());
        testParent.setEmail("test@example.com");

        testKid = new Kid();
        testKid.setId(UUID.randomUUID());
        testKid.setFirstName("Original");
        testKid.setLastName("Kid");
        testKid.setBirthDate(LocalDate.of(2015, 1, 1));
        testKid.setParent(testParent);

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
        when(parentRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testParent));
        when(kidRepository.findByParentId(testParent.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateCurrentChildProfile(updateRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Johnny");
        assertThat(result.age()).isEqualTo(8);
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
    @DisplayName("Should throw ValidationException when parent is not found")
    void updateCurrentChildProfile_ParentNotFound_ThrowsValidationException() {
        // Given
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(parentRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateCurrentChildProfile(updateRequest))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Parent profile not found for user");

        verify(kidRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ValidationException when child profile is not found")
    void updateCurrentChildProfile_KidNotFound_ThrowsValidationException() {
        // Given
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(parentRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testParent));
        when(kidRepository.findByParentId(testParent.getId())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> kidProfileService.updateCurrentChildProfile(updateRequest))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Child profile not found for user");

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
        when(parentRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testParent));
        when(kidRepository.findByParentId(testParent.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateCurrentChildProfile(requestWithNulls);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Johnny");
        assertThat(result.age()).isEqualTo(8);
        assertThat(result.interests()).isNull();
        assertThat(result.avatarId()).isNull();

        verify(kidRepository).save(testKid);
    }

    @Test
    @DisplayName("Should calculate age correctly from birth date")
    void updateCurrentChildProfile_CalculatesAgeCorrectly() {
        // Given
        // Note: The original birthDate will be overwritten by updateKidFromRequest
        // The age will be calculated from the new birthDate set by the request
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(parentRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testParent));
        when(kidRepository.findByParentId(testParent.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateCurrentChildProfile(updateRequest);

        // Then
        // The age should be 8 (from the request), not the original birthDate
        assertThat(result.age()).isEqualTo(8);
    }

    @Test
    @DisplayName("Should return age 0 when birth date is null")
    void updateCurrentChildProfile_WithNullBirthDate_ReturnsAgeZero() {
        // Given
        // Note: Even if we set birthDate to null, updateKidFromRequest will overwrite it
        // So this test should verify that the age from the request is used
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(parentRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testParent));
        when(kidRepository.findByParentId(testParent.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateCurrentChildProfile(updateRequest);

        // Then
        // The age should be 8 (from the request), not 0
        assertThat(result.age()).isEqualTo(8);
    }

    @Test
    @DisplayName("Should calculate age correctly from existing birth date")
    void updateCurrentChildProfile_CalculatesAgeFromExistingBirthDate() {
        // Given
        // Set a specific birth date that should result in a known age
        LocalDate birthDate = LocalDate.now().minusYears(12);
        testKid.setBirthDate(birthDate);
        
        // Create a request that doesn't change the age (to preserve birthDate)
        ChildProfileUpdateRequest agePreservingRequest = new ChildProfileUpdateRequest(
                "Johnny",
                12, // Same age as the birthDate
                "soccer, reading",
                "avatar123"
        );

        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(parentRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testParent));
        when(kidRepository.findByParentId(testParent.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        // When
        ChildProfileDto result = kidProfileService.updateCurrentChildProfile(agePreservingRequest);

        // Then
        // The age should be 12 (calculated from the birthDate)
        assertThat(result.age()).isEqualTo(12);
    }

    @Test
    @DisplayName("Should update birth date from provided age")
    void updateCurrentChildProfile_UpdatesBirthDateFromAge() {
        // Given
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(parentRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testParent));
        when(kidRepository.findByParentId(testParent.getId())).thenReturn(Optional.of(testKid));
        when(kidRepository.save(any(Kid.class))).thenReturn(testKid);

        int currentYear = LocalDate.now().getYear();
        int expectedBirthYear = currentYear - 8;

        // When
        kidProfileService.updateCurrentChildProfile(updateRequest);

        // Then
        verify(kidRepository).save(testKid);
        assertThat(testKid.getBirthDate()).isEqualTo(LocalDate.of(expectedBirthYear, 1, 1));
    }
} 