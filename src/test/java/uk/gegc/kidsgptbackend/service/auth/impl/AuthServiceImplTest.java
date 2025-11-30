package uk.gegc.kidsgptbackend.service.auth.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.dto.auth.AuthTokensResponse;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidDto;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidRegistrationRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.RegisterUserRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.UserDto;
import uk.gegc.kidsgptbackend.shared.exception.UnauthorizedException;
import uk.gegc.kidsgptbackend.shared.exception.ValidationException;
import uk.gegc.kidsgptbackend.features.user.infra.mapping.UserMapper;
import uk.gegc.kidsgptbackend.features.family.domain.model.Kid;
import uk.gegc.kidsgptbackend.features.family.domain.model.Parent;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.Role;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.family.domain.repository.KidRepository;
import uk.gegc.kidsgptbackend.features.family.domain.repository.ParentRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.RoleRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.shared.security.JwtTokenProvider;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
public class AuthServiceImplTest {

    @Mock
    UserRepository userRepository;
    @Mock
    RoleRepository roleRepository;
    @Mock
    ParentRepository parentRepository;
    @Mock
    KidRepository kidRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    UserMapper userMapper;
    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("register: duplicate username throws 409")
    void register_duplicateUsername_throws() {
        RegisterUserRequest req = new RegisterUserRequest("bob", "bob@example.com", "pass");
        when(userRepository.existsByUsername("bob")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(String.valueOf(HttpStatus.CONFLICT.value()));
    }

    @Test
    @DisplayName("register: duplicate email throws 409")
    void register_duplicateEmail_throws() {
        RegisterUserRequest req = new RegisterUserRequest("bob", "bob@example.com", "pass");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(String.valueOf(HttpStatus.CONFLICT.value()));
    }

    @Test
    @DisplayName("register: role missing throws IllegalStateException")
    void register_roleMissing_throws() {
        RegisterUserRequest req = new RegisterUserRequest("bob", "bob@example.com", "pass");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(roleRepository.findByRole(RoleName.ROLE_PARENT.name())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("register: success returns mapped dto")
    void register_success() {
        RegisterUserRequest req = new RegisterUserRequest("bob", "bob@example.com", "pass");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        Role role = new Role(1L, RoleName.ROLE_PARENT.name(), null);
        when(roleRepository.findByRole(RoleName.ROLE_PARENT.name())).thenReturn(Optional.of(role));
        User saved = new User();
        saved.setUsername("bob");
        saved.setEmail("bob@example.com");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        UserDto dto = new UserDto(null, "bob", "bob@example.com", true, Set.of(RoleName.ROLE_PARENT), null, null, null);
        when(userMapper.toDto(saved)).thenReturn(dto);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");

        UserDto result = authService.register(req);
        assertThat(result).isSameAs(dto);
        verify(userRepository).save(any(User.class));
    }

    // Kid Registration Tests
    @Test
    @DisplayName("registerKid: successful registration by parent")
    void registerKid_success() {
        // Given
        KidRegistrationRequest request = new KidRegistrationRequest("Johnny", "password123", AgeGroup.AGE_6_8);
        
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername("parent");
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setEmail("parent@example.com");
        
        Role childRole = new Role();
        childRole.setRole(RoleName.ROLE_CHILD.name());
        
        User kidUser = new User();
        kidUser.setId(UUID.randomUUID());
        kidUser.setUsername("johnny_kid");
        
        Kid savedKid = new Kid();
        savedKid.setId(UUID.randomUUID());
        savedKid.setNickname("Johnny");
        savedKid.setUser(kidUser);
        savedKid.setAgeGroup(AgeGroup.AGE_6_8);
        
        KidDto expectedDto = new KidDto(savedKid.getId(), "Johnny", "johnny_kid", AgeGroup.AGE_6_8, null, null, null);
        
        // When
        when(userRepository.findByUsername("parent")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        when(userRepository.existsByUsername("johnny_kid")).thenReturn(false);
        when(roleRepository.findByRole(RoleName.ROLE_CHILD.name())).thenReturn(Optional.of(childRole));
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(kidUser);
        when(kidRepository.save(any(Kid.class))).thenReturn(savedKid);
        
        KidDto result = authService.registerKid(request, "parent");
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.nickname()).isEqualTo("Johnny");
        assertThat(result.username()).isEqualTo("johnny_kid");
        assertThat(result.ageGroup()).isEqualTo(AgeGroup.AGE_6_8);
        
        verify(userRepository).save(any(User.class));
        verify(kidRepository).save(any(Kid.class));
    }

    @Test
    @DisplayName("registerKid: throws ValidationException when parent user not found")
    void registerKid_parentUserNotFound_throws() {
        KidRegistrationRequest request = new KidRegistrationRequest("Johnny", "password123", AgeGroup.AGE_6_8);
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registerKid(request, "nonexistent"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Parent user not found");
    }

    @Test
    @DisplayName("registerKid: throws ValidationException when user is not a parent")
    void registerKid_userNotParent_throws() {
        KidRegistrationRequest request = new KidRegistrationRequest("Johnny", "password123", AgeGroup.AGE_6_8);
        
        User nonParentUser = new User();
        nonParentUser.setUsername("user");
        Role childRole = new Role();
        childRole.setRole(RoleName.ROLE_CHILD.name());
        nonParentUser.setRoles(Set.of(childRole));
        
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(nonParentUser));

        assertThatThrownBy(() -> authService.registerKid(request, "user"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only parents can create kid accounts");
    }

    @Test
    @DisplayName("registerKid: throws ValidationException when parent profile not found")
    void registerKid_parentProfileNotFound_throws() {
        KidRegistrationRequest request = new KidRegistrationRequest("Johnny", "password123", AgeGroup.AGE_6_8);
        
        User parentUser = new User();
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        when(userRepository.findByUsername("parent")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registerKid(request, "parent"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Parent profile not found");
    }

    @Test
    @DisplayName("registerKid: throws IllegalStateException when ROLE_CHILD not found")
    void registerKid_childRoleNotFound_throws() {
        KidRegistrationRequest request = new KidRegistrationRequest("Johnny", "password123", AgeGroup.AGE_6_8);
        
        User parentUser = new User();
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        Parent parent = new Parent();
        parent.setEmail("parent@example.com");
        
        when(userRepository.findByUsername("parent")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(roleRepository.findByRole(RoleName.ROLE_CHILD.name())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registerKid(request, "parent"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROLE_CHILD not found");
    }

    @Test
    @DisplayName("registerKid: generates unique username when collision occurs")
    void registerKid_usernameCollision_generatesUnique() {
        // Given
        KidRegistrationRequest request = new KidRegistrationRequest("Johnny", "password123", AgeGroup.AGE_6_8);
        
        User parentUser = new User();
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        Parent parent = new Parent();
        parent.setEmail("parent@example.com");
        
        Role childRole = new Role();
        childRole.setRole(RoleName.ROLE_CHILD.name());
        
        User kidUser = new User();
        kidUser.setUsername("johnny_kid1");
        
        Kid savedKid = new Kid();
        savedKid.setUser(kidUser);
        savedKid.setNickname("Johnny");
        
        // When
        when(userRepository.findByUsername("parent")).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        when(userRepository.existsByUsername("johnny_kid")).thenReturn(true); // First collision
        when(userRepository.existsByUsername("johnny_kid1")).thenReturn(false); // Success
        when(roleRepository.findByRole(RoleName.ROLE_CHILD.name())).thenReturn(Optional.of(childRole));
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(kidUser);
        when(kidRepository.save(any(Kid.class))).thenReturn(savedKid);
        
        KidDto result = authService.registerKid(request, "parent");
        
        // Then
        assertThat(result.username()).isEqualTo("johnny_kid1");
        verify(userRepository, atLeast(2)).existsByUsername(anyString());
    }

    @Test
    @DisplayName("login: valid credentials return tokens")
    void login_success() {
        AuthLoginRequest req = new AuthLoginRequest("bob", "pass");
        Authentication auth = new UsernamePasswordAuthenticationToken("bob", "pass",
                Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateAccessToken(auth)).thenReturn("a");
        when(jwtTokenProvider.generateRefreshToken(auth)).thenReturn("r");

        AuthTokensResponse tokens = authService.login(req);
        assertThat(tokens.accessToken()).isEqualTo("a");
        assertThat(tokens.refreshToken()).isEqualTo("r");
    }

    @Test
    @DisplayName("login: authentication exception throws UnauthorizedException")
    void login_authenticationError_throws() {
        AuthLoginRequest req = new AuthLoginRequest("bob", "pass");
        when(authenticationManager.authenticate(any())).thenThrow(mock(AuthenticationException.class));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("login: unexpected exception throws 401 ResponseStatusException")
    void login_unexpectedError_throws() {
        AuthLoginRequest req = new AuthLoginRequest("bob", "pass");
        when(authenticationManager.authenticate(any())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(String.valueOf(HttpStatus.UNAUTHORIZED.value()));
    }

    // Get Parent Kids Tests
    @Test
    @DisplayName("getParentKids: successful retrieval with multiple kids")
    void getParentKids_success_multipleKids() {
        // Given
        String parentUsername = "parent123";
        UUID parentId = UUID.randomUUID();
        
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername(parentUsername);
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setEmail("parent@example.com");
        
        User kidUser1 = new User();
        kidUser1.setId(UUID.randomUUID());
        kidUser1.setUsername("emma_kid");
        
        User kidUser2 = new User();
        kidUser2.setId(UUID.randomUUID());
        kidUser2.setUsername("liam_kid");
        
        Kid kid1 = new Kid();
        kid1.setId(UUID.randomUUID());
        kid1.setNickname("Emma");
        kid1.setAgeGroup(AgeGroup.AGE_6_8);
        kid1.setUser(kidUser1);
        kid1.setParent(parent);
        
        Kid kid2 = new Kid();
        kid2.setId(UUID.randomUUID());
        kid2.setNickname("Liam");
        kid2.setAgeGroup(AgeGroup.AGE_9_10);
        kid2.setUser(kidUser2);
        kid2.setParent(parent);
        
        List<Kid> kids = Arrays.asList(kid1, kid2);
        
        // When
        when(userRepository.findByUsername(parentUsername)).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        when(kidRepository.findAllByParentId(parentId)).thenReturn(kids);
        
        List<KidDto> result = authService.getParentKids(parentUsername);
        
        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).nickname()).isEqualTo("Emma");
        assertThat(result.get(0).username()).isEqualTo("emma_kid");
        assertThat(result.get(0).ageGroup()).isEqualTo(AgeGroup.AGE_6_8);
        assertThat(result.get(1).nickname()).isEqualTo("Liam");
        assertThat(result.get(1).username()).isEqualTo("liam_kid");
        assertThat(result.get(1).ageGroup()).isEqualTo(AgeGroup.AGE_9_10);
        
        verify(userRepository).findByUsername(parentUsername);
        verify(parentRepository).findByEmail("parent@example.com");
        verify(kidRepository).findAllByParentId(parentId);
    }

    @Test
    @DisplayName("getParentKids: successful retrieval with no kids")
    void getParentKids_success_noKids() {
        // Given
        String parentUsername = "parent123";
        UUID parentId = UUID.randomUUID();
        
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername(parentUsername);
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setEmail("parent@example.com");
        
        // When
        when(userRepository.findByUsername(parentUsername)).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        when(kidRepository.findAllByParentId(parentId)).thenReturn(Collections.emptyList());
        
        List<KidDto> result = authService.getParentKids(parentUsername);
        
        // Then
        assertThat(result).isEmpty();
        
        verify(userRepository).findByUsername(parentUsername);
        verify(parentRepository).findByEmail("parent@example.com");
        verify(kidRepository).findAllByParentId(parentId);
    }

    @Test
    @DisplayName("getParentKids: throws ValidationException when parent user not found")
    void getParentKids_parentUserNotFound_throws() {
        // Given
        String parentUsername = "nonexistent";
        
        // When
        when(userRepository.findByUsername(parentUsername)).thenReturn(Optional.empty());
        
        // Then
        assertThatThrownBy(() -> authService.getParentKids(parentUsername))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Parent user not found");
        
        verify(userRepository).findByUsername(parentUsername);
        verifyNoInteractions(parentRepository, kidRepository);
    }

    @Test
    @DisplayName("getParentKids: throws ValidationException when user is not a parent")
    void getParentKids_userNotParent_throws() {
        // Given
        String username = "childuser";
        
        User childUser = new User();
        childUser.setId(UUID.randomUUID());
        childUser.setUsername(username);
        childUser.setEmail("child@example.com");
        Role childRole = new Role();
        childRole.setRole(RoleName.ROLE_CHILD.name());
        childUser.setRoles(Set.of(childRole));
        
        // When
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(childUser));
        
        // Then
        assertThatThrownBy(() -> authService.getParentKids(username))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only parents can retrieve their kids");
        
        verify(userRepository).findByUsername(username);
        verifyNoInteractions(parentRepository, kidRepository);
    }

    @Test
    @DisplayName("getParentKids: throws ValidationException when parent profile not found")
    void getParentKids_parentProfileNotFound_throws() {
        // Given
        String parentUsername = "parent123";
        
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername(parentUsername);
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        // When
        when(userRepository.findByUsername(parentUsername)).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.empty());
        
        // Then
        assertThatThrownBy(() -> authService.getParentKids(parentUsername))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Parent profile not found");
        
        verify(userRepository).findByUsername(parentUsername);
        verify(parentRepository).findByEmail("parent@example.com");
        verifyNoInteractions(kidRepository);
    }

    @Test
    @DisplayName("deleteKid: successful deletion by parent")
    void deleteKid_success() {
        // Given
        UUID kidId = UUID.randomUUID();
        String parentUsername = "parent123";
        UUID parentId = UUID.randomUUID();
        UUID kidUserId = UUID.randomUUID();
        
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setUsername(parentUsername);
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setEmail("parent@example.com");
        
        User kidUser = new User();
        kidUser.setId(kidUserId);
        kidUser.setUsername("emma_kid");
        
        Kid kid = new Kid();
        kid.setId(kidId);
        kid.setNickname("Emma");
        kid.setUser(kidUser);
        kid.setParent(parent);
        
        // When
        when(userRepository.findByUsername(parentUsername)).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        when(kidRepository.findById(kidId)).thenReturn(Optional.of(kid));
        
        authService.deleteKid(kidId, parentUsername);
        
        // Then
        verify(kidRepository).delete(kid);
        verify(userRepository).delete(kidUser);
    }

    @Test
    @DisplayName("deleteKid: throws ValidationException when parent user not found")
    void deleteKid_parentUserNotFound_throws() {
        UUID kidId = UUID.randomUUID();
        String parentUsername = "nonexistent";
        
        when(userRepository.findByUsername(parentUsername)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.deleteKid(kidId, parentUsername))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Parent user not found");
    }

    @Test
    @DisplayName("deleteKid: throws ValidationException when user is not a parent")
    void deleteKid_userNotParent_throws() {
        UUID kidId = UUID.randomUUID();
        String parentUsername = "user";
        
        User nonParentUser = new User();
        nonParentUser.setUsername("user");
        Role childRole = new Role();
        childRole.setRole(RoleName.ROLE_CHILD.name());
        nonParentUser.setRoles(Set.of(childRole));
        
        when(userRepository.findByUsername(parentUsername)).thenReturn(Optional.of(nonParentUser));

        assertThatThrownBy(() -> authService.deleteKid(kidId, parentUsername))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only parents can delete kid accounts");
    }

    @Test
    @DisplayName("deleteKid: throws ValidationException when kid not found")
    void deleteKid_kidNotFound_throws() {
        UUID kidId = UUID.randomUUID();
        String parentUsername = "parent123";
        
        User parentUser = new User();
        parentUser.setUsername(parentUsername);
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        Parent parent = new Parent();
        parent.setEmail("parent@example.com");
        
        when(userRepository.findByUsername(parentUsername)).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        when(kidRepository.findById(kidId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.deleteKid(kidId, parentUsername))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Kid not found");
    }

    @Test
    @DisplayName("deleteKid: throws ValidationException when trying to delete another parent's kid")
    void deleteKid_anotherParentKid_throws() {
        UUID kidId = UUID.randomUUID();
        String parentUsername = "parent123";
        UUID parentId = UUID.randomUUID();
        UUID otherParentId = UUID.randomUUID();
        
        User parentUser = new User();
        parentUser.setUsername(parentUsername);
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setEmail("parent@example.com");
        
        Parent otherParent = new Parent();
        otherParent.setId(otherParentId);
        otherParent.setEmail("other@example.com");
        
        User kidUser = new User();
        kidUser.setUsername("emma_kid");
        
        Kid kid = new Kid();
        kid.setId(kidId);
        kid.setNickname("Emma");
        kid.setUser(kidUser);
        kid.setParent(otherParent); // Kid belongs to different parent
        
        when(userRepository.findByUsername(parentUsername)).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        when(kidRepository.findById(kidId)).thenReturn(Optional.of(kid));

        assertThatThrownBy(() -> authService.deleteKid(kidId, parentUsername))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("You can only delete your own kids' accounts");
    }

    @Test
    @DisplayName("deleteKid: throws ValidationException when kid user account not found")
    void deleteKid_kidUserNotFound_throws() {
        UUID kidId = UUID.randomUUID();
        String parentUsername = "parent123";
        UUID parentId = UUID.randomUUID();
        
        User parentUser = new User();
        parentUser.setUsername(parentUsername);
        parentUser.setEmail("parent@example.com");
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentUser.setRoles(Set.of(parentRole));
        
        Parent parent = new Parent();
        parent.setId(parentId);
        parent.setEmail("parent@example.com");
        
        Kid kid = new Kid();
        kid.setId(kidId);
        kid.setNickname("Emma");
        kid.setUser(null); // No user associated
        kid.setParent(parent);
        
        when(userRepository.findByUsername(parentUsername)).thenReturn(Optional.of(parentUser));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        when(kidRepository.findById(kidId)).thenReturn(Optional.of(kid));

        assertThatThrownBy(() -> authService.deleteKid(kidId, parentUsername))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Kid user account not found");
    }
}
