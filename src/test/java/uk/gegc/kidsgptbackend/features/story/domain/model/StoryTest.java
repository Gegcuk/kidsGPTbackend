package uk.gegc.kidsgptbackend.features.story.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseRepositoryTest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link Story} entity.
 */
@DisplayName("Story Entity Tests")
class StoryTest extends BaseRepositoryTest {

    private String testUsername;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        testUsername = "testuser";
    }

    @Test
    @DisplayName("Entity: should persist all fields correctly")
    void entity_shouldPersistAllFields() {
        // Given
        Story story = new Story();
        story.setUsername(testUsername);
        story.setTitle("My Adventure Story");
        story.setStatus(StoryStatus.STARTED);

        // When
        Story saved = persistFlushAndClear(story);

        // Then
        Story persisted = find(Story.class, saved.getId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getUsername()).isEqualTo(testUsername);
        assertThat(persisted.getTitle()).isEqualTo("My Adventure Story");
        assertThat(persisted.getStatus()).isEqualTo(StoryStatus.STARTED);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
        assertThat(persisted.getMessages()).isEmpty();
    }

    @Test
    @DisplayName("Entity: should auto-populate createdAt and updatedAt")
    void entity_shouldAutoPopulateTimestamps() {
        // Given
        Story story = new Story();
        story.setUsername(testUsername);
        story.setTitle("Test Story");
        story.setStatus(StoryStatus.IN_PROGRESS);

        LocalDateTime beforeSave = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        // When
        Story saved = persistFlushAndClear(story);

        // Then
        Story persisted = find(Story.class, saved.getId());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
                .isAfterOrEqualTo(beforeSave);
        assertThat(persisted.getUpdatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS))
                .isAfterOrEqualTo(beforeSave);
    }

    @Test
    @DisplayName("addMessage: should add message and set bidirectional relationship")
    void addMessage_shouldAddMessageAndSetRelationship() {
        // Given
        Story story = new Story();
        story.setUsername(testUsername);
        story.setTitle("Test Story");
        story.setStatus(StoryStatus.IN_PROGRESS);

        StoryMessage message = new StoryMessage();
        message.setRole("USER");
        message.setContent("Hello");

        // When
        story.addMessage(message);
        Story saved = persistFlushAndClear(story);

        // Then
        Story persisted = find(Story.class, saved.getId());
        assertThat(persisted.getMessages()).hasSize(1);
        assertThat(persisted.getMessages().get(0).getRole()).isEqualTo("USER");
        assertThat(persisted.getMessages().get(0).getContent()).isEqualTo("Hello");
        assertThat(persisted.getMessages().get(0).getStory()).isEqualTo(persisted);
    }

    @Test
    @DisplayName("addMessage: should add multiple messages")
    void addMessage_shouldAddMultipleMessages() {
        // Given
        Story story = new Story();
        story.setUsername(testUsername);
        story.setTitle("Test Story");
        story.setStatus(StoryStatus.IN_PROGRESS);

        StoryMessage message1 = new StoryMessage();
        message1.setRole("USER");
        message1.setContent("Hello");

        StoryMessage message2 = new StoryMessage();
        message2.setRole("ASSISTANT");
        message2.setContent("Hi there!");

        // When
        story.addMessage(message1);
        story.addMessage(message2);
        Story saved = persistFlushAndClear(story);

        // Then
        Story persisted = find(Story.class, saved.getId());
        assertThat(persisted.getMessages()).hasSize(2);
        assertThat(persisted.getMessages().get(0).getContent()).isEqualTo("Hello");
        assertThat(persisted.getMessages().get(1).getContent()).isEqualTo("Hi there!");
    }

    @Test
    @DisplayName("Entity: should support all StoryStatus values")
    void entity_shouldSupportAllStatusValues() {
        // Given
        StoryStatus[] statuses = StoryStatus.values();

        for (StoryStatus status : statuses) {
            Story story = new Story();
            story.setUsername(testUsername + "_" + status.name());
            story.setTitle("Story " + status.name());
            story.setStatus(status);

            // When
            Story saved = persistFlushAndClear(story);

            // Then
            Story persisted = find(Story.class, saved.getId());
            assertThat(persisted.getStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("Entity: should update updatedAt on modification")
    void entity_shouldUpdateUpdatedAtOnModification() throws InterruptedException {
        // Given
        Story story = new Story();
        story.setUsername(testUsername);
        story.setTitle("Original Title");
        story.setStatus(StoryStatus.STARTED);

        Story saved = persistFlushAndClear(story);
        LocalDateTime originalUpdatedAt = saved.getUpdatedAt();

        // Wait a bit to ensure timestamp difference
        Thread.sleep(100);

        // When
        Story persisted = find(Story.class, saved.getId());
        persisted.setTitle("Updated Title");
        persisted.setStatus(StoryStatus.IN_PROGRESS);
        flush();
        clear();

        // Then
        Story updated = find(Story.class, saved.getId());
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(updated.getTitle()).isEqualTo("Updated Title");
    }
}

