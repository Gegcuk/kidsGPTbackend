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
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
    @DisplayName("chat: moderation failure returns contextual response")
    void chat_moderationFailure_exception() {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), eq("chat message")))
                .thenThrow(new ModerationServiceException("down", new RuntimeException()));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        
        // Mock AI failure for validation response generation
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("AI service unavailable"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a fallback response when moderation service fails
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.model()).isEqualTo("kidsGPT-fallback");
    }

    @Test
    @DisplayName("chat: flagged user input returns contextual response")
    void chat_flaggedInput_returnsContextualResponse() {
        ChatMessageRequest req = new ChatMessageRequest("bad", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("bad", user, "chat message")).thenReturn(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        
        // Mock the AI generation for contextual response
        when(callSpec.chatResponse()).thenReturn(simpleResponse("I understand you're curious about that topic! Let's talk about something more fun and appropriate for kids your age. What would you like to learn about today? 🌟"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a contextual response, not throw an exception
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.reply()).doesNotContain("bad"); // Should not repeat inappropriate content
        
        // Verify that the AI was called to generate a contextual response
        verify(callSpec, atLeastOnce()).chatResponse();
    }

    @Test
    @DisplayName("chat: empty message returns contextual response")
    void chat_emptyMessage_returnsContextualResponse() {
        ChatMessageRequest req = new ChatMessageRequest("", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        
        // Mock the AI generation for contextual response
        when(callSpec.chatResponse()).thenReturn(simpleResponse("Hi there! I'm ready to chat, but I didn't see your message. What would you like to talk about today? 🌟"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a contextual response for empty message
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.reply()).contains("ready to chat");
        
        // Verify that the AI was called to generate a contextual response
        verify(callSpec, atLeastOnce()).chatResponse();
    }

    @Test
    @DisplayName("chat: too short message returns contextual response")
    void chat_tooShortMessage_returnsContextualResponse() {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        
        // Mock the AI generation for contextual response
        when(callSpec.chatResponse()).thenReturn(simpleResponse("I see you said 'hi'! That's a great start! Could you tell me a bit more about what you'd like to chat about? I'm here to help! 😊"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a contextual response for short message
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.reply()).contains("bit more");
        
        // Verify that the AI was called to generate a contextual response
        verify(callSpec, atLeastOnce()).chatResponse();
    }



    @Test
    @DisplayName("chat: flagged AI reply generates better contextual response")
    void chat_flaggedReply_generatesContextualResponse() {
        ChatMessageRequest req = new ChatMessageRequest("tell me about fighting", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("tell me about fighting", user, "chat message")).thenReturn(true);
        
        // First moderation call returns false (inappropriate), second returns true (appropriate)
        when(moderationUtil.validateSafetyForAge("Here's how to fight...", AgeGroup.AGE_6_8)).thenReturn(false);
        when(moderationUtil.validateSafetyForAge(argThat(s -> s.contains("martial arts")), eq(AgeGroup.AGE_6_8))).thenReturn(true);
        
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        
        // First call returns inappropriate response, second call returns appropriate contextual response
        when(callSpec.chatResponse())
            .thenReturn(simpleResponse("Here's how to fight...")) // First inappropriate response
            .thenReturn(simpleResponse("I can tell you're curious about martial arts! Fighting can mean different things - like learning martial arts for self-discipline and fitness, or how characters in stories overcome challenges. Would you like to hear about how martial artists train their minds and bodies? 🥋"));
        
        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a contextual response instead of generic fallback
        assertThat(resp.reply()).contains("martial arts");
        assertThat(resp.reply()).contains("self-discipline");
        assertThat(resp.reply()).doesNotContain("Oops, that topic's a bit tricky");
        
        // Verify that the AI was called twice - once for original, once for contextual response
        verify(callSpec, times(2)).chatResponse();
    }

    @Test
    @DisplayName("chat: chat client exception for normal chat results in RateLimitException")
    void chat_chatClientException_rateLimit() {
        ChatMessageRequest req = new ChatMessageRequest("hello there", null, Tone.FRIENDLY, null); // Valid message
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("hello there", user, "chat message")).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("chat: null context creates new context for valid messages")
    void chat_nullContext_createsContext() {
        ChatMessageRequest req = new ChatMessageRequest("hello there", null, Tone.FRIENDLY, null); // Valid message
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("hello there", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_6_8)).thenReturn(true);
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
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("How are you?", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("I'm doing great!", AgeGroup.AGE_6_8)).thenReturn(true);
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
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("test", user, "chat message")).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("messages must alternate between user and assistant"));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(ConversationFormatException.class)
                .hasMessageContaining("Invalid conversation format")
                .hasMessageContaining("Messages must alternate between user and assistant roles");
    }

    @Test
    @DisplayName("chat: AI generation failure for validation falls back to predefined message")
    void chat_aiGenerationFailure_fallsBackToPredefinedMessage() {
        ChatMessageRequest req = new ChatMessageRequest("", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        
        // Mock AI failure
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("AI service unavailable"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a fallback message when AI generation fails
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.model()).isEqualTo("kidsGPT-fallback");
        
        // Should still have attempted to call AI
        verify(callSpec, atLeastOnce()).chatResponse();
    }

    @Test
    @DisplayName("chat: moderation service failure returns fallback response")
    void chat_moderationFailure_returnsFallback() {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive(anyString(), any(User.class), eq("chat message")))
                .thenThrow(new ModerationServiceException("down", new RuntimeException()));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        
        // Mock AI failure for validation response generation
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("AI service unavailable"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a fallback response when moderation service fails
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.model()).isEqualTo("kidsGPT-fallback");
    }
}
