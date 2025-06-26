package uk.gegc.kidsgptbackend.service.chat.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageDto;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageRequest;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageResponse;
import uk.gegc.kidsgptbackend.dto.chat.Tone;
import uk.gegc.kidsgptbackend.exception.ConversationFormatException;
import uk.gegc.kidsgptbackend.exception.ModerationServiceException;
import uk.gegc.kidsgptbackend.exception.RateLimitException;
import uk.gegc.kidsgptbackend.model.chat.ChatContext;
import uk.gegc.kidsgptbackend.model.chat.ChatMessage;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.chat.ChatContextRepository;
import uk.gegc.kidsgptbackend.repository.chat.ChatMessageRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.util.ModerationUtil;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
class AiChatServiceImplTest {

    @Mock
    ChatContextRepository contextRepository;
    @Mock
    ChatMessageRepository messageRepository;
    @Mock
    ChatClient chatClient;
    @Mock
    ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    ChatClient.CallResponseSpec callSpec;
    @Mock
    ModerationUtil moderationUtil;
    @Mock
    UserRepository userRepository;

    @InjectMocks
    AiChatServiceImpl service;

    Principal principal = () -> "alice";

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "systemPrompt", new ByteArrayResource("sys".getBytes()));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    // Removed - now using ModerationUtil

    private ChatResponse simpleResponse(String text) {
        AssistantMessage m = new AssistantMessage(text);
        Generation gen = new Generation(m);
        ChatResponseMetadata meta = ChatResponseMetadata.builder().model("model").build();
        return ChatResponse.builder().generations(List.of(gen)).metadata(meta).build();
    }

    @Test
    @DisplayName("chat: moderation failure throws ModerationServiceException")
    void chat_moderationFailure_exception() {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY, null);
        when(moderationUtil.validateSafety(anyString()))
                .thenThrow(new ModerationServiceException("down", new RuntimeException()));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(ModerationServiceException.class);
    }

    @Test
    @DisplayName("chat: flagged user input throws IllegalArgumentException")
    void chat_flaggedInput_throws() {
        ChatMessageRequest req = new ChatMessageRequest("bad", null, Tone.FRIENDLY, null);
        when(moderationUtil.validateSafety("bad")).thenReturn(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("chat: chat client exception results in RateLimitException")
    void chat_chatClientException_rateLimit() {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY, null);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("chat: flagged reply gets sanitized")
    void chat_flaggedReply_sanitized() {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY, null);
        when(moderationUtil.validateSafety("hi")).thenReturn(true);
        when(moderationUtil.validateSafety("reply")).thenReturn(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        assertThat(resp.reply()).contains("Oops, that topic's a bit tricky");
    }

    @Test
    @DisplayName("chat: null context creates new context")
    void chat_nullContext_createsContext() {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY, null);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        assertThat(resp.contextId()).isNotNull();
        verify(contextRepository).save(any(ChatContext.class));
    }

    @Test
    @DisplayName("chat: context history is properly used when provided")
    void chat_withContext_usesContextHistory() {
        // Create mock chat history
        List<ChatMessageDto> contextHistory = List.of(
                new ChatMessageDto(UUID.randomUUID(), "USER", "Hello!", LocalDateTime.now()),
                new ChatMessageDto(UUID.randomUUID(), "ASSISTANT", "Hi there!", LocalDateTime.now())
        );
        
        ChatMessageRequest req = new ChatMessageRequest("How are you?", null, Tone.FRIENDLY, contextHistory);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("I'm doing great!"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp.reply()).isEqualTo("I'm doing great!");
        assertThat(resp.contextId()).isNotNull();
        
        // Verify that messages() was called with the context history
        verify(requestSpec).messages(any(List.class));
        verify(requestSpec, never()).user(anyString()); // Should not use user() when context is provided
    }

    @Test
    @DisplayName("chat: conversation format error throws helpful ConversationFormatException")
    void chat_conversationFormatError_throwsHelpfulException() {
        ChatMessageRequest req = new ChatMessageRequest("test", null, Tone.FRIENDLY, null);
        when(moderationUtil.validateSafety(anyString())).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("messages must alternate between user and assistant"));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(ConversationFormatException.class)
                .hasMessageContaining("Invalid conversation format")
                .hasMessageContaining("Messages must alternate between user and assistant roles");
    }
}
