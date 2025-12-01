package uk.gegc.kidsgptbackend.features.consent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentGrantRequest;
import uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentHistoryResponse;
import uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse;
import uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentWithdrawRequest;
import uk.gegc.kidsgptbackend.features.consent.application.ConsentService;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentSource;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.features.consent.domain.model.LawfulBasis;
import uk.gegc.kidsgptbackend.shared.config.ClockConfig;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ConsentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfig.class)
@DisplayName("ConsentController Unit Tests")
class ConsentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsentService consentService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID consentId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        consentId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("grantConsent: should return 200 with consent status")
    void grantConsent_validRequest_returns200() throws Exception {
        // Given
        ConsentGrantRequest request = new ConsentGrantRequest(
                userId,
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "https://example.com/policy",
                "abc123",
                UUID.randomUUID(),
                "UK",
                null,
                null,
                ConsentSource.WEB,
                null,
                null,
                null,
                LawfulBasis.CONSENT
        );

        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(),
                false,
                consentId
        );

        when(consentService.grantConsent(any(ConsentGrantRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/consent/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Consent-Id", consentId.toString()))
                .andExpect(jsonPath("$.consentId").value(consentId.toString()));
    }

    @Test
    @DisplayName("withdrawConsent: should return 200 with consent status")
    void withdrawConsent_validRequest_returns200() throws Exception {
        // Given
        ConsentWithdrawRequest request = new ConsentWithdrawRequest(
                userId.toString(),
                ConsentType.PRIVACY_POLICY,
                "1.0",
                "User requested withdrawal",
                null,
                null
        );

        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(),
                false,
                consentId
        );

        when(consentService.withdrawConsent(any(ConsentWithdrawRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/consent/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Consent-Id", consentId.toString()))
                .andExpect(jsonPath("$.consentId").value(consentId.toString()));
    }

    @Test
    @DisplayName("getConsentHistory: should return 200 when user accesses own history")
    @WithMockUser(username = "test-user-id")
    void getConsentHistory_ownHistory_returns200() throws Exception {
        // Given - setup SecurityContext with userId
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId.toString());
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        
        ConsentHistoryResponse.PaginatedConsentHistoryResponse response = 
                ConsentHistoryResponse.PaginatedConsentHistoryResponse.from(
                        new ConsentHistoryResponse(userId.toString(), List.of()),
                        0, 20, 0
                );

        when(consentService.getConsentHistory(eq(userId.toString()), eq(0), eq(20)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("getConsentHistory: should return 403 when user accesses another user's history")
    void getConsentHistory_otherUserHistory_returns403() throws Exception {
        // Given - setup SecurityContext with different userId
        UUID otherUserId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId.toString());
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When & Then - should return 403 because userId doesn't match
        mockMvc.perform(get("/api/v1/consent/history/{userId}", otherUserId))
                .andExpect(status().isForbidden());

        verify(consentService, never()).getConsentHistory(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("getConsentHistory: should return 401 when authentication is null")
    void getConsentHistory_nullAuthentication_returns401() throws Exception {
        // Given
        SecurityContextHolder.clearContext();

        // When & Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}", userId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getConsentHistory: should return 401 when authentication is not authenticated")
    void getConsentHistory_notAuthenticated_returns401() throws Exception {
        // Given
        Authentication unauthenticated = mock(Authentication.class);
        when(unauthenticated.isAuthenticated()).thenReturn(false);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(unauthenticated);
        SecurityContextHolder.setContext(securityContext);

        // When & Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}", userId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getConsentHistory: should return 401 when principal is null")
    void getConsentHistory_nullPrincipal_returns401() throws Exception {
        // Given
        Authentication authWithNullPrincipal = mock(Authentication.class);
        when(authWithNullPrincipal.isAuthenticated()).thenReturn(true);
        when(authWithNullPrincipal.getName()).thenReturn(null);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authWithNullPrincipal);
        SecurityContextHolder.setContext(securityContext);

        // When & Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}", userId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getConsentHistory: should return 401 when principal is anonymousUser")
    void getConsentHistory_anonymousUser_returns401() throws Exception {
        // Given
        Authentication anonymousAuth = mock(Authentication.class);
        when(anonymousAuth.isAuthenticated()).thenReturn(true);
        when(anonymousAuth.getName()).thenReturn("anonymousUser");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(anonymousAuth);
        SecurityContextHolder.setContext(securityContext);

        // When & Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}", userId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getConsentHistory: should return 500 when principal is not a valid UUID")
    void getConsentHistory_invalidUuidPrincipal_returns500() throws Exception {
        // Given
        Authentication invalidAuth = mock(Authentication.class);
        when(invalidAuth.isAuthenticated()).thenReturn(true);
        when(invalidAuth.getName()).thenReturn("not-a-uuid");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(invalidAuth);
        SecurityContextHolder.setContext(securityContext);

        // When & Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}", userId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("getConsentHistory: should handle pagination parameters")
    @WithMockUser(username = "test-user-id")
    void getConsentHistory_withPagination_handlesCorrectly() throws Exception {
        // Given - setup SecurityContext with userId
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId.toString());
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        
        ConsentHistoryResponse.PaginatedConsentHistoryResponse response = 
                ConsentHistoryResponse.PaginatedConsentHistoryResponse.from(
                        new ConsentHistoryResponse(userId.toString(), List.of()),
                        1, 10, 25
                );

        when(consentService.getConsentHistory(eq(userId.toString()), eq(1), eq(10)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=1&size=10", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @DisplayName("getConsentStatus: should return 200 with consent status")
    void getConsentStatus_validVerificationId_returns200() throws Exception {
        // Given
        String verificationId = UUID.randomUUID().toString();
        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(),
                false,
                consentId
        );

        when(consentService.getConsentStatus(verificationId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", verificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentId").value(consentId.toString()));
    }
}

