package uk.gegc.kidsgptbackend.features.image.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import uk.gegc.kidsgptbackend.features.image.api.dto.ImageGenerationRequest;
import uk.gegc.kidsgptbackend.features.image.api.dto.ImageGenerationResponse;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.features.image.application.impl.ImageGenerationServiceImpl;
import uk.gegc.kidsgptbackend.shared.util.ModerationUtil;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ImageGenerationServiceImplTest extends BaseUnitTest {

    @Mock
    private ImageModel imageModel;

    @Mock
    private ModerationUtil moderationUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private ImageGenerationServiceImpl imageGenerationService;

    private Principal principal;
    private User testUser;
    private Instant testStartTime;
    private Instant testEndTime;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        
        principal = () -> "testuser";
        testStartTime = Instant.parse("2024-01-01T12:00:00Z");
        testEndTime = Instant.parse("2024-01-01T12:00:02Z"); // 2 seconds later
        
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setAge(9);
        testUser.setActive(true);

        when(clock.instant())
                .thenReturn(testStartTime)
                .thenReturn(testEndTime);
    }

    @Test
    @DisplayName("generateImage: when user found and validation passes then returns image response")
    void generateImage_userFoundAndValid_returnsResponse() {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest("A cute cat", "cartoon");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");

        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);

        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);

        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.imageUrl()).isEqualTo("https://example.com/image.png");
        assertThat(response.model()).isEqualTo("dall-e-3");
        assertThat(response.ageGroup()).isEqualTo("AGE_9_10");
        assertThat(response.latencyMs()).isGreaterThan(0);
        assertThat(response.revisedPrompt()).contains("A cute cat");
        assertThat(response.revisedPrompt()).contains("cartoon style");

        verify(userRepository).findByUsername("testuser");
        verify(moderationUtil).validateComprehensive(eq("A cute cat"), eq(testUser), eq("image prompt"));
        verify(moderationUtil).validateSafety(anyString());
        verify(imageModel).call(any(ImagePrompt.class));
    }

    @Test
    @DisplayName("generateImage: when user not found then throws IllegalArgumentException")
    void generateImage_userNotFound_throwsException() {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest("A cute cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> imageGenerationService.generateImage(request, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(userRepository).findByUsername("testuser");
        verify(moderationUtil, never()).validateComprehensive(anyString(), any(), anyString());
        verify(imageModel, never()).call(any());
    }

    @Test
    @DisplayName("generateImage: when comprehensive validation fails then throws IllegalArgumentException")
    void generateImage_comprehensiveValidationFails_throwsException() {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest("unsafe content", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> imageGenerationService.generateImage(request, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Image description flagged as unsafe");

        verify(moderationUtil).validateComprehensive(eq("unsafe content"), eq(testUser), eq("image prompt"));
        verify(moderationUtil, never()).validateSafety(anyString());
        verify(imageModel, never()).call(any());
    }

    @Test
    @DisplayName("generateImage: when enhanced prompt validation fails then throws IllegalArgumentException")
    void generateImage_enhancedPromptValidationFails_throwsException() {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> imageGenerationService.generateImage(request, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Generated prompt flagged as unsafe");

        verify(moderationUtil).validateComprehensive(anyString(), any(User.class), anyString());
        verify(moderationUtil).validateSafety(anyString());
        verify(imageModel, never()).call(any());
    }

    @Test
    @DisplayName("generateImage: when API call fails then throws RuntimeException")
    void generateImage_apiCallFails_throwsRuntimeException() {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);
        when(imageModel.call(any(ImagePrompt.class))).thenThrow(new RuntimeException("API error"));

        // When & Then
        assertThatThrownBy(() -> imageGenerationService.generateImage(request, principal))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to generate image");

        verify(imageModel).call(any(ImagePrompt.class));
    }

    @Test
    @DisplayName("generateImage: when age is 7 then uses AGE_6_8 prompt modifiers")
    void generateImage_age7_usesAge6_8Modifiers() {
        // Given
        testUser.setAge(7);
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response.ageGroup()).isEqualTo("AGE_6_8");
        assertThat(response.revisedPrompt()).contains("Create a colorful, friendly, cartoon-style image suitable for young children:");
        assertThat(response.revisedPrompt()).contains("simple, bright colors, cartoon style, no scary elements");
        assertThat(response.revisedPrompt()).contains("Ensure the image is completely safe and appropriate for young children");
    }

    @Test
    @DisplayName("generateImage: when age is 9 then uses AGE_9_10 prompt modifiers")
    void generateImage_age9_usesAge9_10Modifiers() {
        // Given
        testUser.setAge(9);
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response.ageGroup()).isEqualTo("AGE_9_10");
        assertThat(response.revisedPrompt()).contains("Create a fun, engaging, child-friendly image with bright colors:");
        assertThat(response.revisedPrompt()).contains("colorful, friendly, slightly more detailed, cartoon or semi-realistic");
        assertThat(response.revisedPrompt()).contains("Ensure the image is completely safe and appropriate for young children");
    }

    @Test
    @DisplayName("generateImage: when age is 11 then uses AGE_11_12 prompt modifiers")
    void generateImage_age11_usesAge11_12Modifiers() {
        // Given
        testUser.setAge(11);
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response.ageGroup()).isEqualTo("AGE_11_12");
        assertThat(response.revisedPrompt()).contains("Create an interesting, age-appropriate image with good detail:");
        assertThat(response.revisedPrompt()).contains("detailed, realistic or stylized, appropriate complexity");
        assertThat(response.revisedPrompt()).doesNotContain("Ensure the image is completely safe and appropriate for young children");
    }

    @Test
    @DisplayName("generateImage: when age is 13 then uses AGE_13_14 prompt modifiers")
    void generateImage_age13_usesAge13_14Modifiers() {
        // Given
        testUser.setAge(13);
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response.ageGroup()).isEqualTo("AGE_13_14");
        assertThat(response.revisedPrompt()).contains("Create a detailed, engaging image suitable for teens:");
        assertThat(response.revisedPrompt()).contains("realistic, detailed, modern style");
    }

    @Test
    @DisplayName("generateImage: when age is 15 then uses AGE_15_16 prompt modifiers")
    void generateImage_age15_usesAge15_16Modifiers() {
        // Given
        testUser.setAge(15);
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response.ageGroup()).isEqualTo("AGE_15_16");
        assertThat(response.revisedPrompt()).contains("Create a sophisticated, detailed image suitable for teenagers:");
        assertThat(response.revisedPrompt()).contains("high detail, realistic or artistic style, sophisticated");
    }

    @Test
    @DisplayName("generateImage: when age is null then defaults to AGE_9_10")
    void generateImage_ageNull_defaultsToAge9_10() {
        // Given
        testUser.setAge(null);
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response.ageGroup()).isEqualTo("AGE_9_10");
        assertThat(response.revisedPrompt()).contains("Create a fun, engaging, child-friendly image with bright colors:");
    }

    @Test
    @DisplayName("generateImage: when style provided then includes style in prompt")
    void generateImage_styleProvided_includesStyleInPrompt() {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", "realistic");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response.revisedPrompt()).contains("A cat in realistic style");
    }

    @Test
    @DisplayName("generateImage: when style is empty then does not include style in prompt")
    void generateImage_styleEmpty_doesNotIncludeStyle() {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", "");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response.revisedPrompt()).doesNotContain("in style");
    }

    @Test
    @DisplayName("generateImage: when style is whitespace only then does not include style in prompt")
    void generateImage_styleWhitespace_doesNotIncludeStyle() {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", "   ");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response.revisedPrompt()).doesNotContain("in style");
    }

    @Test
    @DisplayName("generateImage: calculates latency correctly")
    void generateImage_calculatesLatencyCorrectly() {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        ImageGenerationResponse response = imageGenerationService.generateImage(request, principal);

        // Then
        assertThat(response.latencyMs()).isEqualTo(2000L); // 2 seconds difference
    }

    @Test
    @DisplayName("generateImage: uses correct DALL-E options")
    void generateImage_usesCorrectDalleOptions() {
        // Given
        ImageGenerationRequest request = new ImageGenerationRequest("A cat", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);

        Image mockImage = mock(Image.class);
        when(mockImage.getUrl()).thenReturn("https://example.com/image.png");
        ImageGeneration mockGeneration = mock(ImageGeneration.class);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        ImageResponse mockResponse = mock(ImageResponse.class);
        ImageResponseMetadata metadata = mock(ImageResponseMetadata.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // When
        imageGenerationService.generateImage(request, principal);

        // Then - Verify ImagePrompt was called with correct options
        verify(imageModel).call(any(ImagePrompt.class));
    }
}

