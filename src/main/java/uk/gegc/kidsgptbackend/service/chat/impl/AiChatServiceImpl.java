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
import uk.gegc.kidsgptbackend.exception.ModerationServiceException;

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
            "CONVERSATION_FORMAT_ERROR", "Invalid conversation format: Messages must alternate between user and assistant roles. Please check your context history.",
            "VALIDATION_MESSAGE_INAPPROPRIATE", "That's an interesting topic! But let's talk about something else that's more fun and appropriate for kids like you. What else would you like to explore? 🎈",
            "VALIDATION_MESSAGE_EMPTY", "Hi! I'm ready to chat, but I didn't see your message. What would you like to talk about today? 🌟",
            "VALIDATION_MESSAGE_TOO_SHORT", "Hey there! I didn't catch what you wanted to say. Could you tell me a bit more? I'm here to help! 😊",
            "VALIDATION_MESSAGE_TOO_LONG", "Wow, you have a lot to say! That's awesome! But could you break it down into smaller pieces? I like to focus on one thing at a time! 🤗",
            "VALIDATION_MESSAGE_GENERIC", "I want to make sure we have a great chat! Could you try asking me something different? I love talking about fun topics, stories, jokes, and learning new things! ✨"
    );

    private final Random random = new Random();
    private static final Logger logger = LoggerFactory.getLogger(AiChatServiceImpl.class);


    @Override
    public ChatMessageResponse chat(ChatMessageRequest request, Principal principal) {
        Instant start = Instant.now();

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Resolve context early so we can maintain it throughout the conversation
        ChatContext context = resolveContext(request, principal);

        // Validate basic request format first
        String basicValidationMessage = validateChatRequest(request, user);
        if (basicValidationMessage != null) {
            return generatePoliteRefusalResponse(request.message(), basicValidationMessage, user, context, start);
        }

        // Use comprehensive validation for user input - let ModerationServiceExceptions propagate
        try {
            if (!moderationUtil.validateComprehensive(request.message(), user, "chat message")) {
                return generatePoliteRefusalResponse(request.message(), "inappropriate content", user, context, start);
            }
        } catch (ModerationServiceException e) {
            // Re-throw service exceptions - don't handle them as validation failures
            throw e;
        }

        // Save only the new user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setContext(context);
        userMsg.setRole("USER");
        userMsg.setContent(request.message());
        messageRepository.save(userMsg);

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

        // Use age-aware validation for AI responses - if inappropriate, generate better response
        if (!moderationUtil.validateSafetyForAge(replyText, user.getAge() != null ? 
                AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10)) {
            
            // Generate a contextual response to handle AI response moderation failure
            return generatePoliteRefusalForAIResponse(request.message(), replyText, user, context, start);
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
        Map<String, String> fallbackMessages = loadFallbackMessages();
        String ageBasedPrompt = String.format(fallbackMessages.get("AGE_PREFIX_TEMPLATE"), user.getAge());
        try {
            String basePrompt = StreamUtils.copyToString(systemPrompt.getInputStream(), StandardCharsets.UTF_8);
            return ageBasedPrompt + basePrompt;
        } catch (IOException e) {
            return ageBasedPrompt + fallbackMessages.get("FALLBACK_SYSTEM_PROMPT");
        }
    }

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

    /**
     * Validates chat request and returns a specific reason if validation fails
     * @param request The chat request to validate
     * @param user The user making the request
     * @return null if valid, specific reason string if invalid
     */
    private String validateChatRequest(ChatMessageRequest request, User user) {
        if (request.message() == null || request.message().trim().isEmpty()) {
            return "empty message";
        }
        
        if (request.message().trim().length() < 3) {
            return "message too short";
        }
        
        if (request.message().length() > 1000) {
            return "message too long";
        }
        
        return null; // Valid
    }

    /**
     * Generates a polite, contextual refusal response when validation fails
     * @param originalMessage The original user message that failed validation
     * @param reason The reason for validation failure
     * @param user The user making the request
     * @param context The chat context to maintain conversation continuity
     * @param start The start time for latency calculation
     * @return A ChatMessageResponse with a polite refusal
     */
    private ChatMessageResponse generatePoliteRefusalResponse(String originalMessage, String reason, User user, ChatContext context, Instant start) {
        try {
            String politeRefusalPrompt = createPoliteRefusalPrompt(originalMessage, reason, user);
            
            ChatResponse chatResponse = chatClient.prompt()
                    .system(loadSystemPrompt(user))
                    .user(politeRefusalPrompt)
                    .call()
                    .chatResponse();
                    
            String generatedResponse = Optional.ofNullable(chatResponse)
                    .map(ChatResponse::getResult)
                    .map(Generation::getOutput)
                    .map(AbstractMessage::getText)
                    .orElse("");

            // Validate the generated response to ensure it's appropriate
            if (!moderationUtil.validateSafetyForAge(generatedResponse, user.getAge() != null ? 
                    AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10)) {
                // Fall back to predefined message if generated response is inappropriate
                return createFallbackValidationResponse(reason, user, context, start);
            }

            // Save the polite refusal response to maintain conversation history
            ChatMessage assistantMsg = new ChatMessage();
            assistantMsg.setContext(context);
            assistantMsg.setRole("ASSISTANT");
            assistantMsg.setContent(generatedResponse);
            messageRepository.save(assistantMsg);

            // Return the validated generated response as a normal chat response
            long latency = Duration.between(start, Instant.now()).toMillis();
            int tokensUsed = Optional.ofNullable(chatResponse)
                    .map(ChatResponse::getMetadata)
                    .map(meta -> meta.getUsage().getTotalTokens())
                    .orElse(0);
            String modelUsed = Optional.ofNullable(chatResponse)
                    .map(resp -> resp.getMetadata().getModel())
                    .orElse("gpt-4o-mini");
                    
            return new ChatMessageResponse(generatedResponse, modelUsed, latency, tokensUsed, context.getId());
            
        } catch (Exception e) {
            logger.warn("Failed to generate polite refusal response: {}", e.getMessage());
            // Fall back to predefined message if AI generation fails
            return createFallbackValidationResponse(reason, user, context, start);
        }
    }

    /**
     * Creates a prompt for generating polite refusal responses
     */
    private String createPoliteRefusalPrompt(String originalMessage, String reason, User user) {
        String ageContext = user.getAge() != null ? 
            String.format("for a %d-year-old child", user.getAge()) : "for a child";
            
        if ("inappropriate content".equals(reason)) {
            return String.format(
                "The user said: \"%s\"\n\n" +
                "This message contains content that isn't appropriate %s. " +
                "Please generate a very polite, friendly, and encouraging response that:\n" +
                "1. Acknowledges their interest without repeating inappropriate content\n" +
                "2. Gently redirects them to more appropriate topics\n" +
                "3. Suggests fun alternative topics they might enjoy\n" +
                "4. Keeps the tone positive and engaging\n" +
                "5. Uses appropriate emojis to keep it fun\n\n" +
                "Make it feel like a friendly conversation, not a rejection.",
                originalMessage, ageContext
            );
        } else if (reason.contains("empty")) {
            return String.format(
                "The user sent an empty message. Please generate a warm, welcoming response %s that:\n" +
                "1. Greets them warmly\n" +
                "2. Invites them to share what they'd like to talk about\n" +
                "3. Suggests some fun topics they might enjoy\n" +
                "4. Uses encouraging and friendly language\n" +
                "5. Includes appropriate emojis\n\n" +
                "Make it feel inviting and exciting to start a conversation.",
                ageContext
            );
        } else if (reason.contains("short")) {
            return String.format(
                "The user said: \"%s\"\n\n" +
                "This message is very short and unclear. Please generate a helpful response %s that:\n" +
                "1. Acknowledges their attempt to communicate\n" +
                "2. Politely asks them to tell you more\n" +
                "3. Shows enthusiasm to help\n" +
                "4. Suggests they can expand on their thought\n" +
                "5. Uses encouraging language and emojis\n\n" +
                "Make it feel supportive and encouraging.",
                originalMessage, ageContext
            );
        } else if (reason.contains("long")) {
            return String.format(
                "The user sent a very long message. Please generate a friendly response %s that:\n" +
                "1. Shows appreciation for their enthusiasm\n" +
                "2. Politely explains you work better with shorter messages\n" +
                "3. Encourages them to break it into smaller parts\n" +
                "4. Offers to help with one thing at a time\n" +
                "5. Uses positive and understanding language with emojis\n\n" +
                "Make it feel like you're excited to help, just in a different way.",
                ageContext
            );
        } else {
            return String.format(
                "The user's message had some issues. Please generate a kind response %s that:\n" +
                "1. Stays positive and encouraging\n" +
                "2. Invites them to try again\n" +
                "3. Suggests fun topics to explore\n" +
                "4. Shows enthusiasm for chatting\n" +
                "5. Uses friendly language and emojis\n\n" +
                "Make it feel welcoming and exciting.",
                ageContext
            );
        }
    }

    /**
     * Creates a fallback response when AI generation fails
     */
    private ChatMessageResponse createFallbackValidationResponse(String reason, User user, ChatContext context, Instant start) {
        Map<String, String> fallbackMessages = loadFallbackMessages();
        String message;
        
        if ("inappropriate content".equals(reason)) {
            message = fallbackMessages.get("VALIDATION_MESSAGE_INAPPROPRIATE");
        } else if (reason.contains("empty")) {
            message = fallbackMessages.get("VALIDATION_MESSAGE_EMPTY");
        } else if (reason.contains("short")) {
            message = fallbackMessages.get("VALIDATION_MESSAGE_TOO_SHORT");
        } else if (reason.contains("long")) {
            message = fallbackMessages.get("VALIDATION_MESSAGE_TOO_LONG");
        } else {
            message = fallbackMessages.get("VALIDATION_MESSAGE_GENERIC");
        }
        
        // Save the fallback message to maintain conversation history
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setContext(context);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(message);
        messageRepository.save(assistantMsg);
        
        long latency = Duration.between(start, Instant.now()).toMillis();
        return new ChatMessageResponse(message, "kidsGPT-fallback", latency, 0, context.getId());
    }

    /**
     * Generates a polite, contextual refusal response for AI response moderation failure
     * @param originalMessage The original user message that failed validation
     * @param replyText The AI generated reply text that failed validation
     * @param user The user making the request
     * @param context The chat context to maintain conversation continuity
     * @param start The start time for latency calculation
     * @return A ChatMessageResponse with a polite refusal
     */
    private ChatMessageResponse generatePoliteRefusalForAIResponse(String originalMessage, String replyText, User user, ChatContext context, Instant start) {
        try {
            String ageContext = user.getAge() != null ? 
                String.format("for a %d-year-old child", user.getAge()) : "for a child";
            
            String politeRefusalPrompt = String.format(
                "The user asked: \"%s\"\n\n" +
                "I generated a response that isn't quite appropriate %s. " +
                "Please generate a much better, age-appropriate response that:\n" +
                "1. Addresses their question or interest in a safe way\n" +
                "2. Redirects to more appropriate aspects of the topic if possible\n" +
                "3. Suggests related fun and educational topics they might enjoy\n" +
                "4. Maintains a warm, helpful, and engaging tone\n" +
                "5. Uses appropriate emojis to keep it friendly\n" +
                "6. Teaches them something positive if possible\n\n" +
                "Make it feel like a natural, helpful response to their question, not a rejection.",
                originalMessage, ageContext
            );
            
            ChatResponse chatResponse = chatClient.prompt()
                    .system(loadSystemPrompt(user))
                    .user(politeRefusalPrompt)
                    .call()
                    .chatResponse();
                    
            String generatedResponse = Optional.ofNullable(chatResponse)
                    .map(ChatResponse::getResult)
                    .map(Generation::getOutput)
                    .map(AbstractMessage::getText)
                    .orElse("");

            // Validate the newly generated response
            if (!moderationUtil.validateSafetyForAge(generatedResponse, user.getAge() != null ? 
                    AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10)) {
                // If this also fails, fall back to predefined safe message
                Map<String, String> fallbackMessages = loadFallbackMessages();
                generatedResponse = fallbackMessages.get("AI_MODERATION_FALLBACK");
            }

            // Save the polite refusal response to maintain conversation history
            ChatMessage assistantMsg = new ChatMessage();
            assistantMsg.setContext(context);
            assistantMsg.setRole("ASSISTANT");
            assistantMsg.setContent(generatedResponse);
            messageRepository.save(assistantMsg);

            // Return as normal chat response
            long latency = Duration.between(start, Instant.now()).toMillis();
            int tokensUsed = Optional.ofNullable(chatResponse)
                    .map(ChatResponse::getMetadata)
                    .map(meta -> meta.getUsage().getTotalTokens())
                    .orElse(0);
            String modelUsed = Optional.ofNullable(chatResponse)
                    .map(resp -> resp.getMetadata().getModel())
                    .orElse("gpt-4o-mini");
                    
            return new ChatMessageResponse(generatedResponse, modelUsed, latency, tokensUsed, context.getId());
            
        } catch (Exception e) {
            logger.warn("Failed to generate polite refusal for AI response: {}", e.getMessage());
            // Fall back to predefined message if AI generation fails
            Map<String, String> fallbackMessages = loadFallbackMessages();
            String fallbackMessage = fallbackMessages.get("AI_MODERATION_FALLBACK");
            
            // Save the fallback message to maintain conversation history
            ChatMessage assistantMsg = new ChatMessage();
            assistantMsg.setContext(context);
            assistantMsg.setRole("ASSISTANT");
            assistantMsg.setContent(fallbackMessage);
            messageRepository.save(assistantMsg);
            
            long latency = Duration.between(start, Instant.now()).toMillis();
            return new ChatMessageResponse(
                fallbackMessage, 
                "kidsGPT-fallback", 
                latency, 
                0, 
                context.getId()
            );
        }
    }
}
