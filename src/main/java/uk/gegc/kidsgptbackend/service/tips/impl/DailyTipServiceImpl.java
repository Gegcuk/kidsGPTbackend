package uk.gegc.kidsgptbackend.service.tips.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import uk.gegc.kidsgptbackend.dto.tips.DailyTipDto;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.service.tips.DailyTipService;
import uk.gegc.kidsgptbackend.util.ModerationUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyTipServiceImpl implements DailyTipService {

    private final ChatClient chatClient;
    private final ModerationUtil moderationUtil;
    private final ResourceLoader resourceLoader;
    private final Random random = new Random();

    @Value("classpath:prompts/tips/categories.txt")
    private Resource categoriesResource;
    
    @Value("classpath:prompts/tips/topics.txt")
    private Resource topicsResource;
    
    @Value("classpath:prompts/tips/fallback-content.txt")
    private Resource fallbackContentResource;

    // Fallback lists if file loading fails
    private static final List<String> FALLBACK_CATEGORIES = Arrays.asList(
            "science", "nature", "space", "history", "animals", "geography", "technology", "art"
    );

    private static final List<String> FALLBACK_TOPICS = Arrays.asList(
            "space and planets", "animals and wildlife", "science and technology",
            "geography and nature", "human body and health", "art and creativity",
            "sports and games", "oceans and marine life", "weather and climate",
            "inventions and discoveries", "plants and trees", "music and sound"
    );

    private static final Map<String, String> FALLBACK_CONTENT = Map.of(
            "FALLBACK_FACT", "Did you know that honey never spoils? Archaeologists have found pots of honey in ancient Egyptian tombs that are over 3,000 years old and still perfectly edible!",
            "FALLBACK_PROMPT", "Generate a fun, safe, and educational fact for a child.",
            "USER_MESSAGE_TEMPLATE", "Give me a fun fact about %s!"
    );

    @Override
    public DailyTipDto getDailyTip() {
        return getDailyTip(AgeGroup.AGE_9_10); // Default age group
    }

    @Override
    public DailyTipDto getDailyTip(AgeGroup ageGroup) {
        log.info("=== Starting daily tip generation for age group: {} ===", ageGroup);

        String prompt = getPromptForAgeGroup(ageGroup);
        log.info("Loaded prompt for age group {}: {}", ageGroup, prompt);

        try {
            log.info("Making AI chat request...");
            List<String> topics = loadTopics();
            Map<String, String> fallbackContent = loadFallbackContent();
            
            String randomTopic = topics.get(random.nextInt(topics.size()));
            log.info("Selected random topic: {}", randomTopic);

            String userMessageTemplate = fallbackContent.get("USER_MESSAGE_TEMPLATE");
            String userMessage = String.format(userMessageTemplate, randomTopic);

            ChatResponse response = chatClient.prompt()
                    .system(prompt)
                    .user(userMessage)
                    .call()
                    .chatResponse();

            log.info("AI response received - Response object: {}", response != null ? "NOT_NULL" : "NULL");

            if (response != null) {
                log.info("AI response result: {}", response.getResult());
                if (response.getResult() != null) {
                    log.info("AI response generation: {}", response.getResult().getOutput());
                    if (response.getResult().getOutput() != null) {
                        log.info("AI response text: '{}'", response.getResult().getOutput().getText());
                    }
                }
            }

            String fact = Optional.ofNullable(response)
                    .map(ChatResponse::getResult)
                    .map(Generation::getOutput)
                    .map(AbstractMessage::getText)
                    .orElse(fallbackContent.get("FALLBACK_FACT"));

            log.info("Extracted fact from AI response: '{}'", fact);
            log.info("Fact is fallback honey fact: {}", fact.contains("honey never spoils"));

            // Validate the generated fact is appropriate for the age group
            log.info("Starting safety validation for age group: {}", ageGroup);
            boolean isSafe = moderationUtil.validateSafetyForAge(fact, ageGroup);
            log.info("Safety validation result: {}", isSafe);

            if (!isSafe) {
                log.warn("Generated fact failed moderation for age group {}, using fallback", ageGroup);
                fact = fallbackContent.get("FALLBACK_FACT");
            }

            DailyTipDto tip = new DailyTipDto();
            tip.setFact(fact);
            List<String> categories = loadCategories();
            tip.setCategory(categories.get(random.nextInt(categories.size())));
            tip.setAgeGroup(ageGroup.name());
            // imageUrl can be null for now, can be added later with image generation

            log.info("=== Successfully generated daily tip: {} ===", tip);
            return tip;

        } catch (Exception e) {
            log.error("=== Exception occurred during daily tip generation ===", e);
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Exception message: {}", e.getMessage());

            // Return a safe fallback fact
            Map<String, String> fallbackContent = loadFallbackContent();
            List<String> categories = loadCategories();
            DailyTipDto fallback = new DailyTipDto();
            fallback.setFact(fallbackContent.get("FALLBACK_FACT"));
            fallback.setCategory(categories.get(0)); // Use first category as safe fallback
            fallback.setAgeGroup(ageGroup.name());
            log.info("=== Returning fallback tip due to exception ===");
            return fallback;
        }
    }

    private String getPromptForAgeGroup(AgeGroup ageGroup) {
        log.info("=== Loading prompt for age group: {} ===", ageGroup);

        String resourcePath = switch (ageGroup) {
            case AGE_6_8 -> "classpath:prompts/tips/age-6-8.txt";
            case AGE_9_10 -> "classpath:prompts/tips/age-9-10.txt";
            case AGE_11_12 -> "classpath:prompts/tips/age-11-12.txt";
            case AGE_13_14 -> "classpath:prompts/tips/age-13-14.txt";
            case AGE_15_16 -> "classpath:prompts/tips/age-15-16.txt";
        };

        log.info("Resource path: {}", resourcePath);

        try {
            var resource = resourceLoader.getResource(resourcePath);
            log.info("Resource loaded: {}", resource != null ? "SUCCESS" : "NULL");
            log.info("Resource exists: {}", resource.exists());

            String prompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            log.info("Prompt loaded successfully, length: {} characters", prompt.length());
            log.info("Prompt content: '{}'", prompt);
            return prompt;
        } catch (Exception e) {
            log.error("=== Failed to load prompt for age group {} ===", ageGroup, e);
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Exception message: {}", e.getMessage());
            log.warn("Using fallback prompt for age group {}", ageGroup);
            Map<String, String> fallbackContent = loadFallbackContent();
            return fallbackContent.get("FALLBACK_PROMPT");
        }
    }

    /**
     * Loads categories from file.
     */
    private List<String> loadCategories() {
        try {
            if (categoriesResource == null) {
                log.warn("Categories resource is null, using fallback");
                return FALLBACK_CATEGORIES;
            }
            String content = StreamUtils.copyToString(categoriesResource.getInputStream(), StandardCharsets.UTF_8);
            return Arrays.asList(content.trim().split("\n"));
        } catch (IOException e) {
            log.warn("Failed to load categories, using fallback", e);
            return FALLBACK_CATEGORIES;
        }
    }

    /**
     * Loads topics from file.
     */
    private List<String> loadTopics() {
        try {
            if (topicsResource == null) {
                log.warn("Topics resource is null, using fallback");
                return FALLBACK_TOPICS;
            }
            String content = StreamUtils.copyToString(topicsResource.getInputStream(), StandardCharsets.UTF_8);
            return Arrays.asList(content.trim().split("\n"));
        } catch (IOException e) {
            log.warn("Failed to load topics, using fallback", e);
            return FALLBACK_TOPICS;
        }
    }

    /**
     * Loads fallback content from file.
     */
    private Map<String, String> loadFallbackContent() {
        try {
            if (fallbackContentResource == null) {
                log.warn("Fallback content resource is null, using fallback");
                return FALLBACK_CONTENT;
            }
            String content = StreamUtils.copyToString(fallbackContentResource.getInputStream(), StandardCharsets.UTF_8);
            Map<String, String> fallbackContent = new HashMap<>();
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    fallbackContent.put(parts[0].trim(), parts[1].trim());
                }
            }
            return fallbackContent;
        } catch (IOException e) {
            log.warn("Failed to load fallback content, using fallback", e);
            return FALLBACK_CONTENT;
        }
    }
} 