package uk.gegc.kidsgptbackend.features.chat.infra.mapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto;
import uk.gegc.kidsgptbackend.features.chat.domain.model.ChatMessage;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatMessageMapper}.
 */
@DisplayName("ChatMessageMapper Tests")
class ChatMessageMapperTest extends BaseUnitTest {

    private ChatMessageMapper chatMessageMapper;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        chatMessageMapper = new ChatMessageMapper();
    }

    @Test
    @DisplayName("toDto: should map ChatMessage to DTO correctly")
    void toDto_shouldMapChatMessageToDto() {
        // Given
        UUID messageId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setRole("USER");
        message.setContent("Hello, how are you?");
        message.setCreatedAt(createdAt);

        // When
        ChatMessageDto dto = chatMessageMapper.toDto(message);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(messageId);
        assertThat(dto.role()).isEqualTo("USER");
        assertThat(dto.content()).isEqualTo("Hello, how are you?");
        assertThat(dto.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("toDto: should map ASSISTANT role correctly")
    void toDto_shouldMapAssistantRole() {
        // Given
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setRole("ASSISTANT");
        message.setContent("I'm doing great, thanks!");
        message.setCreatedAt(LocalDateTime.now());

        // When
        ChatMessageDto dto = chatMessageMapper.toDto(message);

        // Then
        assertThat(dto.role()).isEqualTo("ASSISTANT");
        assertThat(dto.content()).isEqualTo("I'm doing great, thanks!");
    }

    @Test
    @DisplayName("toDto: should handle long content correctly")
    void toDto_shouldHandleLongContent() {
        // Given
        String longContent = "A".repeat(1000);
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setRole("USER");
        message.setContent(longContent);
        message.setCreatedAt(LocalDateTime.now());

        // When
        ChatMessageDto dto = chatMessageMapper.toDto(message);

        // Then
        assertThat(dto.content()).isEqualTo(longContent);
        assertThat(dto.content().length()).isEqualTo(1000);
    }

    @Test
    @DisplayName("toDto: should handle empty content correctly")
    void toDto_shouldHandleEmptyContent() {
        // Given
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setRole("USER");
        message.setContent("");
        message.setCreatedAt(LocalDateTime.now());

        // When
        ChatMessageDto dto = chatMessageMapper.toDto(message);

        // Then
        assertThat(dto.content()).isEmpty();
    }

    @Test
    @DisplayName("toDto: should handle null values gracefully")
    void toDto_shouldHandleNullValues() {
        // Given
        ChatMessage message = new ChatMessage();
        message.setId(null);
        message.setRole(null);
        message.setContent(null);
        message.setCreatedAt(null);

        // When
        ChatMessageDto dto = chatMessageMapper.toDto(message);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isNull();
        assertThat(dto.role()).isNull();
        assertThat(dto.content()).isNull();
        assertThat(dto.createdAt()).isNull();
    }
}

