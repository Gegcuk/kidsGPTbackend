package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.auth.ForgotPasswordRequest;
import uk.gegc.kidsgptbackend.dto.auth.ResetPasswordRequest;
import uk.gegc.kidsgptbackend.model.auth.PasswordResetToken;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.auth.PasswordResetTokenRepository;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.auth.PasswordResetService;
import uk.gegc.kidsgptbackend.service.email.EmailService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PasswordResetControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @MockitoBean
    EmailService emailService;

    @MockitoBean
    PasswordResetService passwordResetService;

    private User testUser;
    private Role parentRole;

    @BeforeEach
    void setup() {
        // Create role if it doesn't exist
        parentRole = roleRepository.findByRole("ROLE_PARENT").orElseGet(() -> {
            Role role = new Role();
            role.setRole("ROLE_PARENT");
            return roleRepository.save(role);
        });

        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setHashedPassword("hashedpassword");
        testUser.setActive(true);
        testUser.setRoles(java.util.Set.of(parentRole));
        testUser = userRepository.save(testUser);

        // Mock email service
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString());
        doNothing().when(emailService).sendPasswordResetConfirmation(anyString(), anyString());
        doNothing().when(passwordResetService).resetPassword(any(ResetPasswordRequest.class));

    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password → 200 for valid email")
    void forgotPassword_validEmail_returnsSuccess() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account with this email exists, a password reset link has been sent."))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password → 200 for non-existent email (security)")
    void forgotPassword_nonExistentEmail_returnsSameMessage() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("nonexistent@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account with this email exists, a password reset link has been sent."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password → 400 for invalid email")
    void forgotPassword_invalidEmail_returnsBadRequest() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("invalid-email");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password → 200 for valid token and password")
    void resetPassword_validTokenAndPassword_returnsSuccess() throws Exception {
        // Create a valid token
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("valid-token-123");
        token.setUserId(testUser.getId());
        token.setEmail(testUser.getEmail());
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        tokenRepository.save(token);

        ResetPasswordRequest request = new ResetPasswordRequest("valid-token-123", "newPassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify token is marked as used
        PasswordResetToken usedToken = tokenRepository.findByToken("valid-token-123").orElse(null);
        assertThat(usedToken).isNotNull();
        assertThat(usedToken.isUsed()).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password → 400 for invalid token")
    void resetPassword_invalidToken_returnsBadRequest() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("invalid-token", "newPassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password → 400 for expired token")
    void resetPassword_expiredToken_returnsBadRequest() throws Exception {
        // Create an expired token
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("expired-token-123");
        token.setUserId(testUser.getId());
        token.setEmail(testUser.getEmail());
        token.setExpiresAt(LocalDateTime.now().minusHours(1));
        tokenRepository.save(token);

        ResetPasswordRequest request = new ResetPasswordRequest("expired-token-123", "newPassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password → 400 for weak password")
    void resetPassword_weakPassword_returnsBadRequest() throws Exception {
        // Create a valid token
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("valid-token-456");
        token.setUserId(testUser.getId());
        token.setEmail(testUser.getEmail());
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        tokenRepository.save(token);

        ResetPasswordRequest request = new ResetPasswordRequest("valid-token-456", "weak");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/auth/reset-password/validate → true for valid token")
    void validateResetToken_validToken_returnsTrue() throws Exception {
        // Create a valid token
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("valid-token-789");
        token.setUserId(testUser.getId());
        token.setEmail(testUser.getEmail());
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        tokenRepository.save(token);

        mockMvc.perform(get("/api/v1/auth/reset-password/validate")
                        .param("token", "valid-token-789"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/reset-password/validate → false for invalid token")
    void validateResetToken_invalidToken_returnsFalse() throws Exception {
        mockMvc.perform(get("/api/v1/auth/reset-password/validate")
                        .param("token", "invalid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/reset-password/validate → false for expired token")
    void validateResetToken_expiredToken_returnsFalse() throws Exception {
        // Create an expired token
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("expired-token-789");
        token.setUserId(testUser.getId());
        token.setEmail(testUser.getEmail());
        token.setExpiresAt(LocalDateTime.now().minusHours(1));
        tokenRepository.save(token);

        mockMvc.perform(get("/api/v1/auth/reset-password/validate")
                        .param("token", "expired-token-789"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
} 