package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.auth.ResetPasswordRequest;
import uk.gegc.kidsgptbackend.model.auth.PasswordResetToken;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.auth.PasswordResetTokenRepository;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        testUser.setRoles(new HashSet<>(java.util.Set.of(parentRole)));
        testUser = userRepository.save(testUser);
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
} 