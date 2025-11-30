package uk.gegc.kidsgptbackend.features.jokes.application.impl;

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
import uk.gegc.kidsgptbackend.features.jokes.api.dto.DailyJokeDto;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.features.jokes.application.DailyJokeService;
import uk.gegc.kidsgptbackend.shared.util.ModerationUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyJokeServiceImpl implements DailyJokeService {

    private final ChatClient chatClient;
    private final ModerationUtil moderationUtil;
    private final ResourceLoader resourceLoader;
    private final Random random = new Random();

    @Value("classpath:prompts/jokes/categories.txt")
    private Resource categoriesResource;
    
    @Value("classpath:prompts/jokes/joke-types.txt")
    private Resource jokeTypesResource;
    
    @Value("classpath:prompts/jokes/fallback-content.txt")
    private Resource fallbackContentResource;

    // Fallback lists if file loading fails
    private static final List<String> FALLBACK_CATEGORIES = Arrays.asList(
            "animals", "school", "science", "wordplay", "food", "sports", "technology", "nature"
    );

    private static final List<String> FALLBACK_JOKE_TYPES = Arrays.asList(
            "animal jokes", "knock-knock jokes", "school jokes", "science puns",
            "food jokes", "sports humor", "technology puns", "silly wordplay",
            "nature jokes", "number jokes", "color jokes", "adventure humor"
    );

    private static final Map<String, String> FALLBACK_CONTENT = Map.of(
            "FALLBACK_JOKE", "Why don't elephants use computers? Because they're afraid of the mouse!",
            "FALLBACK_PROMPT", "Generate a fun, safe, and age-appropriate joke for a child.",
            "USER_MESSAGE_TEMPLATE", "Tell me a %s!"
    );

    @Override
    public DailyJokeDto getDailyJoke() {
        return getDailyJoke(AgeGroup.AGE_9_10); // Default age group
    }

    @Override
    public DailyJokeDto getDailyJoke(AgeGroup ageGroup) {
        log.info("=== Starting daily joke generation for age group: {} ===", ageGroup);

        String prompt = getPromptForAgeGroup(ageGroup);
        log.info("Loaded prompt for age group {}: {}", ageGroup, prompt);

        try {
            log.info("Making AI chat request...");
            List<String> jokeTypes = loadJokeTypes();
            Map<String, String> fallbackContent = loadFallbackContent();
            
            String randomJokeType = jokeTypes.get(random.nextInt(jokeTypes.size()));
            log.info("Selected random joke type: {}", randomJokeType);

            String userMessageTemplate = fallbackContent.get("USER_MESSAGE_TEMPLATE");
            String userMessage = String.format(userMessageTemplate, randomJokeType);

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

            String joke = Optional.ofNullable(response)
                    .map(ChatResponse::getResult)
                    .map(Generation::getOutput)
                    .map(AbstractMessage::getText)
                    .orElse(fallbackContent.get("FALLBACK_JOKE"));

            log.info("Extracted joke from AI response: '{}'", joke);
            log.info("Joke is fallback elephant joke: {}", joke.contains("elephants use computers"));

            // Validate the generated joke is appropriate for the age group
            log.info("Starting safety validation for age group: {}", ageGroup);
            boolean isSafe = moderationUtil.validateSafetyForAge(joke, ageGroup);
            log.info("Safety validation result: {}", isSafe);

            if (!isSafe) {
                log.warn("Generated joke failed moderation for age group {}, using fallback", ageGroup);
                joke = fallbackContent.get("FALLBACK_JOKE");
            }

            DailyJokeDto dailyJoke = new DailyJokeDto();
            dailyJoke.setJoke(joke);
            List<String> categories = loadCategories();
            dailyJoke.setCategory(categories.get(random.nextInt(categories.size())));
            dailyJoke.setAgeGroup(ageGroup.name());
            // imageUrl can be null for now, can be added later with image generation

            log.info("=== Successfully generated daily joke: {} ===", dailyJoke);
            return dailyJoke;

        } catch (Exception e) {
            log.error("=== Exception occurred during daily joke generation ===", e);
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Exception message: {}", e.getMessage());

            // Return a safe fallback joke
            Map<String, String> fallbackContent = loadFallbackContent();
            List<String> categories = loadCategories();
            DailyJokeDto fallback = new DailyJokeDto();
            fallback.setJoke(fallbackContent.get("FALLBACK_JOKE"));
            fallback.setCategory(categories.get(0)); // Use first category as safe fallback
            fallback.setAgeGroup(ageGroup.name());
            log.info("=== Returning fallback joke due to exception ===");
            return fallback;
        }
    }

    private String getPromptForAgeGroup(AgeGroup ageGroup) {
        log.info("=== Loading prompt for age group: {} ===", ageGroup);

        String resourcePath = switch (ageGroup) {
            case AGE_6_8 -> "classpath:prompts/jokes/age-6-8.txt";
            case AGE_9_10 -> "classpath:prompts/jokes/age-9-10.txt";
            case AGE_11_12 -> "classpath:prompts/jokes/age-11-12.txt";
            case AGE_13_14 -> "classpath:prompts/jokes/age-13-14.txt";
            case AGE_15_16 -> "classpath:prompts/jokes/age-15-16.txt";
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
     * Loads joke types from file.
     */
    private List<String> loadJokeTypes() {
        try {
            if (jokeTypesResource == null) {
                log.warn("Joke types resource is null, using fallback");
                return FALLBACK_JOKE_TYPES;
            }
            String content = StreamUtils.copyToString(jokeTypesResource.getInputStream(), StandardCharsets.UTF_8);
            return Arrays.asList(content.trim().split("\n"));
        } catch (IOException e) {
            log.warn("Failed to load joke types, using fallback", e);
            return FALLBACK_JOKE_TYPES;
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