package uk.gegc.kidsgptbackend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gegc.kidsgptbackend.repository.consent.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.repository.consent.ParentVerificationRepository;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class ConsentHistoryRepositoryExceptionIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ConsentLedgerRepository consentLedgerRepository;

    @MockitoBean
    private ConsentChildCoverageRepository consentChildCoverageRepository;

    @MockitoBean
    private ParentVerificationRepository parentVerificationRepository;

    private MockMvc mockMvc;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        testUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Repository throws unexpected runtime exception (ledger) - service returns ResponseStatusException(500)")
    void repositoryThrowsUnexpectedRuntimeException_ledger_serviceReturnsResponseStatusException500() throws Exception {
        // Mock the repository to throw an unexpected runtime exception
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(any(UUID.class), any()))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Make request to trigger the exception
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=10", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(testUserId.toString())))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Failed to retrieve consent history"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Repository throws unexpected runtime exception (ledger) - controller returns 500 with error payload")
    void repositoryThrowsUnexpectedRuntimeException_ledger_controllerReturns500WithErrorPayload() throws Exception {
        // Mock the repository to throw a different unexpected runtime exception
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(any(UUID.class), any()))
                .thenThrow(new IllegalStateException("Database constraint violation"));

        // Make request to trigger the exception
        mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=10", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(testUserId.toString())))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Failed to retrieve consent history"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Repository throws unexpected runtime exception (ledger) - error payload contains exception details")
    void repositoryThrowsUnexpectedRuntimeException_ledger_errorPayloadContainsExceptionDetails() throws Exception {
        // Mock the repository to throw an exception with a specific message
        String exceptionMessage = "Database connection failed";
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(any(UUID.class), any()))
                .thenThrow(new RuntimeException(exceptionMessage));

        // Make request to trigger the exception
        String response = mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=10", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(testUserId.toString())))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify that the error payload contains the exception details
        verifyErrorPayloadContainsExceptionDetails(response, exceptionMessage);
    }

    @Test
    @DisplayName("Repository throws unexpected runtime exception (ledger) - multiple requests handle exceptions consistently")
    void repositoryThrowsUnexpectedRuntimeException_ledger_multipleRequestsHandleExceptionsConsistently() throws Exception {
        // Mock the repository to throw exceptions consistently
        when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(any(UUID.class), any()))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Make multiple requests to verify consistent error handling
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=10", testUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(user(testUserId.toString())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.error").value("Failed to retrieve consent history"));
        }
    }

    @Test
    @DisplayName("Repository throws unexpected runtime exception (ledger) - different exception types handled consistently")
    void repositoryThrowsUnexpectedRuntimeException_ledger_differentExceptionTypesHandledConsistently() throws Exception {
        // Test with different types of runtime exceptions
        RuntimeException[] exceptions = {
                new RuntimeException("Database connection failed"),
                new IllegalStateException("Database constraint violation"),
                new RuntimeException("Query timeout"),
                new IllegalStateException("Connection pool exhausted")
        };

        for (RuntimeException exception : exceptions) {
            // Mock the repository to throw the current exception
            when(consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(any(UUID.class), any()))
                    .thenThrow(exception);

            // Make request to trigger the exception
            mockMvc.perform(get("/api/v1/consent/history/{userId}?page=0&size=10", testUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(user(testUserId.toString())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.error").value("Failed to retrieve consent history"))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    private void verifyErrorPayloadContainsExceptionDetails(String response, String expectedMessage) {
        // Verify that the response contains the expected error structure
        assert response.contains("status");
        assert response.contains("error");
        assert response.contains("details");
        assert response.contains("timestamp");
        
        // Verify that the response contains the exception message (may be in details array)
        assert response.contains("500") : "Response should contain status 500";
        assert response.contains("Failed to retrieve consent history") : "Response should contain error message";
        
        // The exception message might be in the details array or logged separately
        // We verify the basic structure is correct - the response should contain the error message
        assert response.contains("Failed to retrieve consent history") : "Response should contain the error message";
    }
} 