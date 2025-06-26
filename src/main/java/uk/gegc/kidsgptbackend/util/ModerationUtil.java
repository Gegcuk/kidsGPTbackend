package uk.gegc.kidsgptbackend.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.exception.ModerationServiceException;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.model.user.User;

/**
 * Centralized utility for content moderation and validation.
 * Provides consistent moderation logic across all services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationUtil {

    private final ModerationModel moderationModel;
    private final ChatClient chatClient;

    /**
     * Basic content safety validation using OpenAI moderation API.
     * 
     * @param content The content to validate
     * @return true if content is safe, false otherwise
     * @throws ModerationServiceException if moderation service is unavailable
     */
    public boolean validateSafety(String content) {
        log.debug("Validating content safety: '{}'", content);
        
        try {
            ModerationResponse response = moderationModel.call(new ModerationPrompt(content));
            boolean isSafe = response.getResult().getOutput().getResults().stream()
                    .noneMatch(ModerationResult::isFlagged);
            
            if (!isSafe) {
                log.warn("Content flagged by moderation API: '{}'", content);
                response.getResult().getOutput().getResults().stream()
                        .filter(ModerationResult::isFlagged)
                        .forEach(r -> log.warn("Moderation violation categories: {}", r.getCategories()));
            } else {
                log.debug("Content passed moderation check: '{}'", content);
            }
            
            return isSafe;
        } catch (Exception e) {
            log.error("Error calling moderation API for content: '{}'", content, e);
            throw new ModerationServiceException("Moderation service unavailable", e);
        }
    }

    /**
     * Age-aware content safety validation using OpenAI moderation API.
     * Creates age-specific moderation prompts for better context.
     * 
     * @param content The content to validate
     * @param ageGroup The target age group
     * @return true if content is appropriate for the age group, false otherwise
     * @throws ModerationServiceException if moderation service is unavailable
     */
    public boolean validateSafetyForAge(String content, AgeGroup ageGroup) {
        log.debug("Validating content safety for age group {}: '{}'", ageGroup, content);
        
        try {
            // Create age-specific moderation prompt
            String moderationPrompt = String.format(
                    "Check if this content is appropriate for children aged %d-%d years old. " +
                            "Consider age-appropriate language, complexity, and topics. " +
                            "Content: %s",
                    ageGroup.getMinAge(), ageGroup.getMaxAge(), content
            );

            log.debug("Age-aware moderation prompt: '{}'", moderationPrompt);
            
            ModerationResponse response = moderationModel.call(new ModerationPrompt(moderationPrompt));
            boolean isSafe = response.getResult().getOutput().getResults().stream()
                    .noneMatch(ModerationResult::isFlagged);
            
            if (!isSafe) {
                log.warn("Content flagged for age group {}: '{}'", ageGroup, content);
                response.getResult().getOutput().getResults().stream()
                        .filter(ModerationResult::isFlagged)
                        .forEach(r -> log.warn("Age-aware moderation violation categories: {}", r.getCategories()));
            } else {
                log.debug("Content passed age-aware moderation check for {}: '{}'", ageGroup, content);
            }
            
            return isSafe;
        } catch (Exception e) {
            log.error("Error calling age-aware moderation API for content: '{}'", content, e);
            throw new ModerationServiceException("Moderation service unavailable", e);
        }
    }

    /**
     * Advanced AI-based content validation with age awareness.
     * Uses ChatClient for intelligent content analysis.
     * 
     * @param content The content to validate
     * @param user The user context for age-appropriate validation
     * @throws IllegalArgumentException if content is inappropriate
     */
    public void validateContentWithAI(String content, User user) {
        validateContentWithAI(content, user, "general content");
    }

    /**
     * Advanced AI-based content validation with age awareness and content type specification.
     * Uses ChatClient for intelligent content analysis.
     * 
     * @param content The content to validate
     * @param user The user context for age-appropriate validation
     * @param contentType The type of content (e.g., "image prompt", "chat message", "joke")
     * @throws IllegalArgumentException if content is inappropriate
     */
    public void validateContentWithAI(String content, User user, String contentType) {
        // Basic validation first
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }
        
        if (content.length() > 1000) {
            throw new IllegalArgumentException("Content too long (max 1000 characters)");
        }
        
        if (content.length() < 3) {
            throw new IllegalArgumentException("Content too short (min 3 characters)");
        }

        log.debug("Using AI to validate {}: '{}'", contentType, content);

        AgeGroup ageGroup = user.getAge() != null ? AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10;
        
        String validationSystemPrompt = createValidationSystemPrompt(ageGroup, contentType);
        String validationUserPrompt = String.format(
            "Please analyze this %s: \"%s\"\n\n" +
            "Respond with only 'SAFE' if appropriate for the age group, or 'UNSAFE: reason' if not appropriate.", 
            contentType, content
        );

        try {
            log.debug("Sending {} validation request to AI for age group: {}", contentType, ageGroup);
            
            String validationResponse = chatClient.prompt()
                    .system(validationSystemPrompt)
                    .user(validationUserPrompt)
                    .call()
                    .content();

            log.debug("AI validation response: '{}'", validationResponse);

            if (validationResponse == null || !validationResponse.trim().toUpperCase().startsWith("SAFE")) {
                log.warn("AI flagged {} as inappropriate for age group {}: '{}' - Response: '{}'", 
                        contentType, ageGroup, content, validationResponse);
                
                String reason = validationResponse != null && validationResponse.contains(":") 
                    ? validationResponse.substring(validationResponse.indexOf(":") + 1).trim()
                    : "inappropriate content detected";
                    
                throw new IllegalArgumentException(String.format("%s inappropriate for age group: %s", 
                    capitalizeFirst(contentType), reason));
            }

            log.debug("AI validation passed for {}: '{}'", contentType, content);
            
        } catch (IllegalArgumentException e) {
            // Re-throw validation failures
            throw e;
        } catch (Exception e) {
            log.error("Error during AI {} validation for: '{}' - {}", contentType, content, e.getMessage());
            // Fall back to basic validation on AI service failure
            log.warn("Falling back to basic validation due to AI service error");
        }
    }

    /**
     * Comprehensive validation that combines both OpenAI moderation and AI-based validation.
     * Recommended for critical content validation.
     * 
     * @param content The content to validate
     * @param user The user context
     * @param contentType The type of content
     * @return true if content passes all validations, false otherwise
     */
    public boolean validateComprehensive(String content, User user, String contentType) {
        try {
            // First, run basic moderation
            if (!validateSafety(content)) {
                return false;
            }
            
            // Then run AI-based validation
            validateContentWithAI(content, user, contentType);
            
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Comprehensive validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Creates a validation system prompt for AI-based content analysis.
     */
    private String createValidationSystemPrompt(AgeGroup ageGroup, String contentType) {
        return String.format("""
            You are a content safety validator for a children's AI service.
            
            Your task: Determine if this %s is appropriate for %s year old children.
            
            Guidelines for %s:
            %s
            
            ALWAYS consider:
            - No violence, weapons, or scary content
            - No sexual, inappropriate, or adult themes  
            - No harmful substances (drugs, alcohol, tobacco)
            - No disturbing or frightening imagery
            - Content should be positive, educational, or entertaining
            - Age-appropriate complexity and themes
            
            Respond ONLY with:
            - 'SAFE' if the content is completely appropriate
            - 'UNSAFE: [specific reason]' if inappropriate
            
            Be strict in your evaluation - when in doubt, mark as UNSAFE.
            """, 
            contentType,
            getAgeRangeDescription(ageGroup),
            ageGroup.name(),
            getAgeSpecificGuidelines(ageGroup)
        );
    }

    private String getAgeRangeDescription(AgeGroup ageGroup) {
        return switch (ageGroup) {
            case AGE_6_8 -> "6-8";
            case AGE_9_10 -> "9-10"; 
            case AGE_11_12 -> "11-12";
            case AGE_13_14 -> "13-14";
            case AGE_15_16 -> "15-16";
        };
    }

    private String getAgeSpecificGuidelines(AgeGroup ageGroup) {
        return switch (ageGroup) {
            case AGE_6_8 -> """
                - Very simple, colorful, safe content only
                - Focus on animals, toys, basic shapes, friendly characters
                - Absolutely no complex or potentially confusing themes
                - Everything must be clearly safe and non-threatening
                """;
            case AGE_9_10 -> """
                - Simple adventure themes, basic fantasy (friendly dragons, etc.)
                - Educational content (space, nature, science basics)
                - Popular children's characters and themes
                - Sports and outdoor activities
                """;
            case AGE_11_12 -> """
                - More complex themes but still clearly child-appropriate
                - Historical topics (non-violent periods)
                - Science and technology themes
                - Adventure and exploration themes
                """;
            case AGE_13_14 -> """
                - Age-appropriate pop culture references
                - More sophisticated concepts and discussions
                - Realistic depictions of positive life experiences
                - Educational and inspirational content
                """;
            case AGE_15_16 -> """
                - Teen-appropriate themes and interests
                - Creative and artistic expression
                - Future aspirations and career themes
                - Social and environmental awareness themes
                """;
        };
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
} 