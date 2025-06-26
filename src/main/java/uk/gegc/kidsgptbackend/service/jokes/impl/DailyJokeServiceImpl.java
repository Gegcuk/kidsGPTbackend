package uk.gegc.kidsgptbackend.service.jokes.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import uk.gegc.kidsgptbackend.dto.jokes.DailyJokeDto;
import uk.gegc.kidsgptbackend.service.jokes.DailyJokeService;
import org.springframework.util.StreamUtils;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.util.ModerationUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyJokeServiceImpl implements DailyJokeService {

    private final ChatClient chatClient;
    private final ModerationUtil moderationUtil;
    private final ResourceLoader resourceLoader;
    private final Random random = new Random();

    private static final List<String> CATEGORIES = Arrays.asList(
            "animals", "school", "science", "wordplay", "food", "sports", "technology", "nature"
    );

    private static final List<String> JOKE_TYPES = Arrays.asList(
            "animal jokes", "knock-knock jokes", "school jokes", "science puns",
            "food jokes", "sports humor", "technology puns", "silly wordplay",
            "nature jokes", "number jokes", "color jokes", "adventure humor"
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
            String randomJokeType = JOKE_TYPES.get(random.nextInt(JOKE_TYPES.size()));
            log.info("Selected random joke type: {}", randomJokeType);

            ChatResponse response = chatClient.prompt()
                    .system(prompt)
                    .user("Tell me a " + randomJokeType + "!")
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
                    .orElse("Why don't elephants use computers? Because they're afraid of the mouse!");

            log.info("Extracted joke from AI response: '{}'", joke);
            log.info("Joke is fallback elephant joke: {}", joke.contains("elephants use computers"));

            // Validate the generated joke is appropriate for the age group
            log.info("Starting safety validation for age group: {}", ageGroup);
            boolean isSafe = moderationUtil.validateSafetyForAge(joke, ageGroup);
            log.info("Safety validation result: {}", isSafe);

            if (!isSafe) {
                log.warn("Generated joke failed moderation for age group {}, using fallback", ageGroup);
                joke = "Why don't elephants use computers? Because they're afraid of the mouse!";
            }

            DailyJokeDto dailyJoke = new DailyJokeDto();
            dailyJoke.setJoke(joke);
            dailyJoke.setCategory(CATEGORIES.get(random.nextInt(CATEGORIES.size())));
            dailyJoke.setAgeGroup(ageGroup.name());
            // imageUrl can be null for now, can be added later with image generation

            log.info("=== Successfully generated daily joke: {} ===", dailyJoke);
            return dailyJoke;

        } catch (Exception e) {
            log.error("=== Exception occurred during daily joke generation ===", e);
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Exception message: {}", e.getMessage());

            // Return a safe fallback joke
            DailyJokeDto fallback = new DailyJokeDto();
            fallback.setJoke("Why don't elephants use computers? Because they're afraid of the mouse!");
            fallback.setCategory("animals");
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
            return "Generate a fun, safe, and age-appropriate joke for a child.";
        }
    }

    // Removed - now using ModerationUtil
} 