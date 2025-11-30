package uk.gegc.kidsgptbackend.features.tips.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.features.tips.api.dto.DailyTipDto;
import uk.gegc.kidsgptbackend.features.tips.application.impl.DailyTipServiceImpl;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.shared.util.ModerationUtil;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DailyTipServiceImpl}.
 * <p>
 * Tests the service implementation with mocked dependencies to verify
 * business logic, error handling, and fallback scenarios.
 */
class DailyTipServiceImplTest extends BaseUnitTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ModerationUtil moderationUtil;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private AssistantMessage message;

    @Mock
    private Resource ageGroupResource;

    @InjectMocks
    private DailyTipServiceImpl dailyTipService;

    private Resource categoriesResource;
    private Resource topicsResource;
    private Resource fallbackContentResource;

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        
        // Create ByteArrayResources for @Value injected resources
        categoriesResource = new ByteArrayResource("science\nhistory\nnature".getBytes(StandardCharsets.UTF_8));
        topicsResource = new ByteArrayResource("space and planets\nanimals and wildlife".getBytes(StandardCharsets.UTF_8));
        fallbackContentResource = new ByteArrayResource(
            "FALLBACK_FACT=Did you know that honey never spoils?\nFALLBACK_PROMPT=Generate a fun fact\nUSER_MESSAGE_TEMPLATE=Give me a fun fact about %s!"
                .getBytes(StandardCharsets.UTF_8)
        );
        
        // Inject @Value resources using ReflectionTestUtils
        ReflectionTestUtils.setField(dailyTipService, "categoriesResource", categoriesResource);
        ReflectionTestUtils.setField(dailyTipService, "topicsResource", topicsResource);
        ReflectionTestUtils.setField(dailyTipService, "fallbackContentResource", fallbackContentResource);
        
        // Setup default mock behavior for ChatClient chain
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
    }

    @Test
    @DisplayName("getDailyTip: returns tip for default age group")
    void getDailyTip_returnsTipForDefaultAgeGroup() throws IOException {
        // Given
        String prompt = "Generate a fun fact for age 9-10";
        String fact = "Did you know that honey never spoils?";
        
        setupAgeGroupResource(prompt);
        setupChatResponse(fact);
        when(moderationUtil.validateSafetyForAge(fact, AgeGroup.AGE_9_10)).thenReturn(true);

        // When
        DailyTipDto result = dailyTipService.getDailyTip();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFact()).isEqualTo(fact);
        assertThat(result.getCategory()).isIn("science", "history", "nature");
        assertThat(result.getAgeGroup()).isEqualTo("AGE_9_10");
    }

    @Test
    @DisplayName("getDailyTip with ageGroup: returns tip for specific age group")
    void getDailyTip_withAgeGroup_returnsTipForSpecificAge() throws IOException {
        // Given
        String prompt = "Generate a fun fact for age 6-8";
        String fact = "Did you know that octopuses have three hearts?";
        
        setupAgeGroupResource(prompt);
        setupChatResponse(fact);
        when(moderationUtil.validateSafetyForAge(fact, AgeGroup.AGE_6_8)).thenReturn(true);

        // When
        DailyTipDto result = dailyTipService.getDailyTip(AgeGroup.AGE_6_8);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFact()).isEqualTo(fact);
        assertThat(result.getCategory()).isIn("science", "history", "nature");
        assertThat(result.getAgeGroup()).isEqualTo("AGE_6_8");
    }

    @Test
    @DisplayName("getDailyTip: uses fallback when moderation fails")
    void getDailyTip_moderationFails_usesFallback() throws IOException {
        // Given
        String prompt = "Generate a fun fact for age 9-10";
        String unsafeFact = "Unsafe fact content";
        
        setupAgeGroupResource(prompt);
        setupChatResponse(unsafeFact);
        when(moderationUtil.validateSafetyForAge(unsafeFact, AgeGroup.AGE_9_10)).thenReturn(false);

        // When
        DailyTipDto result = dailyTipService.getDailyTip();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFact()).contains("honey never spoils");
        assertThat(result.getCategory()).isIn("science", "history", "nature");
        assertThat(result.getAgeGroup()).isEqualTo("AGE_9_10");
    }

    @Test
    @DisplayName("getDailyTip: uses fallback when chat client throws exception")
    void getDailyTip_chatClientThrowsException_usesFallback() throws IOException {
        // Given
        String prompt = "Generate a fun fact for age 9-10";
        
        setupAgeGroupResource(prompt);
        when(callResponseSpec.chatResponse()).thenThrow(new RuntimeException("Chat client error"));

        // When
        DailyTipDto result = dailyTipService.getDailyTip();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFact()).contains("honey never spoils");
        assertThat(result.getCategory()).isIn("science", "history", "nature");
        assertThat(result.getAgeGroup()).isEqualTo("AGE_9_10");
    }

    @Test
    @DisplayName("getDailyTip: uses fallback when resource loading fails")
    void getDailyTip_resourceLoadingFails_usesFallback() throws IOException {
        // Given
        when(resourceLoader.getResource(anyString())).thenReturn(ageGroupResource);
        when(ageGroupResource.exists()).thenReturn(false);
        when(ageGroupResource.getInputStream()).thenThrow(new IOException("Resource not found"));

        // When
        DailyTipDto result = dailyTipService.getDailyTip();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFact()).contains("honey never spoils");
        assertThat(result.getCategory()).isIn("science", "history", "nature");
        assertThat(result.getAgeGroup()).isEqualTo("AGE_9_10");
    }

    @Test
    @DisplayName("getDailyTip: handles all age groups correctly")
    void getDailyTip_allAgeGroups_handlesCorrectly() throws IOException {
        // Given
        String fact = "Test fact";
        
        for (AgeGroup ageGroup : AgeGroup.values()) {
            String prompt = "Prompt for " + ageGroup;
            setupAgeGroupResource(prompt);
            setupChatResponse(fact);
            when(moderationUtil.validateSafetyForAge(fact, ageGroup)).thenReturn(true);

            // When
            DailyTipDto result = dailyTipService.getDailyTip(ageGroup);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFact()).isEqualTo(fact);
            assertThat(result.getAgeGroup()).isEqualTo(ageGroup.name());
            assertThat(result.getCategory()).isIn("science", "history", "nature");
        }
    }

    @Test
    @DisplayName("getDailyTip: uses fallback when chat response is null")
    void getDailyTip_chatResponseIsNull_usesFallback() throws IOException {
        // Given
        String prompt = "Generate a fun fact for age 9-10";
        
        setupAgeGroupResource(prompt);
        when(callResponseSpec.chatResponse()).thenReturn(null);

        // When
        DailyTipDto result = dailyTipService.getDailyTip();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFact()).contains("honey never spoils");
        assertThat(result.getCategory()).isIn("science", "history", "nature");
    }

    @Test
    @DisplayName("getDailyTip: uses fallback when generation output is null")
    void getDailyTip_generationOutputIsNull_usesFallback() throws IOException {
        // Given
        String prompt = "Generate a fun fact for age 9-10";
        
        setupAgeGroupResource(prompt);
        when(chatResponse.getResult()).thenReturn(null);

        // When
        DailyTipDto result = dailyTipService.getDailyTip();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFact()).contains("honey never spoils");
        assertThat(result.getCategory()).isIn("science", "history", "nature");
    }

    @Test
    @DisplayName("getDailyTip: uses fallback when message text is null")
    void getDailyTip_messageTextIsNull_usesFallback() throws IOException {
        // Given
        String prompt = "Generate a fun fact for age 9-10";
        
        setupAgeGroupResource(prompt);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn(null);

        // When
        DailyTipDto result = dailyTipService.getDailyTip();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFact()).contains("honey never spoils");
        assertThat(result.getCategory()).isIn("science", "history", "nature");
    }

    @Test
    @DisplayName("getDailyTip: uses fallback categories when resource loading fails")
    void getDailyTip_categoriesResourceFails_usesFallbackCategories() throws IOException {
        // Given
        String prompt = "Generate a fun fact for age 9-10";
        String fact = "Test fact";
        
        // Set categoriesResource to null to trigger fallback
        ReflectionTestUtils.setField(dailyTipService, "categoriesResource", null);
        setupAgeGroupResource(prompt);
        setupChatResponse(fact);
        when(moderationUtil.validateSafetyForAge(fact, AgeGroup.AGE_9_10)).thenReturn(true);

        // When
        DailyTipDto result = dailyTipService.getDailyTip();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFact()).isEqualTo(fact);
        // Should use one of the fallback categories
        assertThat(result.getCategory()).isIn(
            "science", "nature", "space", "history", "animals", 
            "geography", "technology", "art"
        );
    }

    @Test
    @DisplayName("getDailyTip: uses fallback topics when resource loading fails")
    void getDailyTip_topicsResourceFails_usesFallbackTopics() throws IOException {
        // Given
        String prompt = "Generate a fun fact for age 9-10";
        String fact = "Test fact";
        
        // Set topicsResource to null to trigger fallback
        ReflectionTestUtils.setField(dailyTipService, "topicsResource", null);
        setupAgeGroupResource(prompt);
        setupChatResponse(fact);
        when(moderationUtil.validateSafetyForAge(fact, AgeGroup.AGE_9_10)).thenReturn(true);

        // When
        DailyTipDto result = dailyTipService.getDailyTip();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFact()).isEqualTo(fact);
        assertThat(result.getCategory()).isIn("science", "history", "nature");
    }

    @Test
    @DisplayName("getDailyTip: uses fallback content when resource loading fails")
    void getDailyTip_fallbackContentResourceFails_usesFallbackContent() throws IOException {
        // Given
        String prompt = "Generate a fun fact for age 9-10";
        String unsafeFact = "Unsafe fact";
        
        // Set fallbackContentResource to null to trigger fallback
        ReflectionTestUtils.setField(dailyTipService, "fallbackContentResource", null);
        setupAgeGroupResource(prompt);
        setupChatResponse(unsafeFact);
        when(moderationUtil.validateSafetyForAge(unsafeFact, AgeGroup.AGE_9_10)).thenReturn(false);

        // When
        DailyTipDto result = dailyTipService.getDailyTip();

        // Then
        assertThat(result).isNotNull();
        // Should use hardcoded fallback fact
        assertThat(result.getFact()).contains("honey never spoils");
        assertThat(result.getCategory()).isIn("science", "history", "nature");
    }

    // Helper methods
    private void setupAgeGroupResource(String prompt) throws IOException {
        when(resourceLoader.getResource(anyString())).thenReturn(ageGroupResource);
        when(ageGroupResource.exists()).thenReturn(true);
        when(ageGroupResource.getInputStream()).thenReturn(
            new ByteArrayInputStream(prompt.getBytes(StandardCharsets.UTF_8))
        );
    }

    private void setupChatResponse(String fact) {
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn(fact);
    }
}

