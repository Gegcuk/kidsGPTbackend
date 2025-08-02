package uk.gegc.kidsgptbackend.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.dto.consent.ConsentHistoryResponse;
import uk.gegc.kidsgptbackend.service.consent.ConsentService;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ConsentController.class)
@AutoConfigureMockMvc(addFilters = false)
class ConsentControllerHistoryIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConsentService consentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        Mockito.reset(consentService);
    }

    @Test
    void unauthenticatedRequest_returns401() throws Exception {
        // Given: no authentication
        String anyUserId = UUID.randomUUID().toString();

        // When / Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}", anyUserId)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isUnauthorized())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.details[0]").value("Authentication required"));

        // Ensure service is not called
        verifyNoInteractions(consentService);
    }

    @Test
    void accessingAnotherUsersHistory_returns403() throws Exception {
        // Given: principal A, but path has B != A
        String principalA = UUID.randomUUID().toString();
        String userB = UUID.randomUUID().toString();

        // Set up authentication context manually
        User principal = new User(
                principalA,
                "password",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        principal.getPassword(),
                        principal.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // When / Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}", userB)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isForbidden())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.details[0]")
               .value("Access denied: You can only view your own consent history"));

        // Ensure service is not called
        verifyNoInteractions(consentService);
    }

    @Test
    void nonUuidPrincipal_returns500() throws Exception {
        // Given: authenticated principal name isn't a UUID (per current code path)
        String pathUserId = UUID.randomUUID().toString();

        // Set up authentication context with non-UUID principal
        User principal = new User(
                "john", // not a UUID
                "password",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        principal.getPassword(),
                        principal.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // When / Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}", pathUserId)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isInternalServerError())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.details[0]").value("Invalid user authentication"));

        // Ensure service is not called
        verifyNoInteractions(consentService);
    }

    @Test
    void invalidUuidPathParameter_returns400() throws Exception {
        // Given: authenticated principal matches any valid UUID
        String principalA = UUID.randomUUID().toString();

        // Set up authentication context manually
        User principal = new User(
                principalA,
                "password",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        principal.getPassword(),
                        principal.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // When / Then
        mockMvc.perform(get("/api/v1/consent/history/not-a-uuid")
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isBadRequest())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.details[0]").value("Parameter 'userId' should be of type UUID"));

        // Ensure service is not called
        verifyNoInteractions(consentService);
    }
} 