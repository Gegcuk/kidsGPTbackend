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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.auth.ForgotPasswordRequest;
import uk.gegc.kidsgptbackend.dto.auth.PasswordResetResponse;
import uk.gegc.kidsgptbackend.dto.auth.ResetPasswordRequest;
import uk.gegc.kidsgptbackend.model.auth.PasswordResetToken;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.auth.PasswordResetTokenRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.email.EmailService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
class PasswordResetServiceImplTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordResetTokenRepository tokenRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    EmailService emailService;
    
    @Mock
    Clock clock;

    @InjectMocks
    PasswordResetServiceImpl passwordResetService;

    private User testUser;
    private PasswordResetToken testToken;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Set up clock mock
        when(clock.instant()).thenReturn(Instant.ofEpochMilli(1000L));
        when(clock.millis()).thenReturn(1000L);
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setActive(true);

        testToken = new PasswordResetToken();
        testToken.setId(UUID.randomUUID());
        testToken.setToken("test-token-123");
        testToken.setUserId(testUser.getId());
        testToken.setEmail(testUser.getEmail());
        testToken.setExpiresAt(LocalDateTime.now().plusHours(1));
        testToken.setUsed(false);
    }

    @Test
    @DisplayName("initiatePasswordReset: success for existing user")
    void initiatePasswordReset_existingUser_success() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString());

        PasswordResetResponse response = passwordResetService.initiatePasswordReset(request);

        assertThat(response.message()).contains("If an account with this email exists");
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now(clock));

        verify(tokenRepository).invalidateAllTokensForUser(eq(testUser.getId()), any(LocalDateTime.class));
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq(testUser.getEmail()), anyString(), eq(testUser.getUsername()));
    }

    @Test
    @DisplayName("initiatePasswordReset: returns same message for non-existent user (security)")
    void initiatePasswordReset_nonExistentUser_returnsSameMessage() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("nonexistent@example.com");

        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        PasswordResetResponse response = passwordResetService.initiatePasswordReset(request);

        assertThat(response.message()).contains("If an account with this email exists");
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now(clock));

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("initiatePasswordReset: returns same message for inactive user (security)")
    void initiatePasswordReset_inactiveUser_returnsSameMessage() {
        testUser.setActive(false);
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        PasswordResetResponse response = passwordResetService.initiatePasswordReset(request);

        assertThat(response.message()).contains("If an account with this email exists");
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now(clock));

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("initiatePasswordReset: throws exception when email fails")
    void initiatePasswordReset_emailFailure_throwsException() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);
        doThrow(new RuntimeException("Email service error")).when(emailService)
                .sendPasswordResetEmail(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> passwordResetService.initiatePasswordReset(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.INTERNAL_SERVER_ERROR);

        verify(tokenRepository).delete(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("resetPassword: success for valid token")
    void resetPassword_validToken_success() {
        ResetPasswordRequest request = new ResetPasswordRequest("test-token-123", "newPassword123");

        when(tokenRepository.findValidTokenByTokenAndExpiresAtAfter(eq("test-token-123"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(testToken));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encodedPassword");
        doNothing().when(emailService).sendPasswordResetConfirmation(anyString(), anyString());

        passwordResetService.resetPassword(request);

        verify(userRepository).save(testUser);
        verify(tokenRepository).save(testToken);
        verify(tokenRepository).invalidateAllTokensForUser(eq(testUser.getId()), any(LocalDateTime.class));
        verify(emailService).sendPasswordResetConfirmation(testUser.getEmail(), testUser.getUsername());
        assertThat(testToken.isUsed()).isTrue();
        assertThat(testToken.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("resetPassword: throws exception for invalid token")
    void resetPassword_invalidToken_throwsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("invalid-token", "newPassword123");

        when(tokenRepository.findValidTokenByTokenAndExpiresAtAfter(eq("invalid-token"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("resetPassword: throws exception for expired token")
    void resetPassword_expiredToken_throwsException() {
        testToken.setExpiresAt(LocalDateTime.now().minusHours(1));
        ResetPasswordRequest request = new ResetPasswordRequest("expired-token", "newPassword123");

        when(tokenRepository.findValidTokenByTokenAndExpiresAtAfter(eq("expired-token"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("resetPassword: throws exception for inactive user")
    void resetPassword_inactiveUser_throwsException() {
        testUser.setActive(false);
        ResetPasswordRequest request = new ResetPasswordRequest("test-token-123", "newPassword123");

        when(tokenRepository.findValidTokenByTokenAndExpiresAtAfter(eq("test-token-123"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(testToken));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateResetToken: returns true for valid token")
    void validateResetToken_validToken_returnsTrue() {
        when(tokenRepository.findValidTokenByTokenAndExpiresAtAfter(eq("test-token-123"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(testToken));

        boolean isValid = passwordResetService.validateResetToken("test-token-123");

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("validateResetToken: returns false for invalid token")
    void validateResetToken_invalidToken_returnsFalse() {
        when(tokenRepository.findValidTokenByTokenAndExpiresAtAfter(eq("invalid-token"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        boolean isValid = passwordResetService.validateResetToken("invalid-token");

        assertThat(isValid).isFalse();
    }
} 