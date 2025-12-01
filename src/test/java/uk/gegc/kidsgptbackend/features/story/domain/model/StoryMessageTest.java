package uk.gegc.kidsgptbackend.features.story.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseRepositoryTest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link StoryMessage} entity.
 */
@DisplayName("StoryMessage Entity Tests")
class StoryMessageTest extends BaseRepositoryTest {

    private Story testStory;

    @BeforeEach
    void setUp() {
        // Create a test story
        testStory = new Story();
        testStory.setUsername("testuser");
        testStory.setTitle("Test Story");
        testStory.setStatus(StoryStatus.IN_PROGRESS);
        testStory = persistFlushAndClear(testStory);
    }

    @Test
    @DisplayName("Entity: should persist all fields correctly")
    void entity_shouldPersistAllFields() {
        // Given
        StoryMessage message = new StoryMessage();
        message.setStory(testStory);
        message.setRole("USER");
        message.setContent("This is a test message content");

        // When
        StoryMessage saved = persistFlushAndClear(message);

        // Then
        StoryMessage persisted = find(StoryMessage.class, saved.getId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getStory()).isNotNull();
        assertThat(persisted.getStory().getId()).isEqualTo(testStory.getId());
        assertThat(persisted.getRole()).isEqualTo("USER");
        assertThat(persisted.getContent()).isEqualTo("This is a test message content");
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Entity: should auto-populate createdAt")
    void entity_shouldAutoPopulateCreatedAt() {
        // Given
        StoryMessage message = new StoryMessage();
        message.setStory(testStory);
        message.setRole("ASSISTANT");
        message.setContent("Response message");

        LocalDateTime beforeSave = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        // When
        StoryMessage saved = persistFlushAndClear(message);

        // Then
        StoryMessage persisted = find(StoryMessage.class, saved.getId());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
                .isAfterOrEqualTo(beforeSave);
    }

    @Test
    @DisplayName("Entity: should support USER and ASSISTANT roles")
    void entity_shouldSupportUserAndAssistantRoles() {
        // Given
        String[] roles = {"USER", "ASSISTANT"};

        for (String role : roles) {
            StoryMessage message = new StoryMessage();
            message.setStory(testStory);
            message.setRole(role);
            message.setContent("Message with role " + role);

            // When
            StoryMessage saved = persistFlushAndClear(message);

            // Then
            StoryMessage persisted = find(StoryMessage.class, saved.getId());
            assertThat(persisted.getRole()).isEqualTo(role);
        }
    }

    @Test
    @DisplayName("Entity: should persist long content (TEXT column)")
    void entity_shouldPersistLongContent() {
        // Given
        String longContent = "A".repeat(1000); // 1000 characters
        StoryMessage message = new StoryMessage();
        message.setStory(testStory);
        message.setRole("USER");
        message.setContent(longContent);

        // When
        StoryMessage saved = persistFlushAndClear(message);

        // Then
        StoryMessage persisted = find(StoryMessage.class, saved.getId());
        assertThat(persisted.getContent()).isEqualTo(longContent);
        assertThat(persisted.getContent().length()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Entity: should maintain relationship with Story")
    void entity_shouldMaintainRelationshipWithStory() {
        // Given
        StoryMessage message = new StoryMessage();
        message.setStory(testStory);
        message.setRole("USER");
        message.setContent("Test message");

        // When
        StoryMessage saved = persistFlushAndClear(message);

        // Then
        StoryMessage persisted = find(StoryMessage.class, saved.getId());
        assertThat(persisted.getStory()).isNotNull();
        assertThat(persisted.getStory().getId()).isEqualTo(testStory.getId());
        
        // Verify bidirectional relationship
        Story story = find(Story.class, testStory.getId());
        assertThat(story.getMessages()).contains(persisted);
    }

    @Test
    @DisplayName("Entity: should cascade delete when story is deleted")
    void entity_shouldCascadeDeleteWhenStoryDeleted() {
        // Given
        StoryMessage message1 = new StoryMessage();
        message1.setStory(testStory);
        message1.setRole("USER");
        message1.setContent("Message 1");
        persistFlushAndClear(message1);

        StoryMessage message2 = new StoryMessage();
        message2.setStory(testStory);
        message2.setRole("ASSISTANT");
        message2.setContent("Message 2");
        UUID message2Id = persistFlushAndClear(message2).getId();

        // When
        Story story = find(Story.class, testStory.getId());
        remove(story);
        flush();
        clear();

        // Then
        assertThat(find(StoryMessage.class, message2Id)).isNull();
    }

    @Test
    @DisplayName("Entity: should persist multiple messages for same story")
    void entity_shouldPersistMultipleMessagesForSameStory() {
        // Given
        StoryMessage message1 = new StoryMessage();
        message1.setStory(testStory);
        message1.setRole("USER");
        message1.setContent("First message");

        StoryMessage message2 = new StoryMessage();
        message2.setStory(testStory);
        message2.setRole("ASSISTANT");
        message2.setContent("Second message");

        StoryMessage message3 = new StoryMessage();
        message3.setStory(testStory);
        message3.setRole("USER");
        message3.setContent("Third message");

        // When
        UUID id1 = persistFlushAndClear(message1).getId();
        UUID id2 = persistFlushAndClear(message2).getId();
        UUID id3 = persistFlushAndClear(message3).getId();

        // Then
        Story story = find(Story.class, testStory.getId());
        assertThat(story.getMessages()).hasSize(3);
        assertThat(find(StoryMessage.class, id1)).isNotNull();
        assertThat(find(StoryMessage.class, id2)).isNotNull();
        assertThat(find(StoryMessage.class, id3)).isNotNull();
    }
}

