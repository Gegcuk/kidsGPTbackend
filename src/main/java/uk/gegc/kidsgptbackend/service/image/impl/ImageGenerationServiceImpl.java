package uk.gegc.kidsgptbackend.service.image.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.image.ImageGenerationRequest;
import uk.gegc.kidsgptbackend.dto.image.ImageGenerationResponse;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.service.image.ImageGenerationService;
import uk.gegc.kidsgptbackend.shared.util.ModerationUtil;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ImageGenerationServiceImpl implements ImageGenerationService {

    private final ImageModel imageModel;
    private final ModerationUtil moderationUtil;
    private final UserRepository userRepository;
    private final Clock clock;

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
        Instant start = Instant.now(clock);
        
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        log.info("Image generation request from user: {} (age: {}), original description: '{}'", 
                user.getUsername(), user.getAge(), request.description());

        // Comprehensive validation using both basic and AI-based moderation
        if (!moderationUtil.validateComprehensive(request.description(), user, "image prompt")) {
            log.warn("User prompt failed comprehensive validation: '{}'", request.description());
            throw new IllegalArgumentException("Image description flagged as unsafe");
        }

        // Create age-appropriate prompt
        String ageAppropriatePrompt = createAgeAppropriatePrompt(request, user);
        
        log.info("Enhanced age-appropriate prompt for user {} ({}): '{}'", 
                user.getUsername(), AgeGroup.fromAge(user.getAge() != null ? user.getAge() : 9), 
                ageAppropriatePrompt);
        
        // Validate the enhanced prompt as well
        if (!moderationUtil.validateSafety(ageAppropriatePrompt)) {
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
            long latencyMs = Duration.between(start, Instant.now(clock)).toMillis();
            
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

    // Removed - now using ModerationUtil for all validation logic
} 