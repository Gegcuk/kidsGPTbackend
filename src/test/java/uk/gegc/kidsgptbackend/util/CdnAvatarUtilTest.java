package uk.gegc.kidsgptbackend.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

class CdnAvatarUtilTest {

    @Test
    @DisplayName("Should generate correct CDN URL for valid avatar ID")
    void getAvatarUrl_WithValidAvatarId_ReturnsCorrectUrl() {
        // Given
        String avatarId = "avatar123";

        // When
        String result = CdnAvatarUtil.getAvatarUrl(avatarId);

        // Then
        assertThat(result).isEqualTo("https://cdn.example.com/avatars/avatar123.png");
    }

    @Test
    @DisplayName("Should return null for null avatar ID")
    void getAvatarUrl_WithNullAvatarId_ReturnsNull() {
        // Given
        String avatarId = null;

        // When
        String result = CdnAvatarUtil.getAvatarUrl(avatarId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for empty avatar ID")
    void getAvatarUrl_WithEmptyAvatarId_ReturnsNull() {
        // Given
        String avatarId = "";

        // When
        String result = CdnAvatarUtil.getAvatarUrl(avatarId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for blank avatar ID")
    void getAvatarUrl_WithBlankAvatarId_ReturnsNull() {
        // Given
        String avatarId = "   ";

        // When
        String result = CdnAvatarUtil.getAvatarUrl(avatarId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle special characters in avatar ID")
    void getAvatarUrl_WithSpecialCharacters_ReturnsCorrectUrl() {
        // Given
        String avatarId = "avatar-123_456";

        // When
        String result = CdnAvatarUtil.getAvatarUrl(avatarId);

        // Then
        assertThat(result).isEqualTo("https://cdn.example.com/avatars/avatar-123_456.png");
    }

    @Test
    @DisplayName("Should handle UUID format avatar ID")
    void getAvatarUrl_WithUuid_ReturnsCorrectUrl() {
        // Given
        String avatarId = "550e8400-e29b-41d4-a716-446655440000";

        // When
        String result = CdnAvatarUtil.getAvatarUrl(avatarId);

        // Then
        assertThat(result).isEqualTo("https://cdn.example.com/avatars/550e8400-e29b-41d4-a716-446655440000.png");
    }
} 