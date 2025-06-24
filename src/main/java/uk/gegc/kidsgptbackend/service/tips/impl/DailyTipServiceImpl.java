package uk.gegc.kidsgptbackend.service.tips.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import uk.gegc.kidsgptbackend.dto.tips.DailyTipDto;
import uk.gegc.kidsgptbackend.exception.ModerationServiceException;
import uk.gegc.kidsgptbackend.service.tips.DailyTipService;
import org.springframework.util.StreamUtils;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyTipServiceImpl implements DailyTipService {

    private final ChatClient chatClient;
    private final ModerationModel moderationClient;
    private final ResourceLoader resourceLoader;
    private final Random random = new Random();

    private static final List<String> CATEGORIES = Arrays.asList(
            "science", "nature", "space", "history", "animals", "geography", "technology", "art"
    );

    private static final List<String> TOPICS = Arrays.asList(
            "space and planets", "animals and wildlife", "science and technology",
            "geography and nature", "human body and health", "art and creativity",
            "sports and games", "oceans and marine life", "weather and climate",
            "inventions and discoveries", "plants and trees", "music and sound"
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
            String randomTopic = TOPICS.get(random.nextInt(TOPICS.size()));
            log.info("Selected random topic: {}", randomTopic);

            ChatResponse response = chatClient.prompt()
                    .system(prompt)
                    .user("Give me a fun fact about " + randomTopic + "!")
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
                    .orElse("Did you know that honey never spoils? Archaeologists have found pots of honey in ancient Egyptian tombs that are over 3,000 years old and still perfectly edible!");

            log.info("Extracted fact from AI response: '{}'", fact);
            log.info("Fact is fallback honey fact: {}", fact.contains("honey never spoils"));

            // Validate the generated fact is appropriate for the age group
            log.info("Starting safety validation for age group: {}", ageGroup);
            boolean isSafe = validateSafety(fact, ageGroup);
            log.info("Safety validation result: {}", isSafe);

            if (!isSafe) {
                log.warn("Generated fact failed moderation for age group {}, using fallback", ageGroup);
                fact = "Did you know that honey never spoils? Archaeologists have found pots of honey in ancient Egyptian tombs that are over 3,000 years old and still perfectly edible!";
            }

            DailyTipDto tip = new DailyTipDto();
            tip.setFact(fact);
            tip.setCategory(CATEGORIES.get(random.nextInt(CATEGORIES.size())));
            tip.setAgeGroup(ageGroup.name());
            // imageUrl can be null for now, can be added later with image generation

            log.info("=== Successfully generated daily tip: {} ===", tip);
            return tip;

        } catch (Exception e) {
            log.error("=== Exception occurred during daily tip generation ===", e);
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Exception message: {}", e.getMessage());

            // Return a safe fallback fact
            DailyTipDto fallback = new DailyTipDto();
            fallback.setFact("Did you know that honey never spoils? Archaeologists have found pots of honey in ancient Egyptian tombs that are over 3,000 years old and still perfectly edible!");
            fallback.setCategory("science");
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
            return "Generate a fun, safe, and educational fact for a child.";
        }
    }

    private boolean validateSafety(String text, AgeGroup ageGroup) {
        log.info("=== Starting safety validation ===");
        log.info("Text to validate: '{}'", text);
        log.info("Age group: {}", ageGroup);

        try {
            // Create age-specific moderation prompt
            String moderationPrompt = String.format(
                    "Check if this content is appropriate for children aged %d-%d years old. " +
                            "Consider age-appropriate language, complexity, and topics. " +
                            "Content: %s",
                    ageGroup.getMinAge(), ageGroup.getMaxAge(), text
            );

            log.info("Moderation prompt: '{}'", moderationPrompt);
            log.info("Calling moderation service...");

            ModerationResponse response = moderationClient.call(new ModerationPrompt(moderationPrompt));
            log.info("Moderation response received: {}", response != null ? "NOT_NULL" : "NULL");

            if (response != null) {
                log.info("Moderation result: {}", response.getResult());
                if (response.getResult() != null) {
                    log.info("Moderation output: {}", response.getResult().getOutput());
                    if (response.getResult().getOutput() != null) {
                        log.info("Moderation results count: {}", response.getResult().getOutput().getResults().size());
                        response.getResult().getOutput().getResults().forEach(result -> {
                            log.info("Moderation result - flagged: {}, categories: {}", result.isFlagged(), result.getCategories());
                        });
                    }
                }
            }

            boolean isSafe = response.getResult().getOutput().getResults().stream()
                    .noneMatch(ModerationResult::isFlagged);
            log.info("Final safety validation result: {}", isSafe);
            log.info("=== Safety validation completed ===");
            return isSafe;
        } catch (Exception ex) {
            log.error("=== Moderation service call failed ===", ex);
            log.error("Exception type: {}", ex.getClass().getSimpleName());
            log.error("Exception message: {}", ex.getMessage());
            throw new ModerationServiceException("Moderation service unavailable", ex);
        }
    }
} 