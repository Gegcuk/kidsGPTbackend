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
}
