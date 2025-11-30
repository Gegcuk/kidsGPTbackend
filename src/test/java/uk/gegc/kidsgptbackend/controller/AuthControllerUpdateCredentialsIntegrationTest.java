package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.auth.UpdateEmailRequest;
import uk.gegc.kidsgptbackend.dto.auth.UpdatePasswordRequest;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.shared.security.JwtTokenProvider;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthControllerUpdateCredentialsIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private String accessToken;

    @BeforeEach
    void setup() {
        // Create role if it doesn't exist
        Role parentRole = roleRepository.findByRole("ROLE_PARENT").orElseGet(() -> {
            Role role = new Role();
            role.setRole("ROLE_PARENT");
            return roleRepository.save(role);
        });

        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setHashedPassword(passwordEncoder.encode("password123"));
        testUser.setActive(true);
        testUser.setRoles(new HashSet<>(java.util.Arrays.asList(parentRole)));
        testUser = userRepository.save(testUser);

        // Generate JWT token
        Authentication auth = new UsernamePasswordAuthenticationToken(
            testUser.getUsername(), 
            null, 
            testUser.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getRole()))
                .collect(java.util.stream.Collectors.toList())
        );
        accessToken = jwtTokenProvider.generateAccessToken(auth);
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-email → 200 & updated profile for valid request")
    void updateEmail_validRequest_returnsUpdatedProfile() throws Exception {
        UpdateEmailRequest request = new UpdateEmailRequest("newemail@example.com");

        String response = mockMvc.perform(put("/api/v1/auth/update-email")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newemail@example.com"))
                .andReturn().getResponse().getContentAsString();

        // Verify database was updated
        User updatedUser = userRepository.findByUsername("testuser").orElseThrow();
        assertThat(updatedUser.getEmail()).isEqualTo("newemail@example.com");
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-email → 409 when email already in use")
    void updateEmail_duplicateEmail_returnsConflict() throws Exception {
        // Create another user with the target email
        User otherUser = new User();
        otherUser.setUsername("otheruser");
        otherUser.setEmail("newemail@example.com");
        otherUser.setHashedPassword(passwordEncoder.encode("password123"));
        otherUser.setActive(true);
        otherUser.setRoles(new HashSet<>(java.util.Arrays.asList(roleRepository.findByRole("ROLE_PARENT").get())));
        userRepository.save(otherUser);

        UpdateEmailRequest request = new UpdateEmailRequest("newemail@example.com");

        mockMvc.perform(put("/api/v1/auth/update-email")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-email → 400 for invalid email format")
    void updateEmail_invalidEmail_returnsBadRequest() throws Exception {
        UpdateEmailRequest request = new UpdateEmailRequest("invalid-email");

        mockMvc.perform(put("/api/v1/auth/update-email")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-email → 401 when not authenticated")
    void updateEmail_notAuthenticated_returnsUnauthorized() throws Exception {
        UpdateEmailRequest request = new UpdateEmailRequest("newemail@example.com");

        mockMvc.perform(put("/api/v1/auth/update-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-password → 200 & updated profile for valid request")
    void updatePassword_validRequest_returnsUpdatedProfile() throws Exception {
        UpdatePasswordRequest request = new UpdatePasswordRequest("password123", "newpassword123");

        String response = mockMvc.perform(put("/api/v1/auth/update-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Verify database was updated
        User updatedUser = userRepository.findByUsername("testuser").orElseThrow();
        assertThat(passwordEncoder.matches("newpassword123", updatedUser.getHashedPassword())).isTrue();
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-password → 400 when current password is incorrect")
    void updatePassword_wrongCurrentPassword_returnsBadRequest() throws Exception {
        UpdatePasswordRequest request = new UpdatePasswordRequest("wrongpassword", "newpassword123");

        mockMvc.perform(put("/api/v1/auth/update-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Current password is incorrect")));
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-password → 400 when new password is same as current")
    void updatePassword_samePassword_returnsBadRequest() throws Exception {
        UpdatePasswordRequest request = new UpdatePasswordRequest("password123", "password123");

        mockMvc.perform(put("/api/v1/auth/update-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("New password must be different")));
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-password → 400 for short new password")
    void updatePassword_shortNewPassword_returnsBadRequest() throws Exception {
        UpdatePasswordRequest request = new UpdatePasswordRequest("password123", "short");

        mockMvc.perform(put("/api/v1/auth/update-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-password → 401 when not authenticated")
    void updatePassword_notAuthenticated_returnsUnauthorized() throws Exception {
        UpdatePasswordRequest request = new UpdatePasswordRequest("password123", "newpassword123");

        mockMvc.perform(put("/api/v1/auth/update-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }


} 