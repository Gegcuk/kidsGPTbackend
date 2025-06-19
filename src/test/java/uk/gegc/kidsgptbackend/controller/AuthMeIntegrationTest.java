package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthMeIntegrationTest {

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

    @org.junit.jupiter.api.BeforeEach
    void setupUser() {
        roleRepository.findByRole("ROLE_PARENT").orElseGet(() -> {
            uk.gegc.kidsgptbackend.model.user.Role r = new uk.gegc.kidsgptbackend.model.user.Role();
            r.setRole("ROLE_PARENT");
            return roleRepository.save(r);
        });

        User u = new User();
        u.setUsername("meuser");
        u.setEmail("me@example.com");
        u.setHashedPassword(passwordEncoder.encode("password123"));
        u.setActive(true);
        u.setRoles(java.util.Set.of(roleRepository.findByRole("ROLE_PARENT").get()));
        userRepository.save(u);
    }

    private String obtainAccessToken() throws Exception {
        AuthLoginRequest req = new AuthLoginRequest("meuser", "password123");
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("accessToken").asText();
    }

    @Test
    @DisplayName("GET /api/v1/auth/me with token → 200 and profile data")
    void me_withToken_returnsProfile() throws Exception {
        String token = obtainAccessToken();

        String response = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("\"username\":\"meuser\"");
    }

    @Test
    @DisplayName("GET /api/v1/auth/me without token → 401")
    void me_noToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}