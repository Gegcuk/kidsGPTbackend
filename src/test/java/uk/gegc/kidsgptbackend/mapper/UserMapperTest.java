package uk.gegc.kidsgptbackend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gegc.kidsgptbackend.dto.user.*;
import uk.gegc.kidsgptbackend.model.family.Kid;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.CONCURRENT)
public class UserMapperTest {

    @Mock
    RoleRepository roleRepository;
    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserMapper mapper;

    public UserMapperTest() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("toDto maps entity fields correctly")
    void toDto_mapsFields() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setRoles(Set.of(new Role(1L, RoleName.ROLE_PARENT.name(), null)));

        UserDto dto = mapper.toDto(user);
        assertThat(dto.id()).isEqualTo(user.getId());
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.email()).isEqualTo("alice@example.com");
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.roles()).containsExactly(RoleName.ROLE_PARENT);
        assertThat(dto.createdAt()).isEqualTo(user.getCreatedAt());
        assertThat(dto.updatedAt()).isEqualTo(user.getUpdatedAt());
    }

    @Test
    @DisplayName("toProfileDto maps entity fields correctly")
    void toProfileDto_mapsFields() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setRoles(Set.of(new Role(1L, RoleName.ROLE_PARENT.name(), null)));

        UserProfileDto dto = mapper.toProfileDto(user);
        assertThat(dto.id()).isEqualTo(user.getId());
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.role()).isEqualTo(RoleName.ROLE_PARENT);
        assertThat(dto.createdAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    @DisplayName("toProfileDto handles user with no roles")
    void toProfileDto_noRoles_returnsNullRole() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setCreatedAt(Instant.now());
        user.setRoles(Set.of());

        UserProfileDto dto = mapper.toProfileDto(user);
        assertThat(dto.id()).isEqualTo(user.getId());
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.role()).isNull();
        assertThat(dto.createdAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    @DisplayName("Should map Kid to ChildProfileDto with correct values and age from age group")
    void toChildProfileDto_WithValidKid_ReturnsCorrectDto() {
        // Given
        Kid kid = new Kid();
        kid.setId(UUID.randomUUID());
        kid.setNickname("Johnny");
        kid.setAge(8); // Set specific age
        kid.setAgeGroup(AgeGroup.AGE_6_8);
        kid.setInterests("soccer, reading");
        kid.setAvatarId("avatar123");

        // When
        ChildProfileDto result = UserMapper.toChildProfileDto(kid);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(kid.getId());
        assertThat(result.name()).isEqualTo("Johnny");
        assertThat(result.interests()).isEqualTo("soccer, reading");
        assertThat(result.avatarId()).isEqualTo("avatar123");
        assertThat(result.ageGroup()).isEqualTo(AgeGroup.AGE_6_8);
        
        // Age should be the specific age, not calculated from age group
        assertThat(result.age()).isEqualTo(8);
    }

    @Test
    @DisplayName("Should return age from age group when specific age is null")
    void toChildProfileDto_WithNullAge_ReturnsAgeFromGroup() {
        // Given
        Kid kid = new Kid();
        kid.setId(UUID.randomUUID());
        kid.setNickname("Johnny");
        kid.setAge(null); // No specific age
        kid.setAgeGroup(AgeGroup.AGE_6_8);
        kid.setInterests("soccer");
        kid.setAvatarId("avatar123");

        // When
        ChildProfileDto result = UserMapper.toChildProfileDto(kid);

        // Then
        int expectedAge = (AgeGroup.AGE_6_8.getMinAge() + AgeGroup.AGE_6_8.getMaxAge()) / 2;
        assertThat(result.age()).isEqualTo(expectedAge);
    }

    @Test
    @DisplayName("Should return age 0 when both age and age group are null")
    void toChildProfileDto_WithNullAgeAndGroup_ReturnsAgeZero() {
        // Given
        Kid kid = new Kid();
        kid.setId(UUID.randomUUID());
        kid.setNickname("Johnny");
        kid.setAge(null);
        kid.setAgeGroup(null);
        kid.setInterests("soccer");
        kid.setAvatarId("avatar123");

        // When
        ChildProfileDto result = UserMapper.toChildProfileDto(kid);

        // Then
        assertThat(result.age()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle null optional fields in Kid entity")
    void toChildProfileDto_WithNullOptionalFields_ReturnsNullValues() {
        // Given
        Kid kid = new Kid();
        kid.setId(UUID.randomUUID());
        kid.setNickname("Johnny");
        kid.setAge(10);
        kid.setAgeGroup(AgeGroup.AGE_9_10);
        kid.setInterests(null);
        kid.setAvatarId(null);

        // When
        ChildProfileDto result = UserMapper.toChildProfileDto(kid);

        // Then
        assertThat(result.interests()).isNull();
        assertThat(result.avatarId()).isNull();
    }

    @Test
    @DisplayName("Should update Kid entity with values from ChildProfileUpdateRequest")
    void updateKidFromRequest_WithValidRequest_UpdatesKidCorrectly() {
        // Given
        Kid kid = new Kid();
        kid.setNickname("Original");
        kid.setAge(7);
        kid.setAgeGroup(AgeGroup.AGE_6_8);
        kid.setInterests("old interests");
        kid.setAvatarId("old avatar");

        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                8,
                "soccer, reading",
                "avatar123"
        );

        // When
        UserMapper.updateKidFromRequest(kid, request);

        // Then
        assertThat(kid.getNickname()).isEqualTo("Johnny");
        assertThat(kid.getAge()).isEqualTo(8); // Specific age updated
        assertThat(kid.getInterests()).isEqualTo("soccer, reading");
        assertThat(kid.getAvatarId()).isEqualTo("avatar123");
        assertThat(kid.getAgeGroup()).isEqualTo(AgeGroup.AGE_6_8); // Age 8 matches AGE_6_8 group
    }

    @Test
    @DisplayName("Should set null values when request contains null optional fields")
    void updateKidFromRequest_WithNullOptionalFields_SetsNullValues() {
        // Given
        Kid kid = new Kid();
        kid.setNickname("Original");
        kid.setAge(7);
        kid.setAgeGroup(AgeGroup.AGE_6_8);
        kid.setInterests("old interests");
        kid.setAvatarId("old avatar");

        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                8,
                null, // null interests
                null  // null avatarId
        );

        // When
        UserMapper.updateKidFromRequest(kid, request);

        // Then
        assertThat(kid.getNickname()).isEqualTo("Johnny");
        assertThat(kid.getAge()).isEqualTo(8);
        assertThat(kid.getInterests()).isNull();
        assertThat(kid.getAvatarId()).isNull();
    }

    @Test
    @DisplayName("Should update age group when valid age is provided")
    void updateKidFromRequest_WithValidAge_UpdatesAgeGroup() {
        // Given
        Kid kid = new Kid();
        kid.setNickname("Original");
        kid.setAge(7);
        kid.setAgeGroup(AgeGroup.AGE_6_8);

        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                12, // Should map to AGE_11_12
                "interests",
                "avatar"
        );

        // When
        UserMapper.updateKidFromRequest(kid, request);

        // Then
        assertThat(kid.getAge()).isEqualTo(12);
        assertThat(kid.getAgeGroup()).isEqualTo(AgeGroup.AGE_11_12);
    }

    @Test
    @DisplayName("Should keep existing age group when invalid age is provided")
    void updateKidFromRequest_WithInvalidAge_KeepsExistingAgeGroup() {
        // Given
        Kid kid = new Kid();
        kid.setNickname("Original");
        kid.setAge(7);
        kid.setAgeGroup(AgeGroup.AGE_6_8);

        ChildProfileUpdateRequest request = new ChildProfileUpdateRequest(
                "Johnny",
                25, // Invalid age - no matching age group
                "interests",
                "avatar"
        );

        // When
        UserMapper.updateKidFromRequest(kid, request);

        // Then
        assertThat(kid.getAge()).isEqualTo(25); // Age still updated
        assertThat(kid.getAgeGroup()).isEqualTo(AgeGroup.AGE_6_8); // Keeps existing age group
    }

    @Test
    @DisplayName("Should calculate age correctly from age group when no specific age")
    void toChildProfileDto_CalculatesAgeFromAgeGroup() {
        // Given
        Kid kid = new Kid();
        kid.setId(UUID.randomUUID());
        kid.setNickname("Johnny");
        kid.setAge(null); // No specific age
        kid.setAgeGroup(AgeGroup.AGE_13_14);

        // When
        ChildProfileDto result = UserMapper.toChildProfileDto(kid);

        // Then
        int expectedAge = (13 + 14) / 2; 
        assertThat(result.age()).isEqualTo(expectedAge);
    }

    @Test
    @DisplayName("Should map Kid to KidDto correctly")
    void toKidDto_WithValidKid_ReturnsCorrectDto() {
        // Given
        User kidUser = new User();
        kidUser.setUsername("johnny_kid");
        
        Kid kid = new Kid();
        kid.setId(UUID.randomUUID());
        kid.setNickname("Johnny");
        kid.setAgeGroup(AgeGroup.AGE_9_10);
        kid.setFavoriteColor("blue");
        kid.setAvatarId("avatar123");
        kid.setInterests("soccer, reading");
        kid.setUser(kidUser);

        // When
        KidDto result = UserMapper.toKidDto(kid);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(kid.getId());
        assertThat(result.nickname()).isEqualTo("Johnny");
        assertThat(result.username()).isEqualTo("johnny_kid");
        assertThat(result.ageGroup()).isEqualTo(AgeGroup.AGE_9_10);
        assertThat(result.favoriteColor()).isEqualTo("blue");
        assertThat(result.avatarId()).isEqualTo("avatar123");
        assertThat(result.interests()).isEqualTo("soccer, reading");
    }

    @Test
    @DisplayName("Should handle null optional fields in KidDto mapping")
    void toKidDto_WithNullOptionalFields_ReturnsNullValues() {
        // Given
        User kidUser = new User();
        kidUser.setUsername("johnny_kid");
        
        Kid kid = new Kid();
        kid.setId(UUID.randomUUID());
        kid.setNickname("Johnny");
        kid.setAgeGroup(AgeGroup.AGE_9_10);
        kid.setFavoriteColor(null);
        kid.setAvatarId(null);
        kid.setInterests(null);
        kid.setUser(kidUser);

        // When
        KidDto result = UserMapper.toKidDto(kid);

        // Then
        assertThat(result.favoriteColor()).isNull();
        assertThat(result.avatarId()).isNull();
        assertThat(result.interests()).isNull();
    }
}
