package uk.gegc.kidsgptbackend.service.image.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.image.ImageGenerationRequest;
import uk.gegc.kidsgptbackend.dto.image.ImageGenerationResponse;
import uk.gegc.kidsgptbackend.exception.ModerationServiceException;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.image.ImageGenerationService;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ImageGenerationServiceImpl implements ImageGenerationService {

    private final ImageModel imageModel;
    private final ModerationModel moderationModel;
    private final ChatClient chatClient;
    private final UserRepository userRepository;

    // Age-appropriate prompt modifiers
    private static final Map<AgeGroup, String> AGE_PROMPT_MODIFIERS = Map.of(
            AgeGroup.AGE_6_8, "Create a colorful, friendly, cartoon-style image suitable for young children: ",
            AgeGroup.AGE_9_10, "Create a fun, engaging, child-friendly image with bright colors: ",
            AgeGroup.AGE_11_12, "Create an interesting, age-appropriate image with good detail: ",
            AgeGroup.AGE_13_14, "Create a detailed, engaging image suitable for teens: ",
            AgeGroup.AGE_15_16, "Create a sophisticated, detailed image suitable for teenagers: "
    );

    // Age-appropriate style modifiers
    private static final Map<AgeGroup, String> AGE_STYLE_MODIFIERS = Map.of(
            AgeGroup.AGE_6_8, "simple, bright colors, cartoon style, no scary elements",
            AgeGroup.AGE_9_10, "colorful, friendly, slightly more detailed, cartoon or semi-realistic",
            AgeGroup.AGE_11_12, "detailed, realistic or stylized, appropriate complexity",
            AgeGroup.AGE_13_14, "realistic, detailed, modern style",
            AgeGroup.AGE_15_16, "high detail, realistic or artistic style, sophisticated"
    );

    @Override
    public ImageGenerationResponse generateImage(ImageGenerationRequest request, Principal principal) {
        Instant start = Instant.now();
        
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        log.info("Image generation request from user: {} (age: {}), original description: '{}'", 
                user.getUsername(), user.getAge(), request.description());

        // Validate prompt before processing using OpenAI
        validatePromptWithAI(request.description(), user);

        // Validate content safety
        if (!validateSafety(request.description())) {
            log.warn("User prompt flagged as unsafe by moderation API: '{}'", request.description());
            throw new IllegalArgumentException("Image description flagged as unsafe");
        }

        // Create age-appropriate prompt
        String ageAppropriatePrompt = createAgeAppropriatePrompt(request, user);
        
        log.info("Enhanced age-appropriate prompt for user {} ({}): '{}'", 
                user.getUsername(), AgeGroup.fromAge(user.getAge() != null ? user.getAge() : 9), 
                ageAppropriatePrompt);
        
        // Validate the enhanced prompt as well
        if (!validateSafety(ageAppropriatePrompt)) {
            log.warn("Enhanced prompt flagged as unsafe by moderation API: '{}'", ageAppropriatePrompt);
            throw new IllegalArgumentException("Generated prompt flagged as unsafe");
        }

        log.info("Sending request to OpenAI DALL-E API for user: {}", user.getUsername());

        // Configure image generation options based on age
        OpenAiImageOptions options = OpenAiImageOptions.builder()
                .withModel("dall-e-3")
                .withQuality("standard") // Use standard quality for faster generation
                .withWidth(1024)
                .withHeight(1024)
                .withStyle("natural") // Natural style is generally safer for children
                .build();

                try {
            log.debug("OpenAI API request details - Model: dall-e-3, Size: 1024x1024, Quality: standard, Style: natural");
            
            ImageResponse response = imageModel.call(
                    new ImagePrompt(ageAppropriatePrompt, options)
            );

            Image image = response.getResult().getOutput();
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            
            AgeGroup ageGroup = user.getAge() != null ? AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10;

            log.info("Image generation successful for user: {} - URL: {}, Latency: {}ms", 
                    user.getUsername(), image.getUrl(), latencyMs);
            
            log.debug("OpenAI API response details - Image URL: {}, Generation time: {}ms", 
                    image.getUrl(), latencyMs);

            return new ImageGenerationResponse(
                    image.getUrl(),
                    ageAppropriatePrompt, // Use our enhanced prompt as the revised prompt
                    "dall-e-3",
                    latencyMs,
                    ageGroup.name()
            );
        } catch (Exception e) {
            log.error("OpenAI API error for user {}: {} - Prompt was: '{}'", 
                    user.getUsername(), e.getMessage(), ageAppropriatePrompt, e);
            throw new RuntimeException("Failed to generate image", e);
        }
    }

    private String createAgeAppropriatePrompt(ImageGenerationRequest request, User user) {
        AgeGroup ageGroup = user.getAge() != null ? AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10;
        
        StringBuilder promptBuilder = new StringBuilder();
        
        // Add age-appropriate prefix
        promptBuilder.append(AGE_PROMPT_MODIFIERS.get(ageGroup));
        
        // Add user's description
        promptBuilder.append(request.description());
        
        // Add style if provided
        if (request.style() != null && !request.style().trim().isEmpty()) {
            promptBuilder.append(" in ").append(request.style()).append(" style");
        }
        
        // Add age-appropriate style modifier
        promptBuilder.append(". ").append(AGE_STYLE_MODIFIERS.get(ageGroup));
        
        // Add safety constraints for younger children
        if (ageGroup == AgeGroup.AGE_6_8 || ageGroup == AgeGroup.AGE_9_10) {
            promptBuilder.append(". Ensure the image is completely safe and appropriate for young children with no scary, violent, or inappropriate content.");
        }

        return promptBuilder.toString();
    }

    private void validatePromptWithAI(String prompt, User user) {
        // Basic validation first
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
        
        if (prompt.length() > 1000) {
            throw new IllegalArgumentException("Prompt too long (max 1000 characters)");
        }
        
        if (prompt.length() < 3) {
            throw new IllegalArgumentException("Prompt too short (min 3 characters)");
        }

        log.debug("Using OpenAI to validate image prompt: '{}'", prompt);

        // Use OpenAI to intelligently validate the prompt
        AgeGroup ageGroup = user.getAge() != null ? AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10;
        
        String validationSystemPrompt = createValidationSystemPrompt(ageGroup);
        String validationUserPrompt = String.format(
            "Please analyze this image generation request: \"%s\"\n\n" +
            "Respond with only 'SAFE' if appropriate for the age group, or 'UNSAFE: reason' if not appropriate.", 
            prompt
        );

        try {
            log.debug("Sending prompt validation request to OpenAI for user age group: {}", ageGroup);
            
            String validationResponse = chatClient.prompt()
                    .system(validationSystemPrompt)
                    .user(validationUserPrompt)
                    .call()
                    .content();

            log.debug("OpenAI validation response: '{}'", validationResponse);

            if (validationResponse == null || !validationResponse.trim().toUpperCase().startsWith("SAFE")) {
                log.warn("OpenAI flagged image prompt as inappropriate for age group {}: '{}' - Response: '{}'", 
                        ageGroup, prompt, validationResponse);
                
                String reason = validationResponse != null && validationResponse.contains(":") 
                    ? validationResponse.substring(validationResponse.indexOf(":") + 1).trim()
                    : "inappropriate content detected";
                    
                throw new IllegalArgumentException("Image prompt inappropriate for age group: " + reason);
            }

            log.debug("OpenAI validation passed for prompt: '{}'", prompt);
            
        } catch (IllegalArgumentException e) {
            // Re-throw validation failures
            throw e;
        } catch (Exception e) {
            log.error("Error during OpenAI prompt validation for: '{}' - {}", prompt, e.getMessage());
            // Fall back to basic validation on AI service failure
            log.warn("Falling back to basic validation due to AI service error");
        }
    }

    private String createValidationSystemPrompt(AgeGroup ageGroup) {
        return String.format("""
            You are a content safety validator for an AI image generation service for children.
            
            Your task: Determine if an image generation prompt is appropriate for %s year old children.
            
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
            - 'SAFE' if the prompt is completely appropriate
            - 'UNSAFE: [specific reason]' if inappropriate
            
            Be strict in your evaluation - when in doubt, mark as UNSAFE.
            """, 
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
                - Very simple, colorful, cartoonish content only
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
                - More sophisticated art and design concepts
                - Realistic depictions of positive life experiences
                - Educational and inspirational content
                """;
            case AGE_15_16 -> """
                - Teen-appropriate themes and interests
                - Artistic and creative expression
                - Future aspirations and career themes
                - Social and environmental awareness themes
                """;
        };
    }

    private boolean validateSafety(String content) {
        log.debug("Calling OpenAI moderation API for content: '{}'", content);
        
        try {
            ModerationResponse moderationResponse = moderationModel.call(new ModerationPrompt(content));
            boolean isSafe = moderationResponse.getResult().getOutput().getResults().stream()
                    .noneMatch(ModerationResult::isFlagged);
            
            if (!isSafe) {
                log.warn("Content flagged by OpenAI moderation API: '{}'", content);
                moderationResponse.getResult().getOutput().getResults().stream()
                        .filter(ModerationResult::isFlagged)
                        .forEach(r -> log.warn("Moderation violation categories: {}", r.getCategories()));
            } else {
                log.debug("Content passed OpenAI moderation check: '{}'", content);
            }
            
            return isSafe;
        } catch (Exception e) {
            log.error("Error calling OpenAI moderation API for content: '{}'", content, e);
            throw new ModerationServiceException("Failed to moderate content", e);
        }
    }
} 