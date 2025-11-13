package uk.gegc.kidsgptbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.ai.openai.api.OpenAiModerationApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Custom OpenAI Moderation configuration.
 * Creates a ModerationModel bean with omni-moderation-latest model.
 * Uses @Primary to override Spring AI's auto-configuration.
 */
@Configuration
@Slf4j
public class OpenAiModerationConfig {

    @Bean
    @Primary
    public ModerationModel moderationModel(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.moderation.options.model:omni-moderation-latest}") String model) {
        
        log.info("🔧 Creating custom ModerationModel bean with model: {}", model);
        
        OpenAiModerationApi moderationApi = OpenAiModerationApi.builder()
                .apiKey(apiKey)
                .build();
        
        OpenAiModerationOptions options = OpenAiModerationOptions.builder()
                .model(model)
                .build();
        
        ModerationModel moderationModel = new OpenAiModerationModel(moderationApi)
                .withDefaultOptions(options);
        
        log.info("✅ ModerationModel created successfully with model: {}", model);
        
        return moderationModel;
    }
}

