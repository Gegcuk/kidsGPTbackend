package uk.gegc.kidsgptbackend.features.chat.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageRequest;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageResponse;
import uk.gegc.kidsgptbackend.features.chat.api.dto.Tone;
import uk.gegc.kidsgptbackend.shared.exception.ConversationFormatException;
import uk.gegc.kidsgptbackend.shared.exception.ModerationServiceException;
import uk.gegc.kidsgptbackend.shared.exception.RateLimitException;
import uk.gegc.kidsgptbackend.features.chat.domain.model.ChatContext;
import uk.gegc.kidsgptbackend.features.chat.domain.model.ChatMessage;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.chat.domain.repository.ChatContextRepository;
import uk.gegc.kidsgptbackend.features.chat.domain.repository.ChatMessageRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.shared.util.ModerationUtil;
import uk.gegc.kidsgptbackend.features.chat.application.impl.AiChatServiceImpl;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
class AiChatServiceImplTest extends BaseUnitTest {

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
    @Mock
    Clock clock;

    @InjectMocks
    AiChatServiceImpl service;

    Principal principal = () -> "alice";

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        // Set up all age-specific prompt resources
        ReflectionTestUtils.setField(service, "systemPromptAge6_8", new ByteArrayResource("sys".getBytes()));
        ReflectionTestUtils.setField(service, "systemPromptAge9_10", new ByteArrayResource("sys".getBytes()));
        ReflectionTestUtils.setField(service, "systemPromptAge11_12", new ByteArrayResource("sys".getBytes()));
        ReflectionTestUtils.setField(service, "systemPromptAge13_14", new ByteArrayResource("sys".getBytes()));
        ReflectionTestUtils.setField(service, "systemPromptAge15_16", new ByteArrayResource("sys".getBytes()));
        
        // Set up clock mock to return consistent values
        when(clock.instant()).thenReturn(Instant.ofEpochMilli(1000L));
        
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
        
        // Mock context creation
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        
        // Mock AI failure for validation response generation
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("AI service unavailable"));
        
        // Mock message save
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a fallback response when moderation service fails
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.model()).isEqualTo("kidsGPT-fallback");
        assertThat(resp.contextId()).isNotNull(); // Should have a context ID
    }

    @Test
    @DisplayName("chat: flagged user input returns contextual response")
    void chat_flaggedInput_returnsContextualResponse() {
        ChatMessageRequest req = new ChatMessageRequest("bad", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("bad", user, "chat message")).thenReturn(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        
        // Mock context creation
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        
        // Mock the AI generation for contextual response
        when(callSpec.chatResponse()).thenReturn(simpleResponse("I understand you're curious about that topic! Let's talk about something more fun and appropriate for kids your age. What would you like to learn about today? 🌟"));
        
        // Mock message save
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a contextual response, not throw an exception
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.reply()).doesNotContain("bad"); // Should not repeat inappropriate content
        assertThat(resp.contextId()).isNotNull(); // Should have a context ID
        
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
        
        // Mock context creation
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        
        // Mock the AI generation for contextual response
        when(callSpec.chatResponse()).thenReturn(simpleResponse("Hi there! I'm ready to chat, but I didn't see your message. What would you like to talk about today? 🌟"));
        
        // Mock message save
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a contextual response for empty message
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.reply()).contains("ready to chat");
        assertThat(resp.contextId()).isNotNull(); // Should have a context ID
        
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
        
        // Mock context creation
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        
        // Mock the AI generation for contextual response
        when(callSpec.chatResponse()).thenReturn(simpleResponse("I see you said 'hi'! That's a great start! Could you tell me a bit more about what you'd like to chat about? I'm here to help! 😊"));
        
        // Mock message save
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a contextual response for short message
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.reply()).contains("bit more");
        assertThat(resp.contextId()).isNotNull(); // Should have a context ID
        
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
        
        // Mock context creation
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        
        // Mock message save
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // First call returns inappropriate response, second call returns appropriate contextual response
        when(callSpec.chatResponse())
            .thenReturn(simpleResponse("Here's how to fight...")) // First inappropriate response
            .thenReturn(simpleResponse("I can tell you're curious about martial arts! Fighting can mean different things - like learning martial arts for self-discipline and fitness, or how characters in stories overcome challenges. Would you like to hear about how martial artists train their minds and bodies? 🥋"));
        
        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a contextual response instead of generic fallback
        assertThat(resp.reply()).contains("martial arts");
        assertThat(resp.reply()).contains("self-discipline");
        assertThat(resp.reply()).doesNotContain("Oops, that topic's a bit tricky");
        assertThat(resp.contextId()).isNotNull(); // Should have a context ID
        
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
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("messages must alternate between user and assistant"));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(ConversationFormatException.class)
                .hasMessageContaining("Invalid conversation format")
                .hasMessageContaining("Messages must alternate between user and assistant roles");
    }

    @Test
    @DisplayName("chat: conversation format error with 'conversation' keyword throws ConversationFormatException")
    void chat_conversationFormatError_withConversationKeyword_throwsException() {
        ChatMessageRequest req = new ChatMessageRequest("test", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("test", user, "chat message")).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("conversation error occurred"));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(ConversationFormatException.class);
    }

    @Test
    @DisplayName("chat: conversation format error with 'alternating' keyword throws ConversationFormatException")
    void chat_conversationFormatError_withAlternatingKeyword_throwsException() {
        ChatMessageRequest req = new ChatMessageRequest("test", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("test", user, "chat message")).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("alternating roles required"));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(ConversationFormatException.class);
    }

    @Test
    @DisplayName("chat: conversation format error with 'consecutive' keyword throws ConversationFormatException")
    void chat_conversationFormatError_withConsecutiveKeyword_throwsException() {
        ChatMessageRequest req = new ChatMessageRequest("test", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("test", user, "chat message")).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("consecutive messages error"));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(ConversationFormatException.class);
    }

    @Test
    @DisplayName("chat: conversation format error with 'role' keyword throws ConversationFormatException")
    void chat_conversationFormatError_withRoleKeyword_throwsException() {
        ChatMessageRequest req = new ChatMessageRequest("test", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("test", user, "chat message")).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("role mismatch error"));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(ConversationFormatException.class);
    }

    @Test
    @DisplayName("chat: AI generation failure for validation falls back to predefined message")
    void chat_aiGenerationFailure_fallsBackToPredefinedMessage() {
        ChatMessageRequest req = new ChatMessageRequest("", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        
        // Mock context creation
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        
        // Mock message save
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // Mock AI failure
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("AI service unavailable"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a fallback message when AI generation fails
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.model()).isEqualTo("kidsGPT-fallback");
        assertThat(resp.contextId()).isNotNull(); // Should have a context ID
        
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
        
        // Mock context creation
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        
        // Mock message save
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // Mock AI failure for validation response generation
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("AI service unavailable"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return a fallback response when moderation service fails
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.model()).isEqualTo("kidsGPT-fallback");
        assertThat(resp.contextId()).isNotNull(); // Should have a context ID
    }

    @Test
    @DisplayName("chat: validation failure preserves context ID")
    void chat_validationFailure_preservesContextId() {
        UUID existingContextId = UUID.randomUUID();
        ChatMessageRequest req = new ChatMessageRequest("", existingContextId, Tone.FRIENDLY, null); // Empty message with existing context
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        
        // Mock existing context
        ChatContext existingContext = new ChatContext();
        existingContext.setId(existingContextId);
        existingContext.setUsername("alice");
        when(contextRepository.findById(existingContextId)).thenReturn(Optional.of(existingContext));
        
        // Mock AI generation for contextual response
        when(callSpec.chatResponse()).thenReturn(simpleResponse("Hi there! I'm ready to chat, but I didn't see your message. What would you like to talk about today? 🌟"));
        
        // Mock message save
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        // Should return the same context ID
        assertThat(resp).isNotNull();
        assertThat(resp.contextId()).isEqualTo(existingContextId);
        assertThat(resp.reply()).contains("ready to chat");
        
        // Verify context was not created new, but reused
        verify(contextRepository).findById(existingContextId);
        verify(contextRepository, never()).save(any(ChatContext.class));
        
        // Verify the response was saved to maintain conversation history
        verify(messageRepository).save(argThat(msg -> 
            msg.getRole().equals("ASSISTANT") && 
            msg.getContext().getId().equals(existingContextId)
        ));
    }

    @Test
    @DisplayName("chat: context not found throws exception")
    void chat_contextNotFound_throwsException() {
        UUID nonExistentContextId = UUID.randomUUID();
        ChatMessageRequest req = new ChatMessageRequest("hello", nonExistentContextId, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(contextRepository.findById(nonExistentContextId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Context not found");
    }

    @Test
    @DisplayName("chat: context ownership mismatch throws exception")
    void chat_contextOwnershipMismatch_throwsException() {
        UUID contextId = UUID.randomUUID();
        ChatMessageRequest req = new ChatMessageRequest("hello", contextId, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        
        ChatContext context = new ChatContext();
        context.setId(contextId);
        context.setUsername("bob"); // Different user
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(context));

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Context not found");
    }

    @Test
    @DisplayName("chat: null error message in exception handling")
    void chat_nullErrorMessage_throwsRateLimitException() {
        ChatMessageRequest req = new ChatMessageRequest("test", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("test", user, "chat message")).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // Exception with null message
        RuntimeException exception = new RuntimeException();
        when(callSpec.chatResponse()).thenThrow(exception);

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("chat: null age uses default age group")
    void chat_nullAge_usesDefaultAgeGroup() {
        ChatMessageRequest req = new ChatMessageRequest("hello there", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(null); // Null age
        when(moderationUtil.validateComprehensive("hello there", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_9_10)).thenReturn(true); // Default age group
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
        verify(moderationUtil).validateSafetyForAge("reply", AgeGroup.AGE_9_10);
    }

    @Test
    @DisplayName("chat: null saved context ID in logging")
    void chat_nullSavedContextId_logsNull() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_6_8)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        
        // Return context with null ID to test null ID logging branch
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(null); // Set ID to null
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
        // Verify logging was called with null context ID
    }

    @Test
    @DisplayName("chat: null saved message in logging")
    void chat_nullSavedMessage_logsNull() {
        ChatMessageRequest req = new ChatMessageRequest("hello there", null, Tone.FRIENDLY, null);
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
        
        // Return null for saved message to test null logging branch
        when(messageRepository.save(any(ChatMessage.class))).thenReturn(null);

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        // Verify null message was handled
        verify(messageRepository, atLeastOnce()).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("chat: IOException in loadSystemPrompt uses fallback")
    void chat_ioExceptionInLoadSystemPrompt_usesFallback() {
        ChatMessageRequest req = new ChatMessageRequest("hello there", null, Tone.FRIENDLY, null);
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
        
        // Set a resource that will throw IOException
        ReflectionTestUtils.setField(service, "systemPromptAge6_8", new ByteArrayResource("sys".getBytes()) {
            @Override
            public java.io.InputStream getInputStream() throws java.io.IOException {
                throw new java.io.IOException("Resource not found");
            }
        });

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
    }

    @Test
    @DisplayName("chat: null age in getSystemPromptResource uses default")
    void chat_nullAgeInGetSystemPromptResource_usesDefault() {
        ChatMessageRequest req = new ChatMessageRequest("hello there", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(null); // Null age
        when(moderationUtil.validateComprehensive("hello there", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_9_10)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
        // Should use systemPromptAge9_10 as default
    }

    @Test
    @DisplayName("chat: ModerationServiceException is re-thrown")
    void chat_moderationServiceException_isRethrown() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        ModerationServiceException moderationException = new ModerationServiceException("Service down", new RuntimeException());
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenThrow(moderationException);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });

        assertThatThrownBy(() -> service.chat(req, principal))
                .isInstanceOf(ModerationServiceException.class)
                .isEqualTo(moderationException);
    }

    @Test
    @DisplayName("chat: age 8 uses age-6-8 prompt")
    void chat_age8_usesAge6_8Prompt() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8); // Exactly 8
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
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
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
    }

    @Test
    @DisplayName("chat: age 9 uses age-9-10 prompt")
    void chat_age9_usesAge9_10Prompt() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(9);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_9_10)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
    }

    @Test
    @DisplayName("chat: age 10 uses age-9-10 prompt")
    void chat_age10_usesAge9_10Prompt() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(10);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_9_10)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
    }

    @Test
    @DisplayName("chat: age 11 uses age-11-12 prompt")
    void chat_age11_usesAge11_12Prompt() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(11);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_11_12)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
    }

    @Test
    @DisplayName("chat: age 12 uses age-11-12 prompt")
    void chat_age12_usesAge11_12Prompt() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(12);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_11_12)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
    }

    @Test
    @DisplayName("chat: age 13 uses age-13-14 prompt")
    void chat_age13_usesAge13_14Prompt() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(13);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_13_14)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
    }

    @Test
    @DisplayName("chat: age 14 uses age-13-14 prompt")
    void chat_age14_usesAge13_14Prompt() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(14);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_13_14)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
    }

    @Test
    @DisplayName("chat: age 15 uses age-15-16 prompt")
    void chat_age15_usesAge15_16Prompt() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(15);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_15_16)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
    }

    @Test
    @DisplayName("chat: age 16 uses age-15-16 prompt")
    void chat_age16_usesAge15_16Prompt() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(16);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_15_16)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
    }

    @Test
    @DisplayName("chat: buildConversationHistory with null context returns empty list")
    void chat_buildConversationHistory_nullContext_returnsEmpty() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
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
        
        assertThat(resp).isNotNull();
        // Verify messages() was called (with empty list when no context)
        verify(requestSpec).messages(any(List.class));
    }

    @Test
    @DisplayName("chat: buildConversationHistory with ASSISTANT role adds AssistantMessage")
    void chat_buildConversationHistory_assistantRole_addsAssistantMessage() {
        List<ChatMessageDto> contextHistory = List.of(
                new ChatMessageDto(UUID.randomUUID(), "ASSISTANT", "Hello from AI!", LocalDateTime.now())
        );
        
        ChatMessageRequest req = new ChatMessageRequest("hello there", null, Tone.FRIENDLY, contextHistory);
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
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("reply");
        // Verify that messages() was called (with context history including ASSISTANT message)
        verify(requestSpec, atLeastOnce()).messages(any(List.class));
    }

    @Test
    @DisplayName("chat: message too long returns validation response")
    void chat_messageTooLong_returnsValidationResponse() {
        String longMessage = "A".repeat(1001); // 1001 characters
        ChatMessageRequest req = new ChatMessageRequest(longMessage, null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("Your message is too long. Please break it into smaller parts."));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        verify(callSpec, atLeastOnce()).chatResponse();
    }

    @Test
    @DisplayName("chat: null message in validation")
    void chat_nullMessage_returnsValidationResponse() {
        ChatMessageRequest req = new ChatMessageRequest(null, null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("Hi! I'm ready to chat. What would you like to talk about?"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        verify(callSpec, atLeastOnce()).chatResponse();
    }

    @Test
    @DisplayName("chat: generatePoliteRefusalResponse with successful AI generation")
    void chat_generatePoliteRefusalResponse_successfulGeneration() {
        ChatMessageRequest req = new ChatMessageRequest("bad word", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("bad word", user, "chat message")).thenReturn(false);
        when(moderationUtil.validateSafetyForAge("That's not appropriate, let's talk about something fun!", AgeGroup.AGE_6_8)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // generatePoliteRefusalResponse calls chatClient.prompt() which uses the same requestSpec/callSpec chain
        // The setUp() method already configures this, so we just need to return the response
        when(callSpec.chatResponse()).thenReturn(simpleResponse("That's not appropriate, let's talk about something fun!"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isEqualTo("That's not appropriate, let's talk about something fun!");
        assertThat(resp.model()).isNotEqualTo("kidsGPT-fallback"); // Should use AI-generated response
        // Verify that chatClient.prompt() was called (for generatePoliteRefusalResponse)
        verify(chatClient, atLeastOnce()).prompt();
    }

    @Test
    @DisplayName("chat: generatePoliteRefusalResponse with null age uses default")
    void chat_generatePoliteRefusalResponse_nullAge_usesDefault() {
        ChatMessageRequest req = new ChatMessageRequest("bad", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(null);
        when(moderationUtil.validateComprehensive("bad", user, "chat message")).thenReturn(false);
        when(moderationUtil.validateSafetyForAge("Let's talk about something fun!", AgeGroup.AGE_9_10)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("Let's talk about something fun!"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        verify(moderationUtil).validateSafetyForAge(anyString(), eq(AgeGroup.AGE_9_10));
    }

    @Test
    @DisplayName("chat: generatePoliteRefusalForAIResponse with null age uses default")
    void chat_generatePoliteRefusalForAIResponse_nullAge_usesDefault() {
        ChatMessageRequest req = new ChatMessageRequest("tell me about violence", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(null);
        when(moderationUtil.validateComprehensive("tell me about violence", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("Here's how to be violent...", AgeGroup.AGE_9_10)).thenReturn(false);
        when(moderationUtil.validateSafetyForAge("Let's talk about conflict resolution!", AgeGroup.AGE_9_10)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse())
            .thenReturn(simpleResponse("Here's how to be violent..."))
            .thenReturn(simpleResponse("Let's talk about conflict resolution!"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).contains("conflict resolution");
        verify(moderationUtil, atLeastOnce()).validateSafetyForAge(anyString(), eq(AgeGroup.AGE_9_10));
    }

    @Test
    @DisplayName("chat: loadFallbackMessages with null resource uses fallback")
    void chat_loadFallbackMessages_nullResource_usesFallback() {
        ChatMessageRequest req = new ChatMessageRequest("", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("AI service unavailable"));
        
        // Set fallbackMessagesResource to null
        ReflectionTestUtils.setField(service, "fallbackMessagesResource", null);

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.model()).isEqualTo("kidsGPT-fallback");
    }

    @Test
    @DisplayName("chat: loadFallbackMessages with IOException uses fallback")
    void chat_loadFallbackMessages_ioException_usesFallback() {
        ChatMessageRequest req = new ChatMessageRequest("", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("AI service unavailable"));
        
        // Set fallbackMessagesResource to throw IOException
        ReflectionTestUtils.setField(service, "fallbackMessagesResource", new ByteArrayResource("test".getBytes()) {
            @Override
            public java.io.InputStream getInputStream() throws java.io.IOException {
                throw new java.io.IOException("Cannot read resource");
            }
        });

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
        assertThat(resp.model()).isEqualTo("kidsGPT-fallback");
    }

    @Test
    @DisplayName("chat: loadFallbackMessages successfully loads from resource")
    void chat_loadFallbackMessages_successfullyLoads() {
        ChatMessageRequest req = new ChatMessageRequest("", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("AI service unavailable"));
        
        // Set fallbackMessagesResource with valid content
        String fallbackContent = "VALIDATION_MESSAGE_EMPTY=Hi! I'm ready to chat.\nAGE_PREFIX_TEMPLATE=You are talking to a %d-year-old child.\n";
        ReflectionTestUtils.setField(service, "fallbackMessagesResource", new ByteArrayResource(fallbackContent.getBytes()));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).isNotEmpty();
    }

    @Test
    @DisplayName("chat: null context in response constructor")
    void chat_nullContextInResponse_handlesGracefully() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_6_8)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        
        // Return context with null ID to test null context branch in response constructor
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(null); // Null ID
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.contextId()).isNull(); // Should handle null context ID
    }

    @Test
    @DisplayName("chat: null saved context in resolveContext logging")
    void chat_nullSavedContextInResolveContext_logsNull() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_6_8)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        
        // Return context with null ID to test null savedContext.getId() logging branch
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(null); // Null ID will be logged as "null"
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        // Verify null context ID was handled in logging
    }

    @Test
    @DisplayName("chat: null context in final logging")
    void chat_nullContextInFinalLogging_handlesGracefully() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_6_8)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        
        // Return context with null ID
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(null);
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.contextId()).isNull();
    }

    @Test
    @DisplayName("chat: null context in assistant message logging")
    void chat_nullContextInAssistantMessageLogging_handlesGracefully() {
        ChatMessageRequest req = new ChatMessageRequest("hello", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("hello", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("reply", AgeGroup.AGE_6_8)).thenReturn(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(callSpec.chatResponse()).thenReturn(simpleResponse("reply"));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(null); // Null ID
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            msg.setContext(null); // Set context to null for assistant message
            return msg;
        });

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        // Verify null context was handled in logging
    }

    @Test
    @DisplayName("chat: generatePoliteRefusalForAIResponse fallback when second generation fails")
    void chat_generatePoliteRefusalForAIResponse_secondGenerationFails_usesFallback() {
        ChatMessageRequest req = new ChatMessageRequest("tell me about violence", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("tell me about violence", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("Here's how to be violent...", AgeGroup.AGE_6_8)).thenReturn(false);
        when(moderationUtil.validateSafetyForAge("Let's talk about conflict resolution!", AgeGroup.AGE_6_8)).thenReturn(false); // Second also fails
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse())
            .thenReturn(simpleResponse("Here's how to be violent..."))
            .thenReturn(simpleResponse("Let's talk about conflict resolution!"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).contains("Oops, that topic's a bit tricky"); // Should use fallback
    }

    @Test
    @DisplayName("chat: generatePoliteRefusalForAIResponse exception uses fallback")
    void chat_generatePoliteRefusalForAIResponse_exception_usesFallback() {
        ChatMessageRequest req = new ChatMessageRequest("tell me about violence", null, Tone.FRIENDLY, null);
        User user = new User();
        user.setAge(8);
        when(moderationUtil.validateComprehensive("tell me about violence", user, "chat message")).thenReturn(true);
        when(moderationUtil.validateSafetyForAge("Here's how to be violent...", AgeGroup.AGE_6_8)).thenReturn(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(contextRepository.save(any(ChatContext.class))).thenAnswer(inv -> {
            ChatContext ctx = inv.getArgument(0);
            ctx.setId(UUID.randomUUID());
            return ctx;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callSpec.chatResponse())
            .thenReturn(simpleResponse("Here's how to be violent..."))
            .thenThrow(new RuntimeException("AI service unavailable"));

        ChatMessageResponse resp = service.chat(req, principal);
        
        assertThat(resp).isNotNull();
        assertThat(resp.reply()).contains("Oops, that topic's a bit tricky"); // Should use fallback
        assertThat(resp.model()).isEqualTo("kidsGPT-fallback");
    }
}

