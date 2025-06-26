package uk.gegc.kidsgptbackend.service.chat.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageDto;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageRequest;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageResponse;
import uk.gegc.kidsgptbackend.exception.ConversationFormatException;
import uk.gegc.kidsgptbackend.exception.RateLimitException;
import uk.gegc.kidsgptbackend.model.chat.ChatContext;
import uk.gegc.kidsgptbackend.model.chat.ChatMessage;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.chat.ChatContextRepository;
import uk.gegc.kidsgptbackend.repository.chat.ChatMessageRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.chat.AiChatService;
import uk.gegc.kidsgptbackend.util.ModerationUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional
public class AiChatServiceImpl implements AiChatService {

    private final ChatContextRepository contextRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatClient chatClient;
    private final ModerationUtil moderationUtil;
    private final UserRepository userRepository;

    @Value("classpath:system-prompt.txt")
    private Resource systemPrompt;

    private static final String[] TEMPLATES = {
            "%s Can you think of another example?",
            "Let's explore this: %s What else comes to mind?",
            "%s What do you think about it?"
    };
    private final Random random = new Random();
    private static final Logger logger = LoggerFactory.getLogger(AiChatServiceImpl.class);


    @Override
    public ChatMessageResponse chat(ChatMessageRequest request, Principal principal) {
        Instant start = Instant.now();

        if (!moderationUtil.validateSafety(request.message())) {
            throw new IllegalArgumentException("User input flagged as unsafe");
        }

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ChatContext context = resolveContext(request, principal);

        // Save only the new user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setContext(context);
        userMsg.setRole("USER");
        userMsg.setContent(request.message());
        messageRepository.save(userMsg);

        // Build conversation history from provided context
        List<Message> conversationHistory = buildConversationHistory(request.context());
        
        // Add the new user message to the conversation
        String decorated = String.format(TEMPLATES[random.nextInt(TEMPLATES.length)], request.message());
        conversationHistory.add(new UserMessage(decorated));

        String systemText = loadSystemPrompt(user);

        ChatResponse chatResponse;
        try {
            chatResponse = chatClient.prompt()
                    .system(systemText)
                    .messages(conversationHistory)
                    .call()
                    .chatResponse();
        } catch (Exception e) {
            // Check if the error is related to conversation format/constraints
            String errorMessage = e.getMessage();
            if (errorMessage != null && (
                errorMessage.contains("conversation") || 
                errorMessage.contains("alternating") || 
                errorMessage.contains("consecutive") ||
                errorMessage.contains("role") ||
                errorMessage.contains("messages must alternate")
            )) {
                throw new ConversationFormatException("Invalid conversation format: Messages must alternate between user and assistant roles. Please check your context history.", e);
            }
            throw new RateLimitException("LLM rate-limited", e);
        }
        String replyText = Optional.ofNullable(chatResponse)
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AbstractMessage::getText)
                .orElse("");

        if (!moderationUtil.validateSafety(replyText)) {
            replyText = "Oops, that topic's a bit tricky. Let's chat about something else fun!";
        }

        // Save only the new assistant message
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setContext(context);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(replyText);
        messageRepository.save(assistantMsg);

        long latency = Duration.between(start, Instant.now()).toMillis();
        int tokensUsed = Optional.ofNullable(chatResponse)
                .map(ChatResponse::getMetadata)
                .map(meta -> meta.getUsage().getTotalTokens())
                .orElse(0);
        String modelUsed = Optional.ofNullable(chatResponse)
                .map(resp -> resp.getMetadata().getModel())
                .orElse("gpt-4o-mini");
        return new ChatMessageResponse(replyText, modelUsed, latency, tokensUsed, context.getId());
    }

    private ChatContext resolveContext(ChatMessageRequest request, Principal principal) {
        if (request.contextId() != null) {
            Optional<ChatContext> opt = contextRepository.findById(request.contextId());
            return opt.orElseThrow(() -> new IllegalArgumentException("Context not found"));
        }
        ChatContext context = new ChatContext();
        context.setUsername(principal.getName());
        contextRepository.save(context);
        return context;
    }

    private String loadSystemPrompt(User user) {
        String ageBasedPrompt = "You are talking to a " + user.getAge() + "-year-old child. ";
        try {
            String basePrompt = StreamUtils.copyToString(systemPrompt.getInputStream(), StandardCharsets.UTF_8);
            return ageBasedPrompt + basePrompt;
        } catch (IOException e) {
            return ageBasedPrompt + "You are KidsGPT, keep replies friendly.";
        }
    }

    // Removed - now using ModerationUtil

    /**
     * Build conversation history from provided context
     */
    private List<Message> buildConversationHistory(List<ChatMessageDto> context) {
        List<Message> messages = new ArrayList<>();
        
        if (context != null && !context.isEmpty()) {
            for (ChatMessageDto messageDto : context) {
                if ("USER".equalsIgnoreCase(messageDto.role())) {
                    messages.add(new UserMessage(messageDto.content()));
                } else if ("ASSISTANT".equalsIgnoreCase(messageDto.role())) {
                    messages.add(new AssistantMessage(messageDto.content()));
                }
            }
        }
        
        return messages;
    }
}
