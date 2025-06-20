package uk.gegc.kidsgptbackend.service.chat.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.ai.chat.client.ChatClient;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageRequest;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageResponse;
import uk.gegc.kidsgptbackend.exception.ModerationServiceException;
import uk.gegc.kidsgptbackend.exception.RateLimitException;
import uk.gegc.kidsgptbackend.model.chat.ChatContext;
import uk.gegc.kidsgptbackend.model.chat.ChatMessage;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.chat.ChatContextRepository;
import uk.gegc.kidsgptbackend.repository.chat.ChatMessageRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.chat.AiChatService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional
public class AiChatServiceImpl implements AiChatService {

    private final ChatContextRepository contextRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatClient chatClient;
    private final ModerationModel moderationClient;
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

        if (!validateSafety(request.message())) {
            throw new IllegalArgumentException("User input flagged as unsafe");
        }

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ChatContext context = resolveContext(request, principal);

        ChatMessage userMsg = new ChatMessage();
        userMsg.setContext(context);
        userMsg.setRole("USER");
        userMsg.setContent(request.message());
        messageRepository.save(userMsg);

        String decorated = String.format(TEMPLATES[random.nextInt(TEMPLATES.length)], request.message());
        String systemText = loadSystemPrompt(user);

        ChatResponse chatResponse;
        try {
            chatResponse = chatClient.prompt()
                    .system(systemText)
                    .user(decorated)
                    .call()
                    .chatResponse();
        } catch (Exception e) {
            throw new RateLimitException("LLM rate-limited", e);
        }
        String replyText = Optional.ofNullable(chatResponse)
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AbstractMessage::getText)
                .orElse("");

        if (!validateSafety(replyText)) {
            replyText = "Oops, that topic's a bit tricky. Let's chat about something else fun!";
        }

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
        return new ChatMessageResponse(replyText, modelUsed, latency, tokensUsed, context.getId());    }

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

    private boolean validateSafety(String text) {
        ModerationResponse response;
        try {
            response = moderationClient.call(new ModerationPrompt(text));
        } catch (Exception ex) {
            logger.error("Moderation service call failed", ex);
            throw new ModerationServiceException("Moderation service unavailable", ex);
        }
        boolean safe = response.getResult().getOutput().getResults().stream()
                .noneMatch(ModerationResult::isFlagged);
        if (!safe) {
            response.getResult().getOutput().getResults().stream()
                    .filter(ModerationResult::isFlagged)
                    .forEach(r -> logger.warn("Moderation violation: {}", r.getCategories()));
        }
        return safe;
    }
}
