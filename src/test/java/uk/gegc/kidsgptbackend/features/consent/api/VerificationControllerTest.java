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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationInitiateRequest;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationInitiationResult;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationStatusResponse;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationVerifyRequest;
import uk.gegc.kidsgptbackend.features.consent.application.ParentVerificationService;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationStatus;
import uk.gegc.kidsgptbackend.shared.config.ClockConfig;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = VerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfig.class)
@DisplayName("VerificationController Unit Tests")
class VerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParentVerificationService parentVerificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID parentId;
    private UUID verificationId;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        parentId = UUID.randomUUID();
        verificationId = UUID.randomUUID();
        now = OffsetDateTime.now();
    }

    @Test
    @DisplayName("initiateVerification: should return 201 when verification is newly created")
    void initiateVerification_newlyCreated_returns201() throws Exception {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "parent@example.com"
        );

        VerificationStatusResponse statusResponse = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.PENDING,
                1,
                now.plusMinutes(30),
                null,
                now
        );

        VerificationInitiationResult result = new VerificationInitiationResult(
                statusResponse,
                true // newlyCreated = true
        );

        when(parentVerificationService.initiateVerification(any(VerificationInitiateRequest.class)))
                .thenReturn(result);

        // When & Then
        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Verification-Id", verificationId.toString()))
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(jsonPath("$.verificationId").value(verificationId.toString()))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
    }

    @Test
    @DisplayName("initiateVerification: should return 200 when verification already exists")
    void initiateVerification_existingPending_returns200() throws Exception {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.EMAIL,
                "parent@example.com"
        );

        VerificationStatusResponse statusResponse = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.PENDING,
                1,
                now.plusMinutes(30),
                null,
                now
        );

        VerificationInitiationResult result = new VerificationInitiationResult(
                statusResponse,
                false // newlyCreated = false
        );

        when(parentVerificationService.initiateVerification(any(VerificationInitiateRequest.class)))
                .thenReturn(result);

        // When & Then
        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Verification-Id", verificationId.toString()))
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(jsonPath("$.verificationId").value(verificationId.toString()))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
    }

    @Test
    @DisplayName("verifyParent: should return 200 with verification status")
    void verifyParent_validCode_returns200() throws Exception {
        // Given
        VerificationVerifyRequest request = new VerificationVerifyRequest(
                verificationId,
                "123456"
        );

        VerificationStatusResponse statusResponse = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.VERIFIED,
                1,
                now.plusMinutes(30),
                now,
                now
        );

        when(parentVerificationService.verifyParent(any(VerificationVerifyRequest.class)))
                .thenReturn(statusResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/verification/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Verification-Id", verificationId.toString()))
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(jsonPath("$.verificationId").value(verificationId.toString()))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
    }

    @Test
    @DisplayName("getVerificationStatus: should return 200 with verification status")
    void getVerificationStatus_validId_returns200() throws Exception {
        // Given
        VerificationStatusResponse statusResponse = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.PENDING,
                1,
                now.plusMinutes(30),
                null,
                now
        );

        when(parentVerificationService.getVerificationStatus(verificationId))
                .thenReturn(statusResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/verification/status/{verificationId}", verificationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(jsonPath("$.verificationId").value(verificationId.toString()))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
    }

    @Test
    @DisplayName("initiateVerification: should handle SMS method")
    void initiateVerification_smsMethod_handlesCorrectly() throws Exception {
        // Given
        VerificationInitiateRequest request = new VerificationInitiateRequest(
                parentId,
                VerificationMethod.SMS,
                "+1234567890"
        );

        VerificationStatusResponse statusResponse = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.SMS,
                VerificationStatus.PENDING,
                1,
                now.plusMinutes(30),
                null,
                now
        );

        VerificationInitiationResult result = new VerificationInitiationResult(
                statusResponse,
                true
        );

        when(parentVerificationService.initiateVerification(any(VerificationInitiateRequest.class)))
                .thenReturn(result);

        // When & Then
        mockMvc.perform(post("/api/v1/verification/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationMethod").value("SMS"));
    }

    @Test
    @DisplayName("getVerificationStatus: should handle different verification statuses")
    void getVerificationStatus_differentStatuses_handlesCorrectly() throws Exception {
        // Test VERIFIED status
        VerificationStatusResponse verifiedResponse = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.VERIFIED,
                1,
                now.plusMinutes(30),
                now,
                now
        );

        when(parentVerificationService.getVerificationStatus(verificationId))
                .thenReturn(verifiedResponse);

        mockMvc.perform(get("/api/v1/verification/status/{verificationId}", verificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        // Test EXPIRED status
        VerificationStatusResponse expiredResponse = new VerificationStatusResponse(
                verificationId,
                parentId,
                VerificationMethod.EMAIL,
                VerificationStatus.EXPIRED,
                3,
                now.minusMinutes(5),
                null,
                now
        );

        when(parentVerificationService.getVerificationStatus(verificationId))
                .thenReturn(expiredResponse);

        mockMvc.perform(get("/api/v1/verification/status/{verificationId}", verificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("EXPIRED"));
    }
}

