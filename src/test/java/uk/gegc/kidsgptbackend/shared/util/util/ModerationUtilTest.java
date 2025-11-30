package uk.gegc.kidsgptbackend.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.moderation.*;
import uk.gegc.kidsgptbackend.exception.ModerationServiceException;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.model.user.User;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModerationUtil Tests")
class ModerationUtilTest {

    @Mock
    private ModerationModel moderationModel;
    
    @Mock
    private ChatClient chatClient;
    
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    
    @Mock
    private ChatClient.CallResponseSpec callSpec;

    private ModerationUtil moderationUtil;

    @BeforeEach
    void setUp() {
        moderationUtil = new ModerationUtil(moderationModel, chatClient);
        
        // Setup ChatClient mock chain (using lenient to avoid unnecessary stubbing errors)
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callSpec);
    }

    // Helper methods for creating mock responses
    private ModerationResponse createSafeResponse() {
        ModerationResult result = new ModerationResult.Builder()
                .flagged(false)
                .build();
        Moderation moderation = Moderation.builder()
                .results(List.of(result))
                .build();
        return new ModerationResponse(new org.springframework.ai.moderation.Generation(moderation));
    }

    private ModerationResponse createUnsafeResponse() {
        ModerationResult result = new ModerationResult.Builder()
                .flagged(true)
                .build();
        Moderation moderation = Moderation.builder()
                .results(List.of(result))
                .build();
        return new ModerationResponse(new org.springframework.ai.moderation.Generation(moderation));
    }

    private User createTestUser(int age) {
        User user = new User();
        user.setAge(age);
        user.setUsername("testuser");
        return user;
    }

    @Test
    @DisplayName("validateSafety: should return true for safe content")
    void validateSafety_safeContent_returnsTrue() {
        // Arrange
        String safeContent = "This is a safe message about puppies";
        when(moderationModel.call(any(ModerationPrompt.class))).thenReturn(createSafeResponse());

        // Act
        boolean result = moderationUtil.validateSafety(safeContent);

        // Assert
        assertThat(result).isTrue();
        verify(moderationModel).call(any(ModerationPrompt.class));
    }

    @Test
    @DisplayName("validateSafety: should return false for unsafe content")
    void validateSafety_unsafeContent_returnsFalse() {
        // Arrange
        String unsafeContent = "This is inappropriate content";
        when(moderationModel.call(any(ModerationPrompt.class))).thenReturn(createUnsafeResponse());

        // Act
        boolean result = moderationUtil.validateSafety(unsafeContent);

        // Assert
        assertThat(result).isFalse();
        verify(moderationModel).call(any(ModerationPrompt.class));
    }

    @Test
    @DisplayName("validateSafety: should throw ModerationServiceException on service error")
    void validateSafety_serviceError_throwsException() {
        // Arrange
        String content = "test content";
        when(moderationModel.call(any(ModerationPrompt.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Act & Assert
        assertThatThrownBy(() -> moderationUtil.validateSafety(content))
                .isInstanceOf(ModerationServiceException.class)
                .hasMessage("Moderation service unavailable");
    }

    @Test
    @DisplayName("validateSafetyForAge: should return true for age-appropriate content")
    void validateSafetyForAge_appropriateContent_returnsTrue() {
        // Arrange
        String content = "Let's learn about animals";
        AgeGroup ageGroup = AgeGroup.AGE_6_8;
        when(moderationModel.call(any(ModerationPrompt.class))).thenReturn(createSafeResponse());

        // Act
        boolean result = moderationUtil.validateSafetyForAge(content, ageGroup);

        // Assert
        assertThat(result).isTrue();
        verify(moderationModel).call(any(ModerationPrompt.class));
    }

    @Test
    @DisplayName("validateSafetyForAge: should return false for age-inappropriate content")
    void validateSafetyForAge_inappropriateContent_returnsFalse() {
        // Arrange
        String content = "Complex adult topic";
        AgeGroup ageGroup = AgeGroup.AGE_6_8;
        when(moderationModel.call(any(ModerationPrompt.class))).thenReturn(createUnsafeResponse());

        // Act
        boolean result = moderationUtil.validateSafetyForAge(content, ageGroup);

        // Assert
        assertThat(result).isFalse();
        verify(moderationModel).call(any(ModerationPrompt.class));
    }

    @Test
    @DisplayName("validateContentWithAI: should pass for safe content")
    void validateContentWithAI_safeContent_passes() {
        // Arrange
        String content = "Draw a happy cat";
        User user = createTestUser(8);
        when(callSpec.content()).thenReturn("SAFE");

        // Act & Assert
        assertThatCode(() -> moderationUtil.validateContentWithAI(content, user, "image prompt"))
                .doesNotThrowAnyException();
        
        verify(chatClient).prompt();
        verify(requestSpec).system(anyString());
        verify(requestSpec).user(anyString());
        verify(requestSpec).call();
        verify(callSpec).content();
    }

    @Test
    @DisplayName("validateContentWithAI: should throw exception for unsafe content")
    void validateContentWithAI_unsafeContent_throwsException() {
        // Arrange
        String content = "Inappropriate content";
        User user = createTestUser(8);
        when(callSpec.content()).thenReturn("UNSAFE: contains inappropriate themes");

        // Act & Assert
        assertThatThrownBy(() -> moderationUtil.validateContentWithAI(content, user, "image prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inappropriate for age group");
    }

    @Test
    @DisplayName("validateContentWithAI: should validate content length")
    void validateContentWithAI_invalidLength_throwsException() {
        // Arrange
        User user = createTestUser(8);

        // Act & Assert - empty content
        assertThatThrownBy(() -> moderationUtil.validateContentWithAI("", user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Content cannot be null or empty");

        // Act & Assert - null content
        assertThatThrownBy(() -> moderationUtil.validateContentWithAI(null, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Content cannot be null or empty");

        // Act & Assert - too long content
        String longContent = "a".repeat(1001);
        assertThatThrownBy(() -> moderationUtil.validateContentWithAI(longContent, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Content too long (max 1000 characters)");

        // Act & Assert - too short content
        assertThatThrownBy(() -> moderationUtil.validateContentWithAI("ab", user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Content too short (min 3 characters)");
    }

    @Test
    @DisplayName("validateContentWithAI: should handle AI service errors gracefully")
    void validateContentWithAI_aiServiceError_handlesGracefully() {
        // Arrange
        String content = "Test content";
        User user = createTestUser(8);
        when(callSpec.content()).thenThrow(new RuntimeException("AI service error"));

        // Act & Assert - should not throw exception, just log warning
        assertThatCode(() -> moderationUtil.validateContentWithAI(content, user))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateContentWithAI: should use default content type")
    void validateContentWithAI_defaultContentType_works() {
        // Arrange
        String content = "Test content";
        User user = createTestUser(8);
        when(callSpec.content()).thenReturn("SAFE");

        // Act & Assert
        assertThatCode(() -> moderationUtil.validateContentWithAI(content, user))
                .doesNotThrowAnyException();

        verify(requestSpec).user(contains("general content"));
    }

    @Test
    @DisplayName("validateComprehensive: should return true when all validations pass")
    void validateComprehensive_allValidationsPass_returnsTrue() {
        // Arrange
        String content = "Safe content";
        User user = createTestUser(8);
        when(moderationModel.call(any(ModerationPrompt.class))).thenReturn(createSafeResponse());
        when(callSpec.content()).thenReturn("SAFE");

        // Act
        boolean result = moderationUtil.validateComprehensive(content, user, "test content");

        // Assert
        assertThat(result).isTrue();
        verify(moderationModel).call(any(ModerationPrompt.class));
        verify(callSpec).content();
    }

    @Test
    @DisplayName("validateComprehensive: should return false when basic moderation fails")
    void validateComprehensive_basicModerationFails_returnsFalse() {
        // Arrange
        String content = "Unsafe content";
        User user = createTestUser(8);
        when(moderationModel.call(any(ModerationPrompt.class))).thenReturn(createUnsafeResponse());

        // Act
        boolean result = moderationUtil.validateComprehensive(content, user, "test content");

        // Assert
        assertThat(result).isFalse();
        verify(moderationModel).call(any(ModerationPrompt.class));
        // AI validation should not be called if basic moderation fails
        verify(chatClient, never()).prompt();
    }

    @Test
    @DisplayName("validateComprehensive: should return false when AI validation fails")
    void validateComprehensive_aiValidationFails_returnsFalse() {
        // Arrange
        String content = "Content that passes basic but fails AI";
        User user = createTestUser(8);
        when(moderationModel.call(any(ModerationPrompt.class))).thenReturn(createSafeResponse());
        when(callSpec.content()).thenReturn("UNSAFE: inappropriate for age");

        // Act
        boolean result = moderationUtil.validateComprehensive(content, user, "test content");

        // Assert
        assertThat(result).isFalse();
        verify(moderationModel).call(any(ModerationPrompt.class));
        verify(callSpec).content();
    }

    @Test
    @DisplayName("Age group validation: should handle different age groups correctly")
    void ageGroupValidation_differentAges_handlesCorrectly() {
        // Arrange
        when(callSpec.content()).thenReturn("SAFE");
        
        // Test different ages map to correct age groups
        User youngChild = createTestUser(7);
        User preteen = createTestUser(12);
        User teenager = createTestUser(15);

        // Act & Assert
        assertThatCode(() -> moderationUtil.validateContentWithAI("simple content", youngChild, "content"))
                .doesNotThrowAnyException();
        
        assertThatCode(() -> moderationUtil.validateContentWithAI("complex content", preteen, "content"))
                .doesNotThrowAnyException();
        
        assertThatCode(() -> moderationUtil.validateContentWithAI("sophisticated content", teenager, "content"))
                .doesNotThrowAnyException();

        // Verify system prompts are called with different age groups
        verify(requestSpec, times(3)).system(anyString());
    }

    @Test
    @DisplayName("Age group validation: should handle null age gracefully")
    void ageGroupValidation_nullAge_usesDefault() {
        // Arrange
        User userWithoutAge = new User();
        userWithoutAge.setUsername("testuser");
        // age is null
        when(callSpec.content()).thenReturn("SAFE");

        // Act & Assert
        assertThatCode(() -> moderationUtil.validateContentWithAI("content", userWithoutAge))
                .doesNotThrowAnyException();

        // Should default to AGE_9_10
        verify(requestSpec).system(contains("9-10 year old"));
    }
} 