package uk.gegc.kidsgptbackend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorTest {

    @Test
    @DisplayName("generateSecureToken: produces non-null token")
    void generateSecureToken_producesNonNullToken() {
        String token = TokenGenerator.generateSecureToken();
        
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
    }

    @Test
    @DisplayName("generateSecureToken: produces tokens of correct length")
    void generateSecureToken_producesCorrectLength() {
        String token = TokenGenerator.generateSecureToken();
        
        // Base64 URL encoding without padding for 32 bytes = 43 characters
        assertThat(token).hasSize(43);
    }

    @Test
    @DisplayName("generateSecureToken: produces URL-safe tokens")
    void generateSecureToken_producesUrlSafeTokens() {
        String token = TokenGenerator.generateSecureToken();
        
        // Base64 URL encoding should only contain alphanumeric, '-', and '_'
        assertThat(token).matches("^[a-zA-Z0-9_-]+$");
    }

    @Test
    @DisplayName("generateSecureToken: produces different tokens on multiple calls")
    void generateSecureToken_producesDifferentTokens() {
        String token1 = TokenGenerator.generateSecureToken();
        String token2 = TokenGenerator.generateSecureToken();
        String token3 = TokenGenerator.generateSecureToken();
        
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1).isNotEqualTo(token3);
        assertThat(token2).isNotEqualTo(token3);
    }

    @Test
    @DisplayName("generateSecureToken: produces tokens without padding")
    void generateSecureToken_producesTokensWithoutPadding() {
        String token = TokenGenerator.generateSecureToken();
        
        // Should not contain padding characters ('=')
        assertThat(token).doesNotContain("=");
    }

    @Test
    @DisplayName("generateSecureToken: produces tokens with mixed characters")
    void generateSecureToken_producesMixedCharacters() {
        String token = TokenGenerator.generateSecureToken();
        
        boolean hasUpperCase = token.chars().anyMatch(Character::isUpperCase);
        boolean hasLowerCase = token.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = token.chars().anyMatch(Character::isDigit);
        
        assertThat(hasUpperCase).isTrue();
        assertThat(hasLowerCase).isTrue();
        assertThat(hasDigit).isTrue();
    }

    @Test
    @DisplayName("generateSecureToken: multiple calls produce statistically different tokens")
    void generateSecureToken_multipleCalls_produceDifferentTokens() {
        String[] tokens = new String[100];
        
        for (int i = 0; i < 100; i++) {
            tokens[i] = TokenGenerator.generateSecureToken();
        }
        
        // Check that all tokens are unique
        long uniqueTokens = java.util.Arrays.stream(tokens).distinct().count();
        assertThat(uniqueTokens).isEqualTo(100);
    }

    @Test
    @DisplayName("generateSecureToken: produces tokens with consistent length")
    void generateSecureToken_producesConsistentLength() {
        for (int i = 0; i < 50; i++) {
            String token = TokenGenerator.generateSecureToken();
            assertThat(token).hasSize(43);
        }
    }

    @Test
    @DisplayName("generateSecureToken: produces tokens suitable for URL parameters")
    void generateSecureToken_producesUrlSuitableTokens() {
        String token = TokenGenerator.generateSecureToken();
        
        // Should not contain characters that need URL encoding
        assertThat(token).doesNotContain("+");
        assertThat(token).doesNotContain("/");
        assertThat(token).doesNotContain("=");
    }

    @Test
    @DisplayName("generateSecureToken: produces unique tokens")
    void generateSecureToken_producesUniqueTokens() {
        String token1 = TokenGenerator.generateSecureToken();
        String token2 = TokenGenerator.generateSecureToken();
        
        assertThat(token1).isNotEqualTo(token2);
    }
} 