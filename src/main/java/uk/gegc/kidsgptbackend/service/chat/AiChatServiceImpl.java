package uk.gegc.kidsgptbackend.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.ai.chat.client.ChatClient;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageRequest;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageResponse;
import uk.gegc.kidsgptbackend.model.chat.ChatContext;
import uk.gegc.kidsgptbackend.model.chat.ChatMessage;
import uk.gegc.kidsgptbackend.repository.chat.ChatContextRepository;
import uk.gegc.kidsgptbackend.repository.chat.ChatMessageRepository;
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

    @Value("classpath:system-prompt.txt")
    private Resource systemPrompt;

    private static final String[] TEMPLATES = {
            "%s Can you think of another example?",
            "Let's explore this: %s What else comes to mind?",
            "%s What do you think about it?"
    };
    private final Random random = new Random();

    @Override
    public ChatMessageResponse chat(ChatMessageRequest request, Principal principal) {
        Instant start = Instant.now();

        ChatContext context = resolveContext(request, principal);

        ChatMessage userMsg = new ChatMessage();
        userMsg.setContext(context);
        userMsg.setRole("USER");
        userMsg.setContent(request.message());
        messageRepository.save(userMsg);

        String decorated = String.format(TEMPLATES[random.nextInt(TEMPLATES.length)], request.getMessage());
        String systemText = loadSystemPrompt();

        String replyText;
        try {
            replyText = chatClient.prompt()
                    .system(systemText)
                    .user(decorated)
                    .call()
                    .content();
        } catch (Exception e) {
            throw new RuntimeException("LLM rate-limited", e);
        }

        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setContext(context);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(replyText);
        messageRepository.save(assistantMsg);

        long latency = Duration.between(start, Instant.now()).toMillis();
        return new ChatMessageResponse(replyText, "gpt-4o-mini", latency, 0, context.getId());
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

    private String loadSystemPrompt() {
        try {
            return StreamUtils.copyToString(systemPrompt.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "You are KidsGPT"; // fallback
        }
    }
}
