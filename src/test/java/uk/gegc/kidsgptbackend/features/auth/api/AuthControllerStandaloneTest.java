package uk.gegc.kidsgptbackend.features.auth.api;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.features.auth.api.AuthController;
import uk.gegc.kidsgptbackend.features.auth.api.dto.ForgotPasswordRequest;
import uk.gegc.kidsgptbackend.features.auth.api.dto.PasswordResetResponse;
import uk.gegc.kidsgptbackend.features.auth.application.AuthService;
import uk.gegc.kidsgptbackend.features.auth.application.PasswordResetService;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AuthControllerStandaloneTest.TestConfig.class, uk.gegc.kidsgptbackend.shared.config.ClockConfig.class})
@DirtiesContext
class AuthControllerStandaloneTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    PasswordResetService passwordResetService;

    @Autowired
    ObjectMapper objectMapper;

    @TestConfiguration
    static class TestConfig {
        @Bean
        AuthService authService() {
            return Mockito.mock(AuthService.class);
        }

        @Bean
        PasswordResetService passwordResetService() {
            return Mockito.mock(PasswordResetService.class);
        }
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout with non Bearer header \u2192 200 OK")
    void logout_headerWithoutBearer_returnsOk() throws Exception {
        // Reset the mock to ensure clean state
        Mockito.reset(authService);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Token abc"))
                .andExpect(status().isOk());

        verify(authService, never()).logout(anyString());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me with null principal \u2192 401")
    void me_nullPrincipal_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).getProfile(anyString());
    }

    @Test
    @DisplayName("GET /api/v1/auth/validate-reset-token returns true when valid")
    void validateResetToken_returnsOk() throws Exception {
        when(passwordResetService.validateResetToken("sometoken")).thenReturn(true);
        mockMvc.perform(get("/api/v1/auth/validate-reset-token")
                        .param("token", "sometoken"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password returns ok")
    void forgotPassword_returnsOk() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest("test@example.com");
        PasswordResetResponse resp = new PasswordResetResponse("msg", LocalDateTime.now().plusHours(1));
        when(passwordResetService.initiatePasswordReset(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout with Bearer header calls service")
    void logout_withBearerHeader_callsService() throws Exception {
        // Reset the mock to ensure clean state
        Mockito.reset(authService);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer sometoken"))
                .andExpect(status().isOk());
        verify(authService).logout("sometoken");
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout with null authHeader returns 200")
    void logout_nullAuthHeader_returnsOk() throws Exception {
        Mockito.reset(authService);

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk());

        verify(authService, never()).logout(anyString());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-kid with null principal returns 401")
    void registerKid_nullPrincipal_returnsUnauthorized() throws Exception {
        uk.gegc.kidsgptbackend.features.user.api.dto.KidRegistrationRequest req =
                new uk.gegc.kidsgptbackend.features.user.api.dto.KidRegistrationRequest(
                        "kidname", "password123", uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup.AGE_6_8);

        mockMvc.perform(post("/api/v1/auth/register-kid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).registerKid(any(), anyString());
    }

    @Test
    @DisplayName("GET /api/v1/auth/kids with null principal returns 401")
    void getMyKids_nullPrincipal_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kids"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).getParentKids(anyString());
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/kids/{kidId} with null principal returns 401")
    void deleteKid_nullPrincipal_returnsUnauthorized() throws Exception {
        java.util.UUID kidId = java.util.UUID.randomUUID();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/auth/kids/" + kidId))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).deleteKid(any(), anyString());
    }

    @Test
    @DisplayName("DELETE /api/v1/auth/account with null principal returns 401")
    void deleteParentAccount_nullPrincipal_returnsUnauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/auth/account"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).deleteParentAccount(anyString());
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-email with null principal returns 401")
    void updateEmail_nullPrincipal_returnsUnauthorized() throws Exception {
        uk.gegc.kidsgptbackend.features.auth.api.dto.UpdateEmailRequest req =
                new uk.gegc.kidsgptbackend.features.auth.api.dto.UpdateEmailRequest("newemail@example.com");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/auth/update-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).updateEmail(anyString(), any());
    }

    @Test
    @DisplayName("PUT /api/v1/auth/update-password with null principal returns 401")
    void updatePassword_nullPrincipal_returnsUnauthorized() throws Exception {
        // Note: In @WebMvcTest without security, principal will be null
        // Validation happens first, so we need valid request data
        uk.gegc.kidsgptbackend.features.auth.api.dto.UpdatePasswordRequest req =
                new uk.gegc.kidsgptbackend.features.auth.api.dto.UpdatePasswordRequest("oldpass123", "newpass123");

        // Since @WebMvcTest with addFilters = false, principal will be null
        // The controller checks principal == null and returns 401
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/auth/update-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).updatePassword(anyString(), any());
    }

    @Test
    @DisplayName("GET /api/v1/auth/validate-reset-token returns false when invalid")
    void validateResetToken_invalidToken_returnsFalse() throws Exception {
        when(passwordResetService.validateResetToken("invalidtoken")).thenReturn(false);
        mockMvc.perform(get("/api/v1/auth/validate-reset-token")
                        .param("token", "invalidtoken"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password returns 200")
    void resetPassword_returnsOk() throws Exception {
        uk.gegc.kidsgptbackend.features.auth.api.dto.ResetPasswordRequest req =
                new uk.gegc.kidsgptbackend.features.auth.api.dto.ResetPasswordRequest("token123", "newpassword");

        doNothing().when(passwordResetService).resetPassword(any());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(passwordResetService).resetPassword(any());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 201")
    void register_returnsCreated() throws Exception {
        uk.gegc.kidsgptbackend.features.user.api.dto.RegisterUserRequest req =
                new uk.gegc.kidsgptbackend.features.user.api.dto.RegisterUserRequest(
                        "testuser", "test@example.com", "password123");

        java.util.UUID userId = java.util.UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        uk.gegc.kidsgptbackend.features.user.api.dto.UserDto userDto =
                new uk.gegc.kidsgptbackend.features.user.api.dto.UserDto(
                        userId, "testuser", "test@example.com", true,
                        java.util.Collections.emptySet(), now, null, now);

        when(authService.register(any())).thenReturn(userDto);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(authService).register(any());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 200")
    void login_returnsOk() throws Exception {
        uk.gegc.kidsgptbackend.features.auth.api.dto.AuthLoginRequest req =
                new uk.gegc.kidsgptbackend.features.auth.api.dto.AuthLoginRequest("testuser", "password123");

        uk.gegc.kidsgptbackend.features.auth.api.dto.AuthTokensResponse tokens =
                new uk.gegc.kidsgptbackend.features.auth.api.dto.AuthTokensResponse(
                        "accesstoken", "refreshtoken", 3600000L, 86400000L);

        when(authService.login(any())).thenReturn(tokens);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(authService).login(any());
    }
}
