package uk.gegc.kidsgptbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.repository.auth.PasswordResetTokenRepository;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ScheduledTasksConfig {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Clock clock;

    /**
     * Clean up expired and used password reset tokens every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            passwordResetTokenRepository.deleteExpiredAndUsedTokens(now);
            log.debug("Cleaned up expired/used password reset tokens");
        } catch (Exception e) {
            log.error("Error cleaning up expired tokens", e);
        }
    }
} 