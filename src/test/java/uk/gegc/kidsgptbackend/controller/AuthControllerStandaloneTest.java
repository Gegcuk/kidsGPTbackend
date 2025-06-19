package uk.gegc.kidsgptbackend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.service.auth.AuthService;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthControllerStandaloneTest.TestConfig.class)
class AuthControllerStandaloneTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        AuthService authService() {
            return Mockito.mock(AuthService.class);
        }
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout with non Bearer header \u2192 200 OK")
    void logout_headerWithoutBearer_returnsOk() throws Exception {
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
}
