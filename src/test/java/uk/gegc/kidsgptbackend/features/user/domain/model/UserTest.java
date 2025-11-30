package uk.gegc.kidsgptbackend.features.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User Entity Tests")
class UserTest extends BaseUnitTest {

    @Test
    @DisplayName("preUpdate should set updatedAt when user is not deleted")
    void preUpdate_WhenNotDeleted_SetsUpdatedAt() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setHashedPassword("hashed");
        user.setActive(true);
        user.setDeleted(false);
        user.setCreatedAt(Instant.now().minusSeconds(100));
        Instant beforeUpdate = Instant.now();

        // When
        user.preUpdate();

        // Then
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
        assertThat(user.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("preUpdate should set deletedAt when user is deleted")
    void preUpdate_WhenDeleted_SetsDeletedAt() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setHashedPassword("hashed");
        user.setActive(true);
        user.setDeleted(true); // User is deleted
        user.setCreatedAt(Instant.now().minusSeconds(100));
        Instant beforeUpdate = Instant.now();

        // When
        user.preUpdate();

        // Then
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getDeletedAt()).isAfterOrEqualTo(beforeUpdate);
        // updatedAt should not be set when user is deleted
        assertThat(user.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("prePersist should set createdAt and isNew flag")
    void prePersist_SetsCreatedAtAndIsNew() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setHashedPassword("hashed");
        Instant beforePersist = Instant.now();

        // When
        user.prePersist();

        // Then
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isAfterOrEqualTo(beforePersist);
        assertThat(user.isNew()).isTrue();
    }

    @Test
    @DisplayName("markNotNew should set isNew to false")
    void markNotNew_SetsIsNewToFalse() {
        // Given
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setHashedPassword("hashed");
        user.setNew(true);

        // When
        user.markNotNew();

        // Then
        assertThat(user.isNew()).isFalse();
    }
}

