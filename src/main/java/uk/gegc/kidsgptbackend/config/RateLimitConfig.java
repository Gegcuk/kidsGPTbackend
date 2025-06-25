package uk.gegc.kidsgptbackend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Value("${app.rate-limit.auth.requests-per-minute:5}")
    private int authRequestsPerMinute;

    @Value("${app.rate-limit.chat.requests-per-minute:30}")
    private int chatRequestsPerMinute;

    @Value("${app.rate-limit.general.requests-per-minute:100}")
    private int generalRequestsPerMinute;

    @Value("${app.rate-limit.register.requests-per-hour:3}")
    private int registerRequestsPerHour;

    @Bean
    public Bucket authRateLimitBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(authRequestsPerMinute)
                        .refillGreedy(authRequestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    @Bean
    public Bucket chatRateLimitBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(chatRequestsPerMinute)
                        .refillGreedy(chatRequestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    @Bean
    public Bucket generalRateLimitBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(generalRequestsPerMinute)
                        .refillGreedy(generalRequestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    @Bean
    public Bucket registerRateLimitBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(registerRequestsPerHour)
                        .refillGreedy(registerRequestsPerHour, Duration.ofHours(1))
                        .build())
                .build();
    }
} 