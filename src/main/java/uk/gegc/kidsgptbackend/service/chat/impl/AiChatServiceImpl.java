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
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Value("classpath:prompts/chat/chat-templates.txt")
    private Resource chatTemplatesResource;
    
    @Value("classpath:prompts/chat/fallback-messages.txt")
    private Resource fallbackMessagesResource;

    // Fallback templates if file loading fails
    private static final String[] FALLBACK_TEMPLATES = {
            "%s Can you think of another example?",
            "Let's explore this: %s What else comes to mind?",
            "%s What do you think about it?"
    };

    private static final Map<String, String> FALLBACK_MESSAGES = Map.of(
            "AGE_PREFIX_TEMPLATE", "You are talking to a %d-year-old child. ",
            "FALLBACK_SYSTEM_PROMPT", "You are KidsGPT, keep replies friendly.",
            "AI_MODERATION_FALLBACK", "Oops, that topic's a bit tricky. Let's chat about something else fun!",
            "CONVERSATION_FORMAT_ERROR", "Invalid conversation format: Messages must alternate between user and assistant roles. Please check your context history."
    );

    private final Random random = new Random();
    private static final Logger logger = LoggerFactory.getLogger(AiChatServiceImpl.class);


    @Override
    public ChatMessageResponse chat(ChatMessageRequest request, Principal principal) {
        Instant start = Instant.now();
        
        logger.info("=== AICHATSERVICE PROCESSING START ===");
        logger.info("User: {}", principal.getName());
        logger.info("ContextId: {}", request.contextId());
        logger.info("Tone: {}", request.tone());
        logger.info("User Message: '{}'", request.message());

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Use comprehensive validation for user input (includes both basic and AI-based validation)
        if (!moderationUtil.validateComprehensive(request.message(), user, "chat message")) {
            throw new IllegalArgumentException("User input flagged as unsafe for age group");
        }

        ChatContext context = resolveContext(request, principal);
        logger.info("AiChatService: Using context ID={} for user={}", 
            context != null ? context.getId() : "null", principal.getName());

        // Save only the new user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setContext(context);
        userMsg.setRole("USER");
        userMsg.setContent(request.message());
        ChatMessage savedUserMsg = messageRepository.save(userMsg);
        logger.info("Saved user message - ID={}, ContextId={}, Content='{}'", 
            savedUserMsg != null ? savedUserMsg.getId() : "null", 
            context != null ? context.getId() : "null", 
            savedUserMsg != null ? savedUserMsg.getContent() : "null");

        // Build conversation history from provided context
        List<Message> conversationHistory = buildConversationHistory(request.context());
        
        // Add the new user message to the conversation
        String[] templates = loadChatTemplates();
        String decorated = String.format(templates[random.nextInt(templates.length)], request.message());
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
                Map<String, String> fallbackMessages = loadFallbackMessages();
                throw new ConversationFormatException(fallbackMessages.get("CONVERSATION_FORMAT_ERROR"), e);
            }
            throw new RateLimitException("LLM rate-limited", e);
        }
        String replyText = Optional.ofNullable(chatResponse)
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AbstractMessage::getText)
                .orElse("");

        // Use age-aware validation for AI responses to ensure appropriateness for the user's age
        if (!moderationUtil.validateSafetyForAge(replyText, user.getAge() != null ? 
                AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10)) {
            Map<String, String> fallbackMessages = loadFallbackMessages();
            replyText = fallbackMessages.get("AI_MODERATION_FALLBACK");

        }

        // Save only the new assistant message
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setContext(context);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(replyText);
        ChatMessage savedAssistantMsg = messageRepository.save(assistantMsg);
        logger.info("Saved assistant message - ID={}, ContextId={}, Content='{}'", 
            savedAssistantMsg != null ? savedAssistantMsg.getId() : "null", 
            context != null ? context.getId() : "null", 
            savedAssistantMsg != null ? savedAssistantMsg.getContent() : "null");

        long latency = Duration.between(start, Instant.now()).toMillis();
        int tokensUsed = Optional.ofNullable(chatResponse)
                .map(ChatResponse::getMetadata)
                .map(meta -> meta.getUsage().getTotalTokens())
                .orElse(0);
        String modelUsed = Optional.ofNullable(chatResponse)
                .map(resp -> resp.getMetadata().getModel())
                .orElse("gpt-4o-mini");
        
        ChatMessageResponse response = new ChatMessageResponse(replyText, modelUsed, latency, tokensUsed, 
            context != null ? context.getId() : null);
        logger.info("=== AICHATSERVICE PROCESSING COMPLETE ===");
        logger.info("User: {}", principal.getName());
        logger.info("ContextId: {}", context != null ? context.getId() : "null");
        logger.info("Model: {}", modelUsed);
        logger.info("Tokens Used: {}", tokensUsed);
        logger.info("Latency: {}ms", latency);
        logger.info("AI Reply: '{}'", replyText);
        logger.info("=== AICHATSERVICE PROCESSING END ===");
        
        return response;
    }

    private ChatContext resolveContext(ChatMessageRequest request, Principal principal) {
        if (request.contextId() != null) {
            logger.info("AiChatService: Resolving existing contextId={} for user={}", request.contextId(), principal.getName());
            Optional<ChatContext> opt = contextRepository.findById(request.contextId());
            if (opt.isPresent()) {
                ChatContext context = opt.get();
                if (!context.getUsername().equals(principal.getName())) {
                    logger.error("AiChatService: Context ownership mismatch - contextId={} belongs to user={}, requested by user={}", 
                        request.contextId(), context.getUsername(), principal.getName());
                    throw new IllegalArgumentException("Context not found");
                }
                logger.info("AiChatService: Found existing context contextId={} for user={}", context.getId(), principal.getName());
                return context;
            } else {
                logger.error("AiChatService: Context not found contextId={} for user={}", request.contextId(), principal.getName());
                throw new IllegalArgumentException("Context not found");
            }
        }
        ChatContext context = new ChatContext();
        context.setUsername(principal.getName());
        ChatContext savedContext = contextRepository.save(context);
        logger.info("AiChatService: Created new contextId={} for user={}", 
            savedContext != null ? savedContext.getId() : "null", principal.getName());
        return savedContext;
    }

    private String loadSystemPrompt(User user) {
        Map<String, String> fallbackMessages = loadFallbackMessages();
        String ageBasedPrompt = String.format(fallbackMessages.get("AGE_PREFIX_TEMPLATE"), user.getAge());
        try {
            String basePrompt = StreamUtils.copyToString(systemPrompt.getInputStream(), StandardCharsets.UTF_8);
            return ageBasedPrompt + basePrompt;
        } catch (IOException e) {
            return ageBasedPrompt + fallbackMessages.get("FALLBACK_SYSTEM_PROMPT");
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

    /**
     * Loads chat templates from file.
     */
    private String[] loadChatTemplates() {
        try {
            if (chatTemplatesResource == null) {
                logger.warn("Chat templates resource is null, using fallback");
                return FALLBACK_TEMPLATES;
            }
            String content = StreamUtils.copyToString(chatTemplatesResource.getInputStream(), StandardCharsets.UTF_8);
            return content.trim().split("\n");
        } catch (IOException e) {
            logger.warn("Failed to load chat templates, using fallback", e);
            return FALLBACK_TEMPLATES;
        }
    }

    /**
     * Loads fallback messages from file.
     */
    private Map<String, String> loadFallbackMessages() {
        try {
            if (fallbackMessagesResource == null) {
                logger.warn("Fallback messages resource is null, using fallback");
                return FALLBACK_MESSAGES;
            }
            String content = StreamUtils.copyToString(fallbackMessagesResource.getInputStream(), StandardCharsets.UTF_8);
            Map<String, String> messages = new HashMap<>();
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    messages.put(parts[0].trim(), parts[1].trim());
                }
            }
            return messages;
        } catch (IOException e) {
            logger.warn("Failed to load fallback messages, using fallback", e);
            return FALLBACK_MESSAGES;
        }
    }
}
