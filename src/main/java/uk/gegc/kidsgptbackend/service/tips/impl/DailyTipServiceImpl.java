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

    @Override
    public DailyTipDto getDailyTip() {
        return getDailyTip(AgeGroup.AGE_9_10); // Default age group
    }

    @Override
    public DailyTipDto getDailyTip(AgeGroup ageGroup) {
        String prompt = getPromptForAgeGroup(ageGroup);
        
        try {
            ChatResponse response = chatClient.prompt()
                    .system(prompt)
                    .user("Give me a fun fact!")
                    .call()
                    .chatResponse();

            String fact = Optional.ofNullable(response)
                    .map(ChatResponse::getResult)
                    .map(Generation::getOutput)
                    .map(AbstractMessage::getText)
                    .orElse("Did you know that honey never spoils? Archaeologists have found pots of honey in ancient Egyptian tombs that are over 3,000 years old and still perfectly edible!");

            // Validate the generated fact is appropriate for the age group
            if (!validateSafety(fact, ageGroup)) {
                log.warn("Generated fact failed moderation for age group {}, using fallback", ageGroup);
                fact = "Did you know that honey never spoils? Archaeologists have found pots of honey in ancient Egyptian tombs that are over 3,000 years old and still perfectly edible!";
            }

            DailyTipDto tip = new DailyTipDto();
            tip.setFact(fact);
            tip.setCategory(CATEGORIES.get(random.nextInt(CATEGORIES.size())));
            tip.setAgeGroup(ageGroup.name());
            // imageUrl can be null for now, can be added later with image generation

            return tip;

        } catch (Exception e) {
            log.error("Error generating daily tip", e);
            // Return a safe fallback fact
            DailyTipDto fallback = new DailyTipDto();
            fallback.setFact("Did you know that honey never spoils? Archaeologists have found pots of honey in ancient Egyptian tombs that are over 3,000 years old and still perfectly edible!");
            fallback.setCategory("science");
            fallback.setAgeGroup(ageGroup.name());
            return fallback;
        }
    }

    private String getPromptForAgeGroup(AgeGroup ageGroup) {
        String resourcePath = switch (ageGroup) {
            case AGE_6_8 -> "classpath:prompts/tips/age-6-8.txt";
            case AGE_9_10 -> "classpath:prompts/tips/age-9-10.txt";
            case AGE_11_12 -> "classpath:prompts/tips/age-11-12.txt";
            case AGE_13_14 -> "classpath:prompts/tips/age-13-14.txt";
            case AGE_15_16 -> "classpath:prompts/tips/age-15-16.txt";
        };
        try {
            var resource = resourceLoader.getResource(resourcePath);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Could not load prompt for age group {}. Using fallback.", ageGroup, e);
            return "Generate a fun, safe, and educational fact for a child.";
        }
    }

    private boolean validateSafety(String text, AgeGroup ageGroup) {
        try {
            // Create age-specific moderation prompt
            String moderationPrompt = String.format(
                "Check if this content is appropriate for children aged %d-%d years old. " +
                "Consider age-appropriate language, complexity, and topics. " +
                "Content: %s", 
                ageGroup.getMinAge(), ageGroup.getMaxAge(), text
            );
            
            ModerationResponse response = moderationClient.call(new ModerationPrompt(moderationPrompt));
            return response.getResult().getOutput().getResults().stream()
                    .noneMatch(ModerationResult::isFlagged);
        } catch (Exception ex) {
            log.error("Moderation service call failed", ex);
            throw new ModerationServiceException("Moderation service unavailable", ex);
        }
    }
} 