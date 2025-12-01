package uk.gegc.kidsgptbackend.features.story.infra.mapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.story.api.dto.StoryDto;
import uk.gegc.kidsgptbackend.features.story.api.dto.StoryListDto;
import uk.gegc.kidsgptbackend.features.story.api.dto.StoryMessageDto;
import uk.gegc.kidsgptbackend.features.story.domain.model.Story;
import uk.gegc.kidsgptbackend.features.story.domain.model.StoryMessage;
import uk.gegc.kidsgptbackend.features.story.domain.model.StoryStatus;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StoryMapper}.
 */
class StoryMapperTest extends BaseUnitTest {

    private StoryMapper storyMapper;

    private Story testStory;
    private StoryMessage testMessage1;
    private StoryMessage testMessage2;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        storyMapper = new StoryMapper();

        // Setup test story
        testStory = new Story();
        testStory.setId(UUID.randomUUID());
        testStory.setUsername("testuser");
        testStory.setTitle("Test Story");
        testStory.setStatus(StoryStatus.IN_PROGRESS);
        testStory.setCreatedAt(LocalDateTime.now());
        testStory.setUpdatedAt(LocalDateTime.now());

        // Setup test messages
        testMessage1 = new StoryMessage();
        testMessage1.setId(UUID.randomUUID());
        testMessage1.setRole("USER");
        testMessage1.setContent("Hello");
        testMessage1.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        testMessage1.setStory(testStory);

        testMessage2 = new StoryMessage();
        testMessage2.setId(UUID.randomUUID());
        testMessage2.setRole("ASSISTANT");
        testMessage2.setContent("Hi there!");
        testMessage2.setCreatedAt(LocalDateTime.now().minusMinutes(4));
        testMessage2.setStory(testStory);

        testStory.setMessages(new ArrayList<>(List.of(testMessage1, testMessage2)));
    }

    @Test
    @DisplayName("toDto: should map story to DTO correctly")
    void toDto_shouldMapStoryToDto() {
        // When
        StoryDto dto = storyMapper.toDto(testStory);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(testStory.getId());
        assertThat(dto.title()).isEqualTo(testStory.getTitle());
        assertThat(dto.status()).isEqualTo(testStory.getStatus());
        assertThat(dto.createdAt()).isEqualTo(testStory.getCreatedAt());
        assertThat(dto.updatedAt()).isEqualTo(testStory.getUpdatedAt());
        assertThat(dto.messages()).hasSize(2);
    }

    @Test
    @DisplayName("toDto: should map messages correctly")
    void toDto_shouldMapMessages() {
        // When
        StoryDto dto = storyMapper.toDto(testStory);

        // Then
        assertThat(dto.messages()).hasSize(2);
        StoryMessageDto messageDto1 = dto.messages().get(0);
        assertThat(messageDto1.id()).isEqualTo(testMessage1.getId());
        assertThat(messageDto1.role()).isEqualTo("USER");
        assertThat(messageDto1.content()).isEqualTo("Hello");

        StoryMessageDto messageDto2 = dto.messages().get(1);
        assertThat(messageDto2.id()).isEqualTo(testMessage2.getId());
        assertThat(messageDto2.role()).isEqualTo("ASSISTANT");
        assertThat(messageDto2.content()).isEqualTo("Hi there!");
    }

    @Test
    @DisplayName("toDto: should handle empty messages list")
    void toDto_emptyMessages_handlesGracefully() {
        // Given
        testStory.setMessages(new ArrayList<>());

        // When
        StoryDto dto = storyMapper.toDto(testStory);

        // Then
        assertThat(dto.messages()).isEmpty();
    }

    @Test
    @DisplayName("toListDto: should map story to list DTO correctly")
    void toListDto_shouldMapStoryToListDto() {
        // When
        StoryListDto dto = storyMapper.toListDto(testStory);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(testStory.getId());
        assertThat(dto.title()).isEqualTo(testStory.getTitle());
        assertThat(dto.status()).isEqualTo(testStory.getStatus());
        assertThat(dto.messageCount()).isEqualTo(2);
        assertThat(dto.createdAt()).isEqualTo(testStory.getCreatedAt());
        assertThat(dto.updatedAt()).isEqualTo(testStory.getUpdatedAt());
    }

    @Test
    @DisplayName("toListDto: should count messages correctly")
    void toListDto_shouldCountMessages() {
        // Given
        testStory.setMessages(new ArrayList<>());

        // When
        StoryListDto dto = storyMapper.toListDto(testStory);

        // Then
        assertThat(dto.messageCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("toMessageDto: should map message to DTO correctly")
    void toMessageDto_shouldMapMessageToDto() {
        // When
        StoryMessageDto dto = storyMapper.toMessageDto(testMessage1);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(testMessage1.getId());
        assertThat(dto.role()).isEqualTo("USER");
        assertThat(dto.content()).isEqualTo("Hello");
        assertThat(dto.createdAt()).isEqualTo(testMessage1.getCreatedAt());
    }

    @Test
    @DisplayName("toDtoList: should map list of stories to DTOs")
    void toDtoList_shouldMapListOfStories() {
        // Given
        Story story2 = new Story();
        story2.setId(UUID.randomUUID());
        story2.setTitle("Story 2");
        story2.setStatus(StoryStatus.COMPLETED);
        story2.setMessages(new ArrayList<>());

        List<Story> stories = List.of(testStory, story2);

        // When
        List<StoryDto> dtos = storyMapper.toDtoList(stories);

        // Then
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).id()).isEqualTo(testStory.getId());
        assertThat(dtos.get(1).id()).isEqualTo(story2.getId());
    }

    @Test
    @DisplayName("toDtoList: should handle empty list")
    void toDtoList_emptyList_handlesGracefully() {
        // When
        List<StoryDto> dtos = storyMapper.toDtoList(List.of());

        // Then
        assertThat(dtos).isEmpty();
    }

    @Test
    @DisplayName("toListDtoList: should map list of stories to list DTOs")
    void toListDtoList_shouldMapListOfStories() {
        // Given
        Story story2 = new Story();
        story2.setId(UUID.randomUUID());
        story2.setTitle("Story 2");
        story2.setStatus(StoryStatus.COMPLETED);
        story2.setMessages(new ArrayList<>());

        List<Story> stories = List.of(testStory, story2);

        // When
        List<StoryListDto> dtos = storyMapper.toListDtoList(stories);

        // Then
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).id()).isEqualTo(testStory.getId());
        assertThat(dtos.get(1).id()).isEqualTo(story2.getId());
    }

    @Test
    @DisplayName("toListDtoList: should handle empty list")
    void toListDtoList_emptyList_handlesGracefully() {
        // When
        List<StoryListDto> dtos = storyMapper.toListDtoList(List.of());

        // Then
        assertThat(dtos).isEmpty();
    }
}

