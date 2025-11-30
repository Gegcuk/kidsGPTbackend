package uk.gegc.kidsgptbackend.features.auth.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uk.gegc.kidsgptbackend.features.user.api.dto.RegisterUserRequest;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
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
        uk.gegc.kidsgptbackend.features.user.domain.model.User existing = new uk.gegc.kidsgptbackend.features.user.domain.model.User();
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
