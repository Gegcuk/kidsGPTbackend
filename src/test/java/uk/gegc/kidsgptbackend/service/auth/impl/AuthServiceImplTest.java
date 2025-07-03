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
import uk.gegc.kidsgptbackend.dto.user.KidDto;
import uk.gegc.kidsgptbackend.dto.user.KidRegistrationRequest;
import uk.gegc.kidsgptbackend.dto.user.RegisterUserRequest;
import uk.gegc.kidsgptbackend.dto.user.UserDto;
import uk.gegc.kidsgptbackend.exception.UnauthorizedException;
import uk.gegc.kidsgptbackend.exception.ValidationException;
import uk.gegc.kidsgptbackend.mapper.UserMapper;
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
import uk.gegc.kidsgptbackend.security.JwtTokenProvider;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
}
