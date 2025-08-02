package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.dto.image.ImageGenerationRequest;
import uk.gegc.kidsgptbackend.dto.image.ImageGenerationResponse;
import uk.gegc.kidsgptbackend.service.image.ImageGenerationService;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageGenerationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImageGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageGenerationService imageGenerationService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should generate image successfully for authenticated user")
    @WithMockUser(username = "testuser")
    void shouldGenerateImageSuccessfully() throws Exception {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest(
                "A cute cartoon cat playing with a ball",
                "cartoon"
        );
        
        ImageGenerationResponse response = new ImageGenerationResponse(
                "https://example.com/generated-image.png",
                "Create a colorful, friendly, cartoon-style image suitable for young children: A cute cartoon cat playing with a ball in cartoon style. colorful, friendly, slightly more detailed, cartoon or semi-realistic",
                "dall-e-3",
                2500L,
                "AGE_9_10"
        );

        when(imageGenerationService.generateImage(eq(request), any(Principal.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/generate-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/generated-image.png"))
                .andExpect(jsonPath("$.model").value("dall-e-3"))
                .andExpect(jsonPath("$.ageGroup").value("AGE_9_10"));
    }

    @Test
    @DisplayName("Should return unauthorized for unauthenticated user")
    void shouldReturnUnauthorizedForUnauthenticatedUser() throws Exception {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest(
                "A cute cartoon cat",
                "cartoon"
        );

        // When & Then
        mockMvc.perform(post("/api/v1/generate-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return bad request for invalid input")
    @WithMockUser(username = "testuser")
    void shouldReturnBadRequestForInvalidInput() throws Exception {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest(
                "", // Empty description should fail validation
                "cartoon"
        );

        // When & Then
        mockMvc.perform(post("/api/v1/generate-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return bad request when service throws IllegalArgumentException")
    @WithMockUser(username = "testuser")
    void shouldReturnBadRequestForUnsafeContent() throws Exception {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest(
                "A dangerous scene",
                "realistic"
        );

        when(imageGenerationService.generateImage(eq(request), any(Principal.class)))
                .thenThrow(new IllegalArgumentException("Image description flagged as unsafe"));

        // When & Then
        mockMvc.perform(post("/api/v1/generate-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
} 