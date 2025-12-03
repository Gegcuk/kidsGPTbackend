package uk.gegc.kidsgptbackend.features.consent.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import uk.gegc.kidsgptbackend.features.consent.application.ConsentService;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

@WebMvcTest(controllers = ConsentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(uk.gegc.kidsgptbackend.shared.config.ClockConfig.class)
class ConsentControllerStatusIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConsentService consentService;

    @AfterEach
    void clearSecurityContext() {
        Mockito.reset(consentService);
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(consentService);
    }

    @Test
    void invalidUuid_returns400() throws Exception {
        // Given: path verificationId = "not-a-uuid"
        String invalidVerificationId = "not-a-uuid";

        // Mock service to throw the expected exception when called with invalid UUID
        Mockito.when(consentService.getConsentStatus(invalidVerificationId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, 
                        "Invalid verification ID format"));

        // When / Then: GET /api/v1/consent/status/not-a-uuid
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", invalidVerificationId)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isBadRequest())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.title").value("Validation Failed"))
           .andExpect(jsonPath("$.status").value(400))
           .andExpect(jsonPath("$.detail").value("Invalid verification ID format"));

        // And: service was called and handled the UUID validation
        Mockito.verify(consentService).getConsentStatus(invalidVerificationId);
    }

    @Test
    void verificationNotFound_returns404() throws Exception {
        // Given: service throws ResponseStatusException(404, "Verification not found")
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        
        Mockito.when(consentService.getConsentStatus(validUuid))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, 
                        "Verification not found"));

        // When: GET /status/{uuid}
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", validUuid)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isNotFound())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.title").value("Resource Not Found"))
           .andExpect(jsonPath("$.status").value(404))
           .andExpect(jsonPath("$.detail").value("Verification not found"));

        // And: service was called
        Mockito.verify(consentService).getConsentStatus(validUuid);
    }

    @Test
    void verificationExpired_returns410() throws Exception {
        // Given: service throws ResponseStatusException(410, "Verification has expired")
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        
        Mockito.when(consentService.getConsentStatus(validUuid))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.GONE, 
                        "Verification has expired"));

        // When: GET /status/{uuid}
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", validUuid)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isGone())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.title").value("Gone"))
           .andExpect(jsonPath("$.status").value(410))
           .andExpect(jsonPath("$.detail").value("Verification has expired"));

        // And: service was called
        Mockito.verify(consentService).getConsentStatus(validUuid);
    }

    @Test
    void verificationNotCompleted_returns409() throws Exception {
        // Given: service throws ResponseStatusException(409, "Verification not completed")
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        
        Mockito.when(consentService.getConsentStatus(validUuid))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, 
                        "Verification not completed"));

        // When: GET /status/{uuid}
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", validUuid)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isConflict())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.title").value("Conflict"))
           .andExpect(jsonPath("$.status").value(409))
           .andExpect(jsonPath("$.detail").value("Verification not completed"));

        // And: service was called
        Mockito.verify(consentService).getConsentStatus(validUuid);
    }

    @Test
    void emptyStatusNoConsents_returns200WithReconsentNeededTrue() throws Exception {
        // Given: service returns empty status with reconsentNeeded=true
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse emptyResponse = 
            new uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse(
                List.of(), // empty latestByType array
                true,      // reconsentNeeded = true
                null       // consentId = null
            );
        
        Mockito.when(consentService.getConsentStatus(validUuid))
                .thenReturn(emptyResponse);

        // When: GET /status/{uuid}
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", validUuid)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("application/json"))
           .andExpect(jsonPath("$.latestByType").isArray())
           .andExpect(jsonPath("$.latestByType").isEmpty())
           .andExpect(jsonPath("$.reconsentNeeded").value(true))
           .andExpect(jsonPath("$.consentId").isEmpty());

        // And: service was called
        Mockito.verify(consentService).getConsentStatus(validUuid);
    }

    @Test
    void populatedStatusAllCurrent_returns200WithReconsentNeededFalse() throws Exception {
        // Given: service returns populated status with reconsentNeeded=false
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse.ConsentStatusByType entry1 = 
            new uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse.ConsentStatusByType(
                uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.PRIVACY_POLICY,
                "1.2.3",
                uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED,
                java.time.LocalDateTime.now(),
                "https://kidsgpt.club/policies/privacy/en-GB"
            );
        
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse.ConsentStatusByType entry2 = 
            new uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse.ConsentStatusByType(
                uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType.TERMS_OF_SERVICE,
                "2.0.0",
                uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus.GRANTED,
                java.time.LocalDateTime.now(),
                "https://kidsgpt.club/policies/terms"
            );
        
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse populatedResponse = 
            new uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse(
                List.of(entry1, entry2), // populated latestByType array with 2 entries
                false,                   // reconsentNeeded = false
                java.util.UUID.randomUUID() // consentId = some UUID
            );
        
        Mockito.when(consentService.getConsentStatus(validUuid))
                .thenReturn(populatedResponse);

        // When: GET /status/{uuid}
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", validUuid)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("application/json"))
           .andExpect(jsonPath("$.latestByType").isArray())
           .andExpect(jsonPath("$.latestByType").isNotEmpty())
           .andExpect(jsonPath("$.latestByType").value(org.hamcrest.Matchers.hasSize(2)))
           .andExpect(jsonPath("$.reconsentNeeded").value(false))
           .andExpect(jsonPath("$.consentId").isNotEmpty())
           // Verify enums are serialized as strings
           .andExpect(jsonPath("$.latestByType[0].type").value("PRIVACY_POLICY"))
           .andExpect(jsonPath("$.latestByType[0].status").value("GRANTED"))
           .andExpect(jsonPath("$.latestByType[1].type").value("TERMS_OF_SERVICE"))
           .andExpect(jsonPath("$.latestByType[1].status").value("GRANTED"));

        // And: service was called
        Mockito.verify(consentService).getConsentStatus(validUuid);
    }

    @Test
    void includesMostRecentConsentId() throws Exception {
        // Given: service returns some UUID in consentId
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        java.util.UUID expectedConsentId = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse responseWithSpecificConsentId = 
            new uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse(
                List.of(), // empty latestByType array
                true,      // reconsentNeeded = true
                expectedConsentId // consentId = specific UUID
            );
        
        Mockito.when(consentService.getConsentStatus(validUuid))
                .thenReturn(responseWithSpecificConsentId);

        // When: GET /status/{uuid}
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", validUuid)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("application/json"))
           .andExpect(jsonPath("$.consentId").value(expectedConsentId.toString()));

        // And: service was called
        Mockito.verify(consentService).getConsentStatus(validUuid);
    }

    @Test
    void contentTypes() throws Exception {
        // Given: service returns success response
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        
        uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse successResponse = 
            new uk.gegc.kidsgptbackend.features.consent.api.dto.ConsentStatusResponse(
                List.of(), // empty latestByType array
                true,      // reconsentNeeded = true
                null       // consentId = null
            );
        
        Mockito.when(consentService.getConsentStatus(validUuid))
                .thenReturn(successResponse);

        // When: GET /status/{uuid} for success case
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", validUuid)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentType("application/json"));

        // And: service was called
        Mockito.verify(consentService).getConsentStatus(validUuid);
        
        // Reset mock for error case
        Mockito.reset(consentService);
        
        // Given: service throws error
        Mockito.when(consentService.getConsentStatus(validUuid))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, 
                        "Verification not found"));

        // When: GET /status/{uuid} for error case
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", validUuid)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isNotFound())
           .andExpect(content().contentType("application/problem+json"));

        // And: service was called
        Mockito.verify(consentService).getConsentStatus(validUuid);
    }

    @Test
    void controllerDoesNotSwallowExceptions() throws Exception {
        // Given: service throws unchecked exception
        String validUuid = "550e8400-e29b-41d4-a716-446655440000"; // Valid UUID format
        
        Mockito.when(consentService.getConsentStatus(validUuid))
                .thenThrow(new RuntimeException("Unexpected database error"));

        // When: GET /status/{uuid}
        mockMvc.perform(get("/api/v1/consent/status/{verificationId}", validUuid)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
           .andExpect(status().isInternalServerError())
           .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
           .andExpect(jsonPath("$.title").value("Internal Server Error"))
           .andExpect(jsonPath("$.status").value(500))
           .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));

        // And: service was called
        Mockito.verify(consentService).getConsentStatus(validUuid);
    }
} 