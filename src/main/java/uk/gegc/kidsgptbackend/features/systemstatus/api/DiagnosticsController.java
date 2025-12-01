package uk.gegc.kidsgptbackend.features.systemstatus.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Diagnostics controller for checking OpenAI configuration.
 * Helps verify that the correct moderation model is being used.
 */
@RestController
@RequestMapping("/api/diagnostics")
@RequiredArgsConstructor
@Slf4j
public class DiagnosticsController {

    private final ModerationModel moderationModel;
    
    @Value("${spring.ai.openai.moderation.options.model:not-set}")
    private String configuredModel;
    
    @Value("${spring.ai.openai.chat.options.model:not-set}")
    private String chatModel;

    /**
     * Returns information about the OpenAI models configured in the application.
     * Public endpoint for diagnostics.
     */
    @GetMapping("/openai-models")
    public ResponseEntity<Map<String, Object>> getOpenAiModels() {
        log.info("Diagnostics: Checking OpenAI model configuration");
        
        Map<String, Object> response = new HashMap<>();
        
        // Moderation model info
        response.put("moderationModelClass", moderationModel.getClass().getName());
        response.put("moderationModelSimpleName", moderationModel.getClass().getSimpleName());
        response.put("propertyConfigured", configuredModel);
        response.put("isOpenAiModerationModel", moderationModel instanceof OpenAiModerationModel);
        
        // Try to get actual model name from OpenAiModerationModel
        if (moderationModel instanceof OpenAiModerationModel openAiModel) {
            try {
                // The defaultOptions might contain the model set by @PostConstruct
                var defaultOptions = openAiModel.getDefaultOptions();
                if (defaultOptions != null) {
                    response.put("defaultOptionsPresent", true);
                    response.put("defaultOptionsClass", defaultOptions.getClass().getName());
                    response.put("defaultOptionsModel", defaultOptions.getModel());
                    
                    response.put("postConstructLikelyRan", "omni-moderation-latest".equals(defaultOptions.getModel()));
                } else {
                    response.put("defaultOptionsPresent", false);
                    response.put("warning", "PostConstruct withDefaultOptions() might not have worked");
                }
            } catch (Exception e) {
                response.put("defaultOptionsError", e.getMessage());
            }
        }
        
        // Chat model info
        response.put("chatModelConfigured", chatModel);
        
        // Expected values
        response.put("expectedModerationModel", "omni-moderation-latest");
        
        // Determine overall status
        boolean propertySet = "omni-moderation-latest".equals(configuredModel);
        response.put("propertySetCorrectly", propertySet);
        
        String status = propertySet ? "PROPERTY_OK" : "PROPERTY_MISSING_OR_WRONG";
        response.put("status", status);
        response.put("nextStep", "Call /api/diagnostics/test-moderation to test actual API call");
        
        log.info("Diagnostics result: {}", response);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Quick health check for moderation API.
     * Tests if moderation calls work without 400 errors.
     */
    @GetMapping("/test-moderation")
    public ResponseEntity<Map<String, Object>> testModeration() {
        log.info("Diagnostics: Testing moderation API");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Try a simple moderation call
            var telemetryModel = configuredModel;
            if ("not-set".equals(telemetryModel) || telemetryModel == null || telemetryModel.isBlank()) {
                telemetryModel = "omni-moderation-latest";
            }
            var moderationPrompt = new org.springframework.ai.moderation.ModerationPrompt(
                    "test",
                    OpenAiModerationOptions.builder().model(telemetryModel).build()
            );
            var result = moderationModel.call(moderationPrompt);
            
            response.put("status", "SUCCESS");
            response.put("message", "Moderation API call succeeded");
            response.put("hasResult", result != null && result.getResult() != null);
            
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("error", e.getClass().getSimpleName());
            response.put("message", e.getMessage());
            
            // Check if it's the specific 400 error about model name
            if (e.getMessage() != null && e.getMessage().contains("omni-moderation-latest")) {
                response.put("diagnosis", "Wrong moderation model configured - needs omni-moderation-latest");
            } else if (e.getMessage() != null && e.getMessage().contains("400")) {
                response.put("diagnosis", "400 error - likely a model name issue");
            }
        }
        
        return ResponseEntity.ok(response);
    }
}

