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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.consent.ConsentHistoryResponse;
import uk.gegc.kidsgptbackend.model.consent.ConsentSource;
import uk.gegc.kidsgptbackend.model.consent.ConsentStatus;
import uk.gegc.kidsgptbackend.model.consent.ConsentType;
import uk.gegc.kidsgptbackend.model.consent.LawfulBasis;
import uk.gegc.kidsgptbackend.service.consent.ConsentService;

import java.time.LocalDateTime;

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

    @Test
    void happyPath_returns200WithWellFormedPaginatedPayload() throws Exception {
        // Given: authenticated principal equals userId
        String userId = UUID.randomUUID().toString();

        // Set up authentication context manually
        User principal = new User(
                userId,
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

        // Mock service response
        ConsentHistoryResponse.PaginatedConsentHistoryResponse mockResponse = 
                new ConsentHistoryResponse.PaginatedConsentHistoryResponse(
                        userId,
                        java.util.List.of(), // empty entries for this test
                        0,
                        5,
                        0L,
                        0,
                        false,
                        false
                );
        
        Mockito.when(consentService.getConsentHistory(userId, 0, 5))
                .thenReturn(mockResponse);

        // When / Then
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=5", userId)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.userId").value(userId))
           .andExpect(jsonPath("$.entries").isArray())
           .andExpect(jsonPath("$.page").value(0))
           .andExpect(jsonPath("$.size").value(5))
           .andExpect(jsonPath("$.total").value(0))
           .andExpect(jsonPath("$.totalPages").value(0))
           .andExpect(jsonPath("$.hasNext").value(false))
           .andExpect(jsonPath("$.hasPrevious").value(false));

        // Verify service was called with correct parameters
        Mockito.verify(consentService).getConsentHistory(userId, 0, 5);
    }

    @Test
    void paginationDefaultsApplied() throws Exception {
        // Given: authenticated principal equals userId
        String userId = UUID.randomUUID().toString();

        // Set up authentication context manually
        User principal = new User(
                userId,
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

        // Mock service response with default pagination values
        ConsentHistoryResponse.PaginatedConsentHistoryResponse mockResponse = 
                new ConsentHistoryResponse.PaginatedConsentHistoryResponse(
                        userId,
                        java.util.List.of(), // empty entries for this test
                        0, // default page
                        20, // default size
                        0L,
                        0,
                        false,
                        false
                );
        
        Mockito.when(consentService.getConsentHistory(userId, 0, 20))
                .thenReturn(mockResponse);

        // When / Then: GET without query params should use defaults
        mockMvc.perform(get("/api/v1/consent/history/{userId}", userId)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.userId").value(userId))
           .andExpect(jsonPath("$.entries").isArray())
           .andExpect(jsonPath("$.page").value(0)) // default page
           .andExpect(jsonPath("$.size").value(20)) // default size
           .andExpect(jsonPath("$.total").value(0))
           .andExpect(jsonPath("$.totalPages").value(0))
           .andExpect(jsonPath("$.hasNext").value(false))
           .andExpect(jsonPath("$.hasPrevious").value(false));

        // Verify service was called with default parameters (page=0, size=20)
        Mockito.verify(consentService).getConsentHistory(userId, 0, 20);
    }

    @Test
    void invalidPaginationParams_returns400() throws Exception {
        // Given: authenticated principal equals userId
        String userId = UUID.randomUUID().toString();

        // Set up authentication context manually
        User principal = new User(
                userId,
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

        // Mock service to throw exceptions for invalid pagination parameters
        Mockito.when(consentService.getConsentHistory(userId, -1, 20))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page number must be non-negative"));
        
        Mockito.when(consentService.getConsentHistory(userId, 0, 0))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100"));
        
        Mockito.when(consentService.getConsentHistory(userId, 0, 101))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100"));

        // Test case 1: page < 0
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=-1&size=20", userId)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isBadRequest())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.details[0]").value("Page number must be non-negative"));

        // Test case 2: size = 0
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=0", userId)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isBadRequest())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.details[0]").value("Page size must be between 1 and 100"));

        // Test case 3: size > 100
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=101", userId)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isBadRequest())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.details[0]").value("Page size must be between 1 and 100"));

        // Verify service was called with the invalid parameters
        Mockito.verify(consentService).getConsentHistory(userId, -1, 20);
        Mockito.verify(consentService).getConsentHistory(userId, 0, 0);
        Mockito.verify(consentService).getConsentHistory(userId, 0, 101);
    }

    @Test
    void emptyHistory_returnsEmptyListAndZeroTotals() throws Exception {
        // Given: authenticated principal equals userId and no ledger rows for user
        String userId = UUID.randomUUID().toString();

        // Set up authentication context manually
        User principal = new User(
                userId,
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

        // Mock service response with empty history (no ledger rows)
        ConsentHistoryResponse.PaginatedConsentHistoryResponse mockResponse = 
                new ConsentHistoryResponse.PaginatedConsentHistoryResponse(
                        userId,
                        java.util.List.of(), // empty entries array
                        0, // page
                        20, // size
                        0L, // total = 0
                        0, // totalPages = 0
                        false, // hasNext = false
                        false // hasPrevious = false
                );
        
        Mockito.when(consentService.getConsentHistory(userId, 0, 20))
                .thenReturn(mockResponse);

        // When / Then: GET should return empty list and zero totals
        mockMvc.perform(get("/api/v1/consent/history/{userId}", userId)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.userId").value(userId))
           .andExpect(jsonPath("$.entries").isArray())
           .andExpect(jsonPath("$.entries").isEmpty()) // entries=[]
           .andExpect(jsonPath("$.page").value(0))
           .andExpect(jsonPath("$.size").value(20))
           .andExpect(jsonPath("$.total").value(0)) // total=0
           .andExpect(jsonPath("$.totalPages").value(0)) // totalPages=0
           .andExpect(jsonPath("$.hasNext").value(false)) // hasNext=false
           .andExpect(jsonPath("$.hasPrevious").value(false)); // hasPrevious=false

        // Verify service was called with correct parameters
        Mockito.verify(consentService).getConsentHistory(userId, 0, 20);
    }

    @Test
    void deterministicOrdering_acrossPages() throws Exception {
        // Given: authenticated principal equals userId and multiple rows with identical consentTimestamp but differing createdAt
        String userId = UUID.randomUUID().toString();
        LocalDateTime sameTimestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        LocalDateTime earlierCreatedAt = LocalDateTime.of(2024, 1, 15, 10, 25, 0);
        LocalDateTime laterCreatedAt = LocalDateTime.of(2024, 1, 15, 10, 35, 0);
        
        // Format timestamps to match JSON serialization format (with seconds)
        String sameTimestampStr = "2024-01-15T10:30:00";
        String earlierCreatedAtStr = "2024-01-15T10:25:00";
        String laterCreatedAtStr = "2024-01-15T10:35:00";

        // Set up authentication context manually
        User principal = new User(
                userId,
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

        // Create mock entries with identical consentTimestamp but differing createdAt
        ConsentHistoryResponse.ConsentHistoryEntry entry1 = new ConsentHistoryResponse.ConsentHistoryEntry(
                "consent-1", ConsentType.DATA_PROCESSING, "1.0.0", ConsentStatus.GRANTED,
                "policy-url", "hash1", "GB", "UK", "en", LawfulBasis.CONSENT, ConsentSource.WEB,
                "192.168.1.1", "Mozilla/5.0", sameTimestamp, null, 
                LocalDateTime.of(2032, 1, 15, 10, 30, 0), laterCreatedAt, // later createdAt
                java.util.List.of("kid1"), null
        );
        
        ConsentHistoryResponse.ConsentHistoryEntry entry2 = new ConsentHistoryResponse.ConsentHistoryEntry(
                "consent-2", ConsentType.DATA_PROCESSING, "1.0.0", ConsentStatus.GRANTED,
                "policy-url", "hash2", "GB", "UK", "en", LawfulBasis.CONSENT, ConsentSource.WEB,
                "192.168.1.1", "Mozilla/5.0", sameTimestamp, null, 
                LocalDateTime.of(2032, 1, 15, 10, 30, 0), earlierCreatedAt, // earlier createdAt
                java.util.List.of("kid2"), null
        );

        // Mock service response for page 0 (size=1) - should return entry1 (later createdAt first)
        ConsentHistoryResponse.PaginatedConsentHistoryResponse mockResponsePage0 = 
                new ConsentHistoryResponse.PaginatedConsentHistoryResponse(
                        userId,
                        java.util.List.of(entry1), // later createdAt first
                        0, // page
                        1, // size
                        2L, // total
                        2, // totalPages
                        true, // hasNext
                        false // hasPrevious
                );
        
        // Mock service response for page 1 (size=1) - should return entry2 (earlier createdAt second)
        ConsentHistoryResponse.PaginatedConsentHistoryResponse mockResponsePage1 = 
                new ConsentHistoryResponse.PaginatedConsentHistoryResponse(
                        userId,
                        java.util.List.of(entry2), // earlier createdAt second
                        1, // page
                        1, // size
                        2L, // total
                        2, // totalPages
                        false, // hasNext
                        true // hasPrevious
                );
        
        Mockito.when(consentService.getConsentHistory(userId, 0, 1))
                .thenReturn(mockResponsePage0);
        Mockito.when(consentService.getConsentHistory(userId, 1, 1))
                .thenReturn(mockResponsePage1);

        // When / Then: Page 0 should return entry with later createdAt first
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=1", userId)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.userId").value(userId))
           .andExpect(jsonPath("$.entries").isArray())
           .andExpect(jsonPath("$.page").value(0))
           .andExpect(jsonPath("$.size").value(1))
           .andExpect(jsonPath("$.total").value(2))
           .andExpect(jsonPath("$.totalPages").value(2))
           .andExpect(jsonPath("$.hasNext").value(true))
           .andExpect(jsonPath("$.hasPrevious").value(false))
           .andExpect(jsonPath("$.entries[0].consentId").value("consent-1"))
           .andExpect(jsonPath("$.entries[0].consentTimestamp").value(sameTimestampStr))
           .andExpect(jsonPath("$.entries[0].createdAt").value(laterCreatedAtStr)); // later createdAt first

        // When / Then: Page 1 should return entry with earlier createdAt second
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=1&size=1", userId)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.userId").value(userId))
           .andExpect(jsonPath("$.entries").isArray())
           .andExpect(jsonPath("$.page").value(1))
           .andExpect(jsonPath("$.size").value(1))
           .andExpect(jsonPath("$.total").value(2))
           .andExpect(jsonPath("$.totalPages").value(2))
           .andExpect(jsonPath("$.hasNext").value(false))
           .andExpect(jsonPath("$.hasPrevious").value(true))
           .andExpect(jsonPath("$.entries[0].consentId").value("consent-2"))
           .andExpect(jsonPath("$.entries[0].consentTimestamp").value(sameTimestampStr))
           .andExpect(jsonPath("$.entries[0].createdAt").value(earlierCreatedAtStr)); // earlier createdAt second

                 // Verify service was called with correct parameters
         Mockito.verify(consentService).getConsentHistory(userId, 0, 1);
         Mockito.verify(consentService).getConsentHistory(userId, 1, 1);
     }

     @Test
     void entriesIncludeGrantedAndWithdrawnStatuses() throws Exception {
         // Given: authenticated principal equals userId and mixed ledger statuses
         String userId = UUID.randomUUID().toString();
         LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
         LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 25, 0);

         // Set up authentication context manually
         User principal = new User(
                 userId,
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

         // Create mock entries with both GRANTED and WITHDRAWN statuses
         ConsentHistoryResponse.ConsentHistoryEntry grantedEntry = new ConsentHistoryResponse.ConsentHistoryEntry(
                 "consent-granted", ConsentType.DATA_PROCESSING, "1.0.0", ConsentStatus.GRANTED,
                 "policy-url", "hash1", "GB", "UK", "en", LawfulBasis.CONSENT, ConsentSource.WEB,
                 "192.168.1.1", "Mozilla/5.0", timestamp, null, 
                 LocalDateTime.of(2032, 1, 15, 10, 30, 0), createdAt,
                 java.util.List.of("kid1"), null
         );
         
         ConsentHistoryResponse.ConsentHistoryEntry withdrawnEntry = new ConsentHistoryResponse.ConsentHistoryEntry(
                 "consent-withdrawn", ConsentType.DATA_PROCESSING, "1.0.0", ConsentStatus.WITHDRAWN,
                 "policy-url", "hash2", "GB", "UK", "en", LawfulBasis.CONSENT, ConsentSource.WEB,
                 "192.168.1.1", "Mozilla/5.0", timestamp, "consent-granted", // withdrawnConsentId links to granted entry
                 LocalDateTime.of(2032, 1, 15, 10, 30, 0), createdAt,
                 java.util.List.of("kid1"), null
         );

         // Mock service response with both statuses
         ConsentHistoryResponse.PaginatedConsentHistoryResponse mockResponse = 
                 new ConsentHistoryResponse.PaginatedConsentHistoryResponse(
                         userId,
                         java.util.List.of(grantedEntry, withdrawnEntry), // both statuses
                         0, // page
                         20, // size
                         2L, // total
                         1, // totalPages
                         false, // hasNext
                         false // hasPrevious
                 );
         
         Mockito.when(consentService.getConsentHistory(userId, 0, 20))
                 .thenReturn(mockResponse);

         // When / Then: GET should return both GRANTED and WITHDRAWN statuses
         mockMvc.perform(get("/api/v1/consent/history/{userId}", userId)
                 .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.userId").value(userId))
                         .andExpect(jsonPath("$.entries").isArray())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.hasPrevious").value(false))
            // Verify GRANTED entry
            .andExpect(jsonPath("$.entries[0].consentId").value("consent-granted"))
            .andExpect(jsonPath("$.entries[0].consentStatus").value("GRANTED"))
            .andExpect(jsonPath("$.entries[0].withdrawnConsentId").isEmpty())
                         // Verify WITHDRAWN entry
             .andExpect(jsonPath("$.entries[1].consentId").value("consent-withdrawn"))
             .andExpect(jsonPath("$.entries[1].consentStatus").value("WITHDRAWN"))
             .andExpect(jsonPath("$.entries[1].parentVerificationId").value("consent-granted"));

         // Verify service was called with correct parameters
         Mockito.verify(consentService).getConsentHistory(userId, 0, 20);
     }
} 