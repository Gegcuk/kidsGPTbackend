package uk.gegc.kidsgptbackend.shared.config;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiImageConfig {

    @Bean
    public ImageModel imageModel(@Value("${spring.ai.openai.api-key}") String apiKey) {
        return new OpenAiImageModel(OpenAiImageApi.builder()
                .apiKey(apiKey)
                .build());
    }
} 