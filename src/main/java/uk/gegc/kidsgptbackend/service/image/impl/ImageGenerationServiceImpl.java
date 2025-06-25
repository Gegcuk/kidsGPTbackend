package uk.gegc.kidsgptbackend.service.image.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        // Validate content safety
        if (!validateSafety(request.description())) {
            throw new IllegalArgumentException("Image description flagged as unsafe");
        }

        // Create age-appropriate prompt
        String ageAppropriatePrompt = createAgeAppropriatePrompt(request, user);
        
        // Validate the enhanced prompt as well
        if (!validateSafety(ageAppropriatePrompt)) {
            throw new IllegalArgumentException("Generated prompt flagged as unsafe");
        }

        log.info("Generating image for user age {} with prompt: {}", user.getAge(), ageAppropriatePrompt);

        // Configure image generation options based on age
        OpenAiImageOptions options = OpenAiImageOptions.builder()
                .withModel("dall-e-3")
                .withQuality("standard") // Use standard quality for faster generation
                .withWidth(1024)
                .withHeight(1024)
                .withStyle("natural") // Natural style is generally safer for children
                .build();

        try {
            ImageResponse response = imageModel.call(
                    new ImagePrompt(ageAppropriatePrompt, options)
            );

            Image image = response.getResult().getOutput();
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            
            AgeGroup ageGroup = user.getAge() != null ? AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10;

            return new ImageGenerationResponse(
                    image.getUrl(),
                    ageAppropriatePrompt, // Use our enhanced prompt as the revised prompt
                    "dall-e-3",
                    latencyMs,
                    ageGroup.name()
            );
        } catch (Exception e) {
            log.error("Error generating image for user {}: {}", user.getUsername(), e.getMessage());
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

    private boolean validateSafety(String content) {
        try {
            ModerationResponse moderationResponse = moderationModel.call(new ModerationPrompt(content));
            boolean isSafe = moderationResponse.getResult().getOutput().getResults().stream()
                    .noneMatch(ModerationResult::isFlagged);
            
            if (!isSafe) {
                log.warn("Content flagged by moderation service: {}", content);
                moderationResponse.getResult().getOutput().getResults().stream()
                        .filter(ModerationResult::isFlagged)
                        .forEach(r -> log.warn("Moderation violation: {}", r.getCategories()));
            }
            
            return isSafe;
        } catch (Exception e) {
            log.error("Error calling moderation service", e);
            throw new ModerationServiceException("Failed to moderate content", e);
        }
    }
} 