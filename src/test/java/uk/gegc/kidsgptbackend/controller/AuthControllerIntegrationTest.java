package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.user.RegisterUserRequest;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;

    @org.junit.jupiter.api.BeforeEach
    void setupRole() {
        roleRepository.findByRole("ROLE_PARENT").orElseGet(() -> {
            uk.gegc.kidsgptbackend.model.user.Role r = new uk.gegc.kidsgptbackend.model.user.Role();
            r.setRole("ROLE_PARENT");
            return roleRepository.save(r);
        });
    }

    @Test
    @DisplayName("POST /api/v1/auth/register → 201 & user details for valid request")
    void register_validRequest_returnsUser() throws Exception {
        RegisterUserRequest req = new RegisterUserRequest("alice", "alice@example.com", "secretPass1");

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("\"username\":\"alice\"");
        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
    }

    @Test
    @DisplayName("Duplicate email → 409 Conflict")
    void register_duplicateEmail_returnsConflict() throws Exception {
        uk.gegc.kidsgptbackend.model.user.User existing = new uk.gegc.kidsgptbackend.model.user.User();
        existing.setUsername("bob");
        existing.setEmail("bob@example.com");
        existing.setHashedPassword("hash");
        existing.setActive(true);
        existing.setRoles(java.util.Set.of(roleRepository.findByRole("ROLE_PARENT").get()));
        userRepository.save(existing);

        RegisterUserRequest req = new RegisterUserRequest("other", "bob@example.com", "secretPass1");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Invalid email or password → 400 Bad Request")
    void register_invalidEmailPassword_returnsBadRequest() throws Exception {
        RegisterUserRequest req = new RegisterUserRequest("carl", "bad-email", "short");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Missing fields → 400 Bad Request")
    void register_missingFields_returnsBadRequest() throws Exception {
        String json = "{}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

}
