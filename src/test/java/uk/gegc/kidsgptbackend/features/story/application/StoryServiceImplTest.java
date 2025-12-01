package uk.gegc.kidsgptbackend.features.story.application;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.features.chat.api.dto.Tone;
import uk.gegc.kidsgptbackend.features.story.api.dto.*;
import uk.gegc.kidsgptbackend.features.story.application.impl.StoryServiceImpl;
import uk.gegc.kidsgptbackend.features.story.domain.model.Story;
import uk.gegc.kidsgptbackend.features.story.domain.model.StoryStatus;
import uk.gegc.kidsgptbackend.features.story.domain.repository.StoryRepository;
import uk.gegc.kidsgptbackend.features.story.infra.mapping.StoryMapper;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.shared.exception.ResourceNotFoundException;
import uk.gegc.kidsgptbackend.shared.util.ModerationUtil;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StoryServiceImpl}.
 * <p>
 * Tests the service implementation with mocked dependencies to verify
 * business logic, error handling, and fallback scenarios.
 */
class StoryServiceImplTest extends BaseUnitTest {

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ModerationUtil moderationUtil;

    @Mock
    private StoryMapper storyMapper;

    @Mock
    private Clock clock;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @InjectMocks
    private StoryServiceImpl storyService;

    private Principal principal;
    private User testUser;
    private Story testStory;
    private Instant testStartTime;
    private Instant testEndTime;
    private Resource storyPromptResource;
    private Resource startTemplatesResource;
    private Resource continueTemplatesResource;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        principal = () -> "testuser";
        testStartTime = Instant.parse("2024-01-01T12:00:00Z");
        testEndTime = Instant.parse("2024-01-01T12:00:02Z"); // 2 seconds later

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setAge(9);
        testUser.setActive(true);

        testStory = new Story();
        testStory.setId(UUID.randomUUID());
        testStory.setUsername("testuser");
        testStory.setTitle("Test Story");
        testStory.setStatus(StoryStatus.STARTED);
        testStory.setCreatedAt(LocalDateTime.now());

        // Create mock resources for @Value injection
        storyPromptResource = new ByteArrayResource("You are a helpful story assistant.".getBytes(StandardCharsets.UTF_8));
        startTemplatesResource = new ByteArrayResource("Template 1: %s\nTemplate 2: %s".getBytes(StandardCharsets.UTF_8));
        continueTemplatesResource = new ByteArrayResource("Continue 1: %s\nContinue 2: %s".getBytes(StandardCharsets.UTF_8));

        // Inject @Value resources using ReflectionTestUtils
        ReflectionTestUtils.setField(storyService, "storyPrompt6_8", storyPromptResource);
        ReflectionTestUtils.setField(storyService, "storyPrompt9_10", storyPromptResource);
        ReflectionTestUtils.setField(storyService, "storyPrompt11_12", storyPromptResource);
        ReflectionTestUtils.setField(storyService, "storyPrompt13_14", storyPromptResource);
        ReflectionTestUtils.setField(storyService, "storyPrompt15_16", storyPromptResource);
        ReflectionTestUtils.setField(storyService, "startTemplatesResource", startTemplatesResource);
        ReflectionTestUtils.setField(storyService, "continueTemplatesResource", continueTemplatesResource);

        // Setup default mock behavior for ChatClient chain
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);

        when(clock.instant())
                .thenReturn(testStartTime)
                .thenReturn(testEndTime);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(true);
        when(moderationUtil.validateSafetyForAge(anyString(), any(AgeGroup.class))).thenReturn(true);
    }

    @Test
    @DisplayName("startStory: should create story and return response when valid request")
    void startStory_validRequest_createsStoryAndReturnsResponse() {
        // Given
        StartStoryRequest request = new StartStoryRequest("My Adventure", "A brave hero");
        Story savedStory = new Story();
        savedStory.setId(UUID.randomUUID());
        savedStory.setUsername("testuser");
        savedStory.setTitle("My Adventure");
        savedStory.setStatus(StoryStatus.IN_PROGRESS);
        savedStory.setCreatedAt(LocalDateTime.now());

        AssistantMessage assistantMessage = new AssistantMessage("Great story idea! Let's begin.");
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);

        when(storyRepository.save(any(Story.class))).thenReturn(savedStory);

        // When
        StartStoryResponse response = storyService.startStory(request, principal);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.storyId()).isEqualTo(savedStory.getId());
        assertThat(response.title()).isEqualTo("My Adventure");
        assertThat(response.encouragingMessage()).isEqualTo("Great story idea! Let's begin.");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.latencyMs()).isEqualTo(Duration.between(testStartTime, testEndTime).toMillis());

        verify(storyRepository, times(2)).save(any(Story.class));
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    @DisplayName("startStory: should handle null initialIdea")
    void startStory_nullInitialIdea_handlesGracefully() {
        // Given
        StartStoryRequest request = new StartStoryRequest("My Story", null);
        Story savedStory = new Story();
        savedStory.setId(UUID.randomUUID());
        savedStory.setCreatedAt(LocalDateTime.now());

        AssistantMessage assistantMessage = new AssistantMessage("Let's start!");
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(storyRepository.save(any(Story.class))).thenReturn(savedStory);

        // When
        StartStoryResponse response = storyService.startStory(request, principal);

        // Then
        assertThat(response).isNotNull();
        verify(storyRepository, times(2)).save(any(Story.class));
    }

    @Test
    @DisplayName("startStory: should throw exception when user not found")
    void startStory_userNotFound_throwsException() {
        // Given
        StartStoryRequest request = new StartStoryRequest("My Story", null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> storyService.startStory(request, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(storyRepository, never()).save(any());
    }

    @Test
    @DisplayName("startStory: should use fallback when AI response fails moderation")
    void startStory_aiResponseFailsModeration_usesFallback() {
        // Given
        StartStoryRequest request = new StartStoryRequest("My Story", null);
        Story savedStory = new Story();
        savedStory.setId(UUID.randomUUID());
        savedStory.setCreatedAt(LocalDateTime.now());

        AssistantMessage assistantMessage = new AssistantMessage("Inappropriate content");
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(moderationUtil.validateSafetyForAge(anyString(), any(AgeGroup.class))).thenReturn(false);
        when(storyRepository.save(any(Story.class))).thenReturn(savedStory);

        // When
        StartStoryResponse response = storyService.startStory(request, principal);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.encouragingMessage()).contains("different approach");
        verify(moderationUtil).validateSafetyForAge(anyString(), any(AgeGroup.class));
    }

    @Test
    @DisplayName("startStory: should handle AI API exception gracefully")
    void startStory_aiApiException_handlesGracefully() {
        // Given
        StartStoryRequest request = new StartStoryRequest("My Story", null);
        Story savedStory = new Story();
        savedStory.setId(UUID.randomUUID());
        savedStory.setCreatedAt(LocalDateTime.now());

        when(requestSpec.call()).thenThrow(new RuntimeException("API error"));
        when(storyRepository.save(any(Story.class))).thenReturn(savedStory);

        // When
        StartStoryResponse response = storyService.startStory(request, principal);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.encouragingMessage()).contains("excited to help");
    }

    @Test
    @DisplayName("continueStory: should continue story and return response")
    void continueStory_validRequest_continuesStory() {
        // Given
        UUID storyId = UUID.randomUUID();
        ContinueStoryRequest request = new ContinueStoryRequest(
                storyId,
                "The hero found a door",
                Tone.FRIENDLY,
                Collections.emptyList()
        );

        Story existingStory = new Story();
        existingStory.setId(storyId);
        existingStory.setUsername("testuser");
        existingStory.setTitle("My Story");
        existingStory.setStatus(StoryStatus.IN_PROGRESS);

        AssistantMessage assistantMessage = new AssistantMessage("What's behind the door?");
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(storyRepository.findByIdAndUsername(storyId, "testuser")).thenReturn(Optional.of(existingStory));
        when(storyRepository.save(any(Story.class))).thenReturn(existingStory);

        // When
        ContinueStoryResponse response = storyService.continueStory(request, principal);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.storyId()).isEqualTo(storyId);
        assertThat(response.reply()).isEqualTo("What's behind the door?");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.latencyMs()).isEqualTo(Duration.between(testStartTime, testEndTime).toMillis());

        verify(storyRepository).findByIdAndUsername(storyId, "testuser");
        verify(storyRepository).save(existingStory);
        verify(moderationUtil).validateComprehensive("The hero found a door", testUser, "story continuation");
    }

    @Test
    @DisplayName("continueStory: should throw exception when story not found")
    void continueStory_storyNotFound_throwsException() {
        // Given
        UUID storyId = UUID.randomUUID();
        ContinueStoryRequest request = new ContinueStoryRequest(
                storyId,
                "Continue story",
                Tone.FRIENDLY,
                Collections.emptyList()
        );

        when(storyRepository.findByIdAndUsername(storyId, "testuser")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> storyService.continueStory(request, principal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Story not found");

        verify(storyRepository, never()).save(any());
    }

    @Test
    @DisplayName("continueStory: should throw exception when moderation fails")
    void continueStory_moderationFails_throwsException() {
        // Given
        UUID storyId = UUID.randomUUID();
        ContinueStoryRequest request = new ContinueStoryRequest(
                storyId,
                "Inappropriate content",
                Tone.FRIENDLY,
                Collections.emptyList()
        );

        Story existingStory = new Story();
        existingStory.setId(storyId);
        existingStory.setUsername("testuser");

        when(storyRepository.findByIdAndUsername(storyId, "testuser")).thenReturn(Optional.of(existingStory));
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), anyString())).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> storyService.continueStory(request, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe for age group");

        verify(storyRepository, never()).save(any());
    }

    @Test
    @DisplayName("continueStory: should build conversation history from context")
    void continueStory_withContext_buildsHistory() {
        // Given
        UUID storyId = UUID.randomUUID();
        List<StoryMessageDto> context = List.of(
                new StoryMessageDto(UUID.randomUUID(), "USER", "Hello", LocalDateTime.now()),
                new StoryMessageDto(UUID.randomUUID(), "ASSISTANT", "Hi there!", LocalDateTime.now())
        );

        ContinueStoryRequest request = new ContinueStoryRequest(
                storyId,
                "Continue",
                Tone.FRIENDLY,
                context
        );

        Story existingStory = new Story();
        existingStory.setId(storyId);
        existingStory.setUsername("testuser");

        AssistantMessage assistantMessage = new AssistantMessage("Response");
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(storyRepository.findByIdAndUsername(storyId, "testuser")).thenReturn(Optional.of(existingStory));
        when(storyRepository.save(any(Story.class))).thenReturn(existingStory);

        // When
        storyService.continueStory(request, principal);

        // Then
        verify(requestSpec).messages(anyList()); // Should be called with conversation history
    }

    @Test
    @DisplayName("getStory: should return story DTO when found")
    void getStory_storyFound_returnsDto() {
        // Given
        UUID storyId = UUID.randomUUID();
        Story story = new Story();
        story.setId(storyId);
        story.setUsername("testuser");

        StoryDto expectedDto = new StoryDto(
                storyId,
                "My Story",
                StoryStatus.IN_PROGRESS,
                Collections.emptyList(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(storyRepository.findByIdAndUsername(storyId, "testuser")).thenReturn(Optional.of(story));
        when(storyMapper.toDto(story)).thenReturn(expectedDto);

        // When
        StoryDto result = storyService.getStory(storyId, principal);

        // Then
        assertThat(result).isEqualTo(expectedDto);
        verify(storyRepository).findByIdAndUsername(storyId, "testuser");
        verify(storyMapper).toDto(story);
    }

    @Test
    @DisplayName("getStory: should throw exception when story not found")
    void getStory_storyNotFound_throwsException() {
        // Given
        UUID storyId = UUID.randomUUID();
        when(storyRepository.findByIdAndUsername(storyId, "testuser")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> storyService.getStory(storyId, principal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Story not found");
    }

    @Test
    @DisplayName("getStoriesByUser: should return page of story list DTOs")
    void getStoriesByUser_validRequest_returnsPage() {
        // Given
        Story story1 = new Story();
        story1.setId(UUID.randomUUID());
        story1.setUsername("testuser");

        Story story2 = new Story();
        story2.setId(UUID.randomUUID());
        story2.setUsername("testuser");

        Page<Story> storyPage = new PageImpl<>(List.of(story1, story2));
        Pageable pageable = Pageable.ofSize(20);

        StoryListDto dto1 = new StoryListDto(UUID.randomUUID(), "Story 1", StoryStatus.IN_PROGRESS, 5, LocalDateTime.now(), LocalDateTime.now());
        StoryListDto dto2 = new StoryListDto(UUID.randomUUID(), "Story 2", StoryStatus.IN_PROGRESS, 3, LocalDateTime.now(), LocalDateTime.now());

        when(storyRepository.findByUsernameOrderByUpdatedAtDesc("testuser", pageable)).thenReturn(storyPage);
        when(storyMapper.toListDto(story1)).thenReturn(dto1);
        when(storyMapper.toListDto(story2)).thenReturn(dto2);

        // When
        Page<StoryListDto> result = storyService.getStoriesByUser(principal, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        verify(storyRepository).findByUsernameOrderByUpdatedAtDesc("testuser", pageable);
    }

    @Test
    @DisplayName("startStory: should handle different age groups")
    void startStory_differentAgeGroups_loadsCorrectPrompt() {
        // Given
        testUser.setAge(7); // AGE_6_8
        StartStoryRequest request = new StartStoryRequest("Story", null);
        Story savedStory = new Story();
        savedStory.setId(UUID.randomUUID());
        savedStory.setCreatedAt(LocalDateTime.now());

        AssistantMessage assistantMessage = new AssistantMessage("Response");
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(storyRepository.save(any(Story.class))).thenReturn(savedStory);

        // When
        storyService.startStory(request, principal);

        // Then
        verify(requestSpec).system(anyString()); // Prompt should be loaded
    }

    @Test
    @DisplayName("startStory: should handle null age by defaulting to AGE_9_10")
    void startStory_nullAge_defaultsToAge9_10() {
        // Given
        testUser.setAge(null);
        StartStoryRequest request = new StartStoryRequest("Story", null);
        Story savedStory = new Story();
        savedStory.setId(UUID.randomUUID());
        savedStory.setCreatedAt(LocalDateTime.now());

        AssistantMessage assistantMessage = new AssistantMessage("Response");
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(storyRepository.save(any(Story.class))).thenReturn(savedStory);

        // When
        storyService.startStory(request, principal);

        // Then
        verify(moderationUtil).validateSafetyForAge(anyString(), eq(AgeGroup.AGE_9_10));
    }
}

