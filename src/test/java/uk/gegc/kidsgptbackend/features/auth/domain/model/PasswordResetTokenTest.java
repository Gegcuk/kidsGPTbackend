package uk.gegc.kidsgptbackend.features.auth.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gegc.kidsgptbackend.features.auth.domain.repository.PasswordResetTokenRepository;
import uk.gegc.kidsgptbackend.test.BaseRepositoryTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordResetToken Entity Tests")
class PasswordResetTokenTest extends BaseRepositoryTest {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    private UUID testUserId;
    private String testEmail;

    @BeforeEach
    void initTokenTestData() {
        testUserId = UUID.randomUUID();
        testEmail = "test@example.com";
    }

    @Test
    @DisplayName("@PrePersist: Should auto-populate createdAt when null")
    void prePersist_shouldAutoPopulateCreatedAtWhenNull() {
        // Given - Create token with null createdAt
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("test-token-123");
        token.setUserId(testUserId);
        token.setEmail(testEmail);
        token.setCreatedAt(null); // Explicitly null
        token.setExpiresAt(null); // Will also be set by @PrePersist
        token.setUsed(false);

        LocalDateTime beforeSave = LocalDateTime.now();

        // When - Save the entity
        PasswordResetToken saved = persistFlushAndClear(token);

        // Then - Verify createdAt was auto-populated
        PasswordResetToken persisted = find(PasswordResetToken.class, saved.getId());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getCreatedAt()).isAfter(beforeSave.minusSeconds(1));
        assertThat(persisted.getCreatedAt()).isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("@PrePersist: Should auto-populate expiresAt when null")
    void prePersist_shouldAutoPopulateExpiresAtWhenNull() {
        // Given - Create token with null expiresAt
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("test-token-456");
        token.setUserId(testUserId);
        token.setEmail(testEmail);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(null); // Explicitly null
        token.setUsed(false);

        LocalDateTime beforeSave = LocalDateTime.now();

        // When - Save the entity
        PasswordResetToken saved = persistFlushAndClear(token);

        // Then - Verify expiresAt was auto-populated (1 hour from now)
        PasswordResetToken persisted = find(PasswordResetToken.class, saved.getId());
        assertThat(persisted.getExpiresAt()).isNotNull();
        assertThat(persisted.getExpiresAt()).isAfter(beforeSave.plusHours(1).minusSeconds(5));
        assertThat(persisted.getExpiresAt()).isBefore(LocalDateTime.now().plusHours(1).plusSeconds(5));
    }

    @Test
    @DisplayName("@CreatedDate: Should auto-populate createdAt via auditing")
    void createdDate_shouldAutoPopulateCreatedAt() {
        // Given - Create token without setting createdAt (will be set by @CreatedDate)
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("test-token-789");
        token.setUserId(testUserId);
        token.setEmail(testEmail);
        token.setCreatedAt(null); // Will be auto-populated by @CreatedDate
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        token.setUsed(false);

        LocalDateTime beforeSave = LocalDateTime.now();

        // When - Save using repository to trigger auditing
        PasswordResetToken saved = tokenRepository.save(token);
        flush();
        clear();

        // Then - Verify createdAt was auto-populated by @CreatedDate
        PasswordResetToken persisted = find(PasswordResetToken.class, saved.getId());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getCreatedAt()).isAfter(beforeSave.minusSeconds(1));
        assertThat(persisted.getCreatedAt()).isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("@PrePersist: Should not override existing expiresAt")
    void prePersist_shouldNotOverrideExistingExpiresAt() {
        // Given - Create token with specific expiresAt
        LocalDateTime specificExpiresAt = LocalDateTime.of(2024, 12, 31, 23, 59, 59);
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("test-token-abc");
        token.setUserId(testUserId);
        token.setEmail(testEmail);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(specificExpiresAt); // Already set
        token.setUsed(false);

        // When - Save the entity
        PasswordResetToken saved = persistFlushAndClear(token);

        // Then - Verify expiresAt was not overridden
        PasswordResetToken persisted = find(PasswordResetToken.class, saved.getId());
        assertThat(persisted.getExpiresAt()).isNotNull();
        // Verify it matches the specific date/time we set
        assertThat(persisted.getExpiresAt()).isEqualTo(specificExpiresAt);
    }

    @Test
    @DisplayName("@PrePersist: Should set both createdAt and expiresAt when both are null")
    void prePersist_shouldSetBothWhenBothAreNull() {
        // Given - Create token with both null
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("test-token-xyz");
        token.setUserId(testUserId);
        token.setEmail(testEmail);
        token.setCreatedAt(null);
        token.setExpiresAt(null);
        token.setUsed(false);

        // When - Save the entity
        PasswordResetToken saved = persistFlushAndClear(token);

        // Then - Verify both were auto-populated
        PasswordResetToken persisted = find(PasswordResetToken.class, saved.getId());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getExpiresAt()).isNotNull();
        assertThat(persisted.getExpiresAt()).isAfter(persisted.getCreatedAt());
        
        // Verify expiresAt is approximately 1 hour after createdAt
        long hoursDifference = java.time.Duration.between(persisted.getCreatedAt(), persisted.getExpiresAt()).toHours();
        assertThat(hoursDifference).isEqualTo(1L);
    }

    @Test
    @DisplayName("Entity: Should persist all fields correctly")
    void entity_shouldPersistAllFields() {
        // Given - Don't set ID, let JPA generate it
        String token = "test-token-persist";
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        LocalDateTime expiresAt = createdAt.plusHours(1);
        LocalDateTime usedAt = createdAt.plusMinutes(30);

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setToken(token);
        passwordResetToken.setUserId(testUserId);
        passwordResetToken.setEmail(testEmail);
        passwordResetToken.setCreatedAt(createdAt);
        passwordResetToken.setExpiresAt(expiresAt);
        passwordResetToken.setUsed(true);
        passwordResetToken.setUsedAt(usedAt);

        // When - Use repository save to ensure proper lifecycle
        PasswordResetToken saved = tokenRepository.save(passwordResetToken);
        flush();
        clear();

        // Then
        PasswordResetToken persisted = find(PasswordResetToken.class, saved.getId());
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getToken()).isEqualTo(token);
        assertThat(persisted.getUserId()).isEqualTo(testUserId);
        assertThat(persisted.getEmail()).isEqualTo(testEmail);
        // Compare with truncated values to avoid nanosecond precision issues
        assertThat(persisted.getCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                .isEqualTo(createdAt);
        assertThat(persisted.getExpiresAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                .isEqualTo(expiresAt);
        assertThat(persisted.isUsed()).isTrue();
        assertThat(persisted.getUsedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                .isEqualTo(usedAt);
    }
}

