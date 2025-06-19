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
import uk.gegc.kidsgptbackend.dto.user.RegisterUserRequest;
import uk.gegc.kidsgptbackend.dto.user.UserDto;
import uk.gegc.kidsgptbackend.exception.UnauthorizedException;
import uk.gegc.kidsgptbackend.mapper.UserMapper;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.security.JwtTokenProvider;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
public class AuthServiceImplTest {

    @Mock
    UserRepository userRepository;
    @Mock
    RoleRepository roleRepository;
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
}
