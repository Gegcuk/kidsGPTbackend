package uk.gegc.kidsgptbackend.features.auth.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uk.gegc.kidsgptbackend.features.user.api.dto.RegisterUserRequest;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    @DisplayName("POST /api/v1/auth/logout without Bearer header → 401 Unauthorized")
    void logout_withoutBearerHeader_returnsUnauthorized() throws Exception {
        // Test logout without Authorization header
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());

        // Test logout with non-Bearer header
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Token sometoken"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/auth/kids without authentication → 401 Unauthorized")
    void getMyKids_noAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kids"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/kids/{kidId} without authentication → 401 Unauthorized")
    void deleteKid_noAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/kids/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/account without authentication → 401 Unauthorized")
    void deleteParentAccount_noAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/account"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-email without authentication → 401 Unauthorized")
    void updateEmail_noAuthentication_returnsUnauthorized() throws Exception {
        uk.gegc.kidsgptbackend.features.auth.api.dto.UpdateEmailRequest request = 
            new uk.gegc.kidsgptbackend.features.auth.api.dto.UpdateEmailRequest("new@example.com");
        
        mockMvc.perform(put("/api/v1/auth/update-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-password without authentication → 401 Unauthorized")
    void updatePassword_noAuthentication_returnsUnauthorized() throws Exception {
        uk.gegc.kidsgptbackend.features.auth.api.dto.UpdatePasswordRequest request = 
            new uk.gegc.kidsgptbackend.features.auth.api.dto.UpdatePasswordRequest("oldpass", "newpass");
        
        mockMvc.perform(put("/api/v1/auth/update-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

}
