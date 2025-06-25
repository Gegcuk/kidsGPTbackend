package uk.gegc.kidsgptbackend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final JwtTokenProvider jwtTokenProvider;
    
    // Per-user rate limit buckets for authenticated users
    private final ConcurrentMap<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    
    // Global buckets for unauthenticated requests
    private final Bucket authRateLimitBucket;
    private final Bucket chatRateLimitBucket;
    private final Bucket generalRateLimitBucket;
    private final Bucket registerRateLimitBucket;

    @Value("${app.rate-limit.chat.requests-per-minute:30}")
    private int chatRequestsPerMinute;

    public RateLimitResult checkRateLimit(String requestURI, String authHeader) {
        String username = extractUsername(authHeader);
        Bucket bucket = getBucketForRequest(requestURI, username);
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        return RateLimitResult.builder()
                .allowed(probe.isConsumed())
                .remainingTokens(probe.getRemainingTokens())
                .waitForRefillSeconds(probe.getNanosToWaitForRefill() / 1_000_000_000)
                .build();
    }

    private String extractUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "anonymous";
        }
        
        try {
            String token = authHeader.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                return jwtTokenProvider.getUsername(token);
            }
        } catch (Exception e) {
            log.debug("Failed to extract username from token: {}", e.getMessage());
        }
        
        return "anonymous";
    }

    private Bucket getBucketForRequest(String requestURI, String username) {
        if (requestURI.startsWith("/api/v1/auth/register")) {
            return registerRateLimitBucket;
        } else if (requestURI.startsWith("/api/v1/auth/")) {
            return authRateLimitBucket;
        } else if (requestURI.startsWith("/api/v1/chat")) {
            if (!"anonymous".equals(username)) {
                return getUserBucket(username);
            }
            return chatRateLimitBucket;
        } else {
            if (!"anonymous".equals(username)) {
                return getUserBucket(username);
            }
            return generalRateLimitBucket;
        }
    }

    private Bucket getUserBucket(String username) {
        return userBuckets.computeIfAbsent(username, k -> 
            Bucket.builder()
                .addLimit(Bandwidth.builder()
                    .capacity(chatRequestsPerMinute)
                    .refillGreedy(chatRequestsPerMinute, Duration.ofMinutes(1))
                    .build())
                .build()
        );
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final long remainingTokens;
        private final long waitForRefillSeconds;

        private RateLimitResult(Builder builder) {
            this.allowed = builder.allowed;
            this.remainingTokens = builder.remainingTokens;
            this.waitForRefillSeconds = builder.waitForRefillSeconds;
        }

        public boolean isAllowed() { return allowed; }
        public long getRemainingTokens() { return remainingTokens; }
        public long getWaitForRefillSeconds() { return waitForRefillSeconds; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean allowed;
            private long remainingTokens;
            private long waitForRefillSeconds;

            public Builder allowed(boolean allowed) {
                this.allowed = allowed;
                return this;
            }

            public Builder remainingTokens(long remainingTokens) {
                this.remainingTokens = remainingTokens;
                return this;
            }

            public Builder waitForRefillSeconds(long waitForRefillSeconds) {
                this.waitForRefillSeconds = waitForRefillSeconds;
                return this;
            }

            public RateLimitResult build() {
                return new RateLimitResult(this);
            }
        }
    }
} 