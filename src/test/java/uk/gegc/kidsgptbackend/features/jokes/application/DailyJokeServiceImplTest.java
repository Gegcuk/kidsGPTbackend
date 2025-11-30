package uk.gegc.kidsgptbackend.features.jokes.application;

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
import uk.gegc.kidsgptbackend.features.jokes.api.dto.DailyJokeDto;
import uk.gegc.kidsgptbackend.features.jokes.application.impl.DailyJokeServiceImpl;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.shared.util.ModerationUtil;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DailyJokeServiceImpl}.
 * <p>
 * Tests the service implementation with mocked dependencies to verify
 * business logic, error handling, and fallback scenarios.
 */
class DailyJokeServiceImplTest extends BaseUnitTest {

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
    private DailyJokeServiceImpl dailyJokeService;

    private Resource categoriesResource;
    private Resource jokeTypesResource;
    private Resource fallbackContentResource;

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        
        // Create ByteArrayResources for @Value injected resources
        categoriesResource = new ByteArrayResource("animals\nschool\nscience".getBytes(StandardCharsets.UTF_8));
        jokeTypesResource = new ByteArrayResource("animal jokes\nknock-knock jokes\nschool jokes".getBytes(StandardCharsets.UTF_8));
        fallbackContentResource = new ByteArrayResource(
            "FALLBACK_JOKE=Why don't elephants use computers? Because they're afraid of the mouse!\nFALLBACK_PROMPT=Generate a fun joke\nUSER_MESSAGE_TEMPLATE=Tell me a %s!"
                .getBytes(StandardCharsets.UTF_8)
        );
        
        // Inject @Value resources using ReflectionTestUtils
        ReflectionTestUtils.setField(dailyJokeService, "categoriesResource", categoriesResource);
        ReflectionTestUtils.setField(dailyJokeService, "jokeTypesResource", jokeTypesResource);
        ReflectionTestUtils.setField(dailyJokeService, "fallbackContentResource", fallbackContentResource);
        
        // Setup default mock behavior for ChatClient chain
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
    }

    @Test
    @DisplayName("getDailyJoke: returns joke for default age group")
    void getDailyJoke_returnsJokeForDefaultAgeGroup() throws IOException {
        // Given
        String prompt = "Generate a fun joke for age 9-10";
        String joke = "Why don't scientists trust atoms? Because they make up everything!";
        
        setupAgeGroupResource(prompt);
        setupChatResponse(joke);
        when(moderationUtil.validateSafetyForAge(joke, AgeGroup.AGE_9_10)).thenReturn(true);

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJoke()).isEqualTo(joke);
        assertThat(result.getCategory()).isIn("animals", "school", "science");
        assertThat(result.getAgeGroup()).isEqualTo("AGE_9_10");
    }

    @Test
    @DisplayName("getDailyJoke with ageGroup: returns joke for specific age group")
    void getDailyJoke_withAgeGroup_returnsJokeForSpecificAge() throws IOException {
        // Given
        String prompt = "Generate a fun joke for age 6-8";
        String joke = "What do you call a sleeping bull? A bulldozer!";
        
        setupAgeGroupResource(prompt);
        setupChatResponse(joke);
        when(moderationUtil.validateSafetyForAge(joke, AgeGroup.AGE_6_8)).thenReturn(true);

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke(AgeGroup.AGE_6_8);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJoke()).isEqualTo(joke);
        assertThat(result.getCategory()).isIn("animals", "school", "science");
        assertThat(result.getAgeGroup()).isEqualTo("AGE_6_8");
    }

    @Test
    @DisplayName("getDailyJoke: uses fallback when moderation fails")
    void getDailyJoke_moderationFails_usesFallback() throws IOException {
        // Given
        String prompt = "Generate a fun joke for age 9-10";
        String unsafeJoke = "Unsafe joke content";
        
        setupAgeGroupResource(prompt);
        setupChatResponse(unsafeJoke);
        when(moderationUtil.validateSafetyForAge(unsafeJoke, AgeGroup.AGE_9_10)).thenReturn(false);

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJoke()).contains("elephants use computers");
        assertThat(result.getCategory()).isIn("animals", "school", "science");
        assertThat(result.getAgeGroup()).isEqualTo("AGE_9_10");
    }

    @Test
    @DisplayName("getDailyJoke: uses fallback when chat client throws exception")
    void getDailyJoke_chatClientThrowsException_usesFallback() throws IOException {
        // Given
        String prompt = "Generate a fun joke for age 9-10";
        
        setupAgeGroupResource(prompt);
        when(callResponseSpec.chatResponse()).thenThrow(new RuntimeException("Chat client error"));

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJoke()).contains("elephants use computers");
        assertThat(result.getCategory()).isIn("animals", "school", "science");
        assertThat(result.getAgeGroup()).isEqualTo("AGE_9_10");
    }

    @Test
    @DisplayName("getDailyJoke: uses fallback when resource loading fails")
    void getDailyJoke_resourceLoadingFails_usesFallback() throws IOException {
        // Given
        when(resourceLoader.getResource(anyString())).thenReturn(ageGroupResource);
        when(ageGroupResource.exists()).thenReturn(false);
        when(ageGroupResource.getInputStream()).thenThrow(new IOException("Resource not found"));

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJoke()).contains("elephants use computers");
        assertThat(result.getCategory()).isIn("animals", "school", "science");
        assertThat(result.getAgeGroup()).isEqualTo("AGE_9_10");
    }

    @Test
    @DisplayName("getDailyJoke: handles all age groups correctly")
    void getDailyJoke_allAgeGroups_handlesCorrectly() throws IOException {
        // Given
        String joke = "Test joke";
        
        for (AgeGroup ageGroup : AgeGroup.values()) {
            String prompt = "Prompt for " + ageGroup;
            setupAgeGroupResource(prompt);
            setupChatResponse(joke);
            when(moderationUtil.validateSafetyForAge(joke, ageGroup)).thenReturn(true);

            // When
            DailyJokeDto result = dailyJokeService.getDailyJoke(ageGroup);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getJoke()).isEqualTo(joke);
            assertThat(result.getAgeGroup()).isEqualTo(ageGroup.name());
            assertThat(result.getCategory()).isIn("animals", "school", "science");
        }
    }

    @Test
    @DisplayName("getDailyJoke: uses fallback when chat response is null")
    void getDailyJoke_chatResponseIsNull_usesFallback() throws IOException {
        // Given
        String prompt = "Generate a fun joke for age 9-10";
        
        setupAgeGroupResource(prompt);
        when(callResponseSpec.chatResponse()).thenReturn(null);

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJoke()).contains("elephants use computers");
        assertThat(result.getCategory()).isIn("animals", "school", "science");
    }

    @Test
    @DisplayName("getDailyJoke: uses fallback when generation output is null")
    void getDailyJoke_generationOutputIsNull_usesFallback() throws IOException {
        // Given
        String prompt = "Generate a fun joke for age 9-10";
        
        setupAgeGroupResource(prompt);
        when(chatResponse.getResult()).thenReturn(null);

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJoke()).contains("elephants use computers");
        assertThat(result.getCategory()).isIn("animals", "school", "science");
    }

    @Test
    @DisplayName("getDailyJoke: uses fallback when message text is null")
    void getDailyJoke_messageTextIsNull_usesFallback() throws IOException {
        // Given
        String prompt = "Generate a fun joke for age 9-10";
        
        setupAgeGroupResource(prompt);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn(null);

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJoke()).contains("elephants use computers");
        assertThat(result.getCategory()).isIn("animals", "school", "science");
    }

    @Test
    @DisplayName("getDailyJoke: uses fallback categories when resource loading fails")
    void getDailyJoke_categoriesResourceFails_usesFallbackCategories() throws IOException {
        // Given
        String prompt = "Generate a fun joke for age 9-10";
        String joke = "Test joke";
        
        // Set categoriesResource to null to trigger fallback
        ReflectionTestUtils.setField(dailyJokeService, "categoriesResource", null);
        setupAgeGroupResource(prompt);
        setupChatResponse(joke);
        when(moderationUtil.validateSafetyForAge(joke, AgeGroup.AGE_9_10)).thenReturn(true);

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJoke()).isEqualTo(joke);
        // Should use one of the fallback categories
        assertThat(result.getCategory()).isIn(
            "animals", "school", "science", "wordplay", "food", 
            "sports", "technology", "nature"
        );
    }

    @Test
    @DisplayName("getDailyJoke: uses fallback joke types when resource loading fails")
    void getDailyJoke_jokeTypesResourceFails_usesFallbackJokeTypes() throws IOException {
        // Given
        String prompt = "Generate a fun joke for age 9-10";
        String joke = "Test joke";
        
        // Set jokeTypesResource to null to trigger fallback
        ReflectionTestUtils.setField(dailyJokeService, "jokeTypesResource", null);
        setupAgeGroupResource(prompt);
        setupChatResponse(joke);
        when(moderationUtil.validateSafetyForAge(joke, AgeGroup.AGE_9_10)).thenReturn(true);

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJoke()).isEqualTo(joke);
        assertThat(result.getCategory()).isIn("animals", "school", "science");
    }

    @Test
    @DisplayName("getDailyJoke: uses fallback content when resource loading fails")
    void getDailyJoke_fallbackContentResourceFails_usesFallbackContent() throws IOException {
        // Given
        String prompt = "Generate a fun joke for age 9-10";
        String unsafeJoke = "Unsafe joke";
        
        // Set fallbackContentResource to null to trigger fallback
        ReflectionTestUtils.setField(dailyJokeService, "fallbackContentResource", null);
        setupAgeGroupResource(prompt);
        setupChatResponse(unsafeJoke);
        when(moderationUtil.validateSafetyForAge(unsafeJoke, AgeGroup.AGE_9_10)).thenReturn(false);

        // When
        DailyJokeDto result = dailyJokeService.getDailyJoke();

        // Then
        assertThat(result).isNotNull();
        // Should use hardcoded fallback joke
        assertThat(result.getJoke()).contains("elephants use computers");
        assertThat(result.getCategory()).isIn("animals", "school", "science");
    }

    // Helper methods
    private void setupAgeGroupResource(String prompt) throws IOException {
        when(resourceLoader.getResource(anyString())).thenReturn(ageGroupResource);
        when(ageGroupResource.exists()).thenReturn(true);
        when(ageGroupResource.getInputStream()).thenReturn(
            new ByteArrayInputStream(prompt.getBytes(StandardCharsets.UTF_8))
        );
    }

    private void setupChatResponse(String joke) {
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn(joke);
    }
}

