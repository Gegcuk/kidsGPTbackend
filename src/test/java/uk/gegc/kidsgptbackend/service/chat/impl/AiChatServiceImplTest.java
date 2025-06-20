package uk.gegc.kidsgptbackend.service.chat.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageRequest;
import uk.gegc.kidsgptbackend.dto.chat.Tone;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageResponse;
import uk.gegc.kidsgptbackend.exception.RateLimitException;
import uk.gegc.kidsgptbackend.model.chat.ChatContext;
import uk.gegc.kidsgptbackend.model.chat.ChatMessage;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.chat.ChatContextRepository;
import uk.gegc.kidsgptbackend.repository.chat.ChatMessageRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationResult;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    ModerationModel moderationClient;
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
        when(requestSpec.call()).thenReturn(callSpec);
    }

    private ModerationResponse safeModeration() {
        Moderation mod = Moderation.builder()
                .results(List.of(new ModerationResult.Builder().flagged(false).build()))
                .build();
        return new ModerationResponse(new org.springframework.ai.moderation.Generation(mod));
    }

    private ModerationResponse flaggedModeration() {
        Moderation mod = Moderation.builder()
                .results(List.of(new ModerationResult.Builder().flagged(true).build()))
                .build();
        return new ModerationResponse(new org.springframework.ai.moderation.Generation(mod));
    }

    private ChatResponse simpleResponse(String text) {
        AssistantMessage m = new AssistantMessage(text);
        Generation gen = new Generation(m);
        ChatResponseMetadata meta = ChatResponseMetadata.builder().model("model").build();
        return ChatResponse.builder().generations(List.of(gen)).metadata(meta).build();
    }

    @Test
    @DisplayName("chat: flagged user input throws IllegalArgumentException")
    void chat_flaggedInput_throws() {
        ChatMessageRequest req = new ChatMessageRequest("bad", null, Tone.FRIENDLY);
        when(moderationClient.call(any(ModerationPrompt.class))).thenReturn(flaggedModeration());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("chat: chat client exception results in RateLimitException")
    void chat_chatClientException_rateLimit() {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY);
        when(moderationClient.call(any(ModerationPrompt.class)))
                .thenReturn(safeModeration());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("chat: flagged reply gets sanitized")
    void chat_flaggedReply_sanitized() {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY);
        when(moderationClient.call(any(ModerationPrompt.class)))
                .thenReturn(safeModeration())
                .thenReturn(flaggedModeration());
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
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY);
        when(moderationClient.call(any(ModerationPrompt.class)))
                .thenReturn(safeModeration())
                .thenReturn(safeModeration());
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
}
