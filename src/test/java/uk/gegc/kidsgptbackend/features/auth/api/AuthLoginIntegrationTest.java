package uk.gegc.kidsgptbackend.features.auth.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uk.gegc.kidsgptbackend.features.auth.api.dto.AuthLoginRequest;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthLoginIntegrationTest extends BaseIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();

        User u = new User();
        u.setUsername("loginuser");
        u.setEmail("login@example.com");
        u.setHashedPassword(passwordEncoder.encode("password123"));
        u.setActive(true);
        u.setRoles(java.util.Set.of(roleRepository.findByRole("ROLE_PARENT").orElseThrow()));
        userRepository.save(u);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login → 200 & tokens for valid request")
    void login_validRequest_returnsTokens() throws Exception {
        AuthLoginRequest req = new AuthLoginRequest("loginuser", "password123");
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).contains("accessToken").contains("refreshToken");
    }

    @Test
    @DisplayName("Wrong username → 401 Unauthorized")
    void login_wrongUsername_returnsUnauthorized() throws Exception {
        AuthLoginRequest req = new AuthLoginRequest("wrong", "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid password → 401 Unauthorized")
    void login_invalidPassword_returnsUnauthorized() throws Exception {
        AuthLoginRequest req = new AuthLoginRequest("loginuser", "badpass");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Missing field → 400 Bad Request")
    void login_missingField_returnsBadRequest() throws Exception {
        String json = "{}";
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
