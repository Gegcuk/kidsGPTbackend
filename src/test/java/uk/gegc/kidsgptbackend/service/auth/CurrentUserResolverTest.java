package uk.gegc.kidsgptbackend.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Execution(ExecutionMode.CONCURRENT)
class CurrentUserResolverTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private CurrentUserResolver currentUserResolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("getCurrentUser resolves by username when user exists")
    void getCurrentUser_resolvesByUsernameWhenUserExists() {
        // Given
        String username = "testuser";
        User expectedUser = createTestUser(username, "test@example.com");
        
        when(userDetails.getUsername()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(expectedUser));

        // When
        User result = currentUserResolver.getCurrentUser(userDetails);

        // Then
        assertThat(result).isEqualTo(expectedUser);
        assertThat(result.getUsername()).isEqualTo(username);
    }

    @Test
    @DisplayName("getCurrentUser resolves by email when username not found")
    void getCurrentUser_resolvesByEmailWhenUsernameNotFound() {
        // Given
        String email = "test@example.com";
        User expectedUser = createTestUser("testuser", email);
        
        when(userDetails.getUsername()).thenReturn(email);
        when(userRepository.findByUsername(email)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(expectedUser));

        // When
        User result = currentUserResolver.getCurrentUser(userDetails);

        // Then
        assertThat(result).isEqualTo(expectedUser);
        assertThat(result.getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("getCurrentUser throws exception when user not found by username or email")
    void getCurrentUser_throwsExceptionWhenUserNotFound() {
        // Given
        String username = "nonexistent";
        
        when(userDetails.getUsername()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(username)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> currentUserResolver.getCurrentUser(userDetails))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User not found: " + username);
    }

    @Test
    @DisplayName("getCurrentUser throws exception when principal is null")
    void getCurrentUser_throwsExceptionWhenPrincipalIsNull() {
        // When & Then
        assertThatThrownBy(() -> currentUserResolver.getCurrentUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Principal cannot be null");
    }

    @Test
    @DisplayName("getCurrentUser handles email as username correctly")
    void getCurrentUser_handlesEmailAsUsernameCorrectly() {
        // Given
        String email = "user@example.com";
        User expectedUser = createTestUser("actualuser", email);
        
        when(userDetails.getUsername()).thenReturn(email);
        when(userRepository.findByUsername(email)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(expectedUser));

        // When
        User result = currentUserResolver.getCurrentUser(userDetails);

        // Then
        assertThat(result).isEqualTo(expectedUser);
        assertThat(result.getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("getCurrentUser prioritizes username over email when both exist")
    void getCurrentUser_prioritizesUsernameOverEmail() {
        // Given
        String username = "testuser";
        String email = "test@example.com";
        User usernameUser = createTestUser(username, "different@example.com");
        User emailUser = createTestUser("differentuser", email);
        
        when(userDetails.getUsername()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(usernameUser));
        // Note: findByEmail should not be called when findByUsername returns a result

        // When
        User result = currentUserResolver.getCurrentUser(userDetails);

        // Then
        assertThat(result).isEqualTo(usernameUser);
        assertThat(result.getUsername()).isEqualTo(username);
    }

    @Test
    @DisplayName("getCurrentUser works with different username formats")
    void getCurrentUser_worksWithDifferentUsernameFormats() {
        // Given
        String username = "user.name+tag@domain.com";
        User expectedUser = createTestUser("actualuser", username);
        
        when(userDetails.getUsername()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(username)).thenReturn(Optional.of(expectedUser));

        // When
        User result = currentUserResolver.getCurrentUser(userDetails);

        // Then
        assertThat(result).isEqualTo(expectedUser);
        assertThat(result.getEmail()).isEqualTo(username);
    }

    private User createTestUser(String username, String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(email);
        user.setActive(true);
        return user;
    }
}
