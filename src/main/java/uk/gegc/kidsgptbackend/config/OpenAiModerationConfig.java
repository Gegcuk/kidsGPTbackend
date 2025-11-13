package uk.gegc.kidsgptbackend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures Spring AI's auto-configured {@link OpenAiModerationModel} always uses
 * the OpenAI-supported moderation model (currently omni-moderation-latest).
 *
 * This avoids 400 errors that occur when the upstream default
 * (text-moderation-latest) is requested.
 */
@Configuration
@ConditionalOnBean(ModerationModel.class)
@RequiredArgsConstructor
@Slf4j
public class OpenAiModerationConfig {

    private final ModerationModel moderationModel;

    @Value("${spring.ai.openai.moderation.options.model:omni-moderation-latest}")
    private String configuredModel;

    @PostConstruct
    void enforceSupportedModel() {
        if (moderationModel instanceof OpenAiModerationModel openAiModerationModel) {
            openAiModerationModel.withDefaultOptions(OpenAiModerationOptions.builder()
                    .model(configuredModel)
                    .build());
            log.info("Configured OpenAI moderation model to '{}'.", configuredModel);
        } else {
            log.warn("ModerationModel bean is {}, not OpenAiModerationModel – skipping OpenAI-specific configuration.",
                    moderationModel.getClass().getName());
        }
    }
}

