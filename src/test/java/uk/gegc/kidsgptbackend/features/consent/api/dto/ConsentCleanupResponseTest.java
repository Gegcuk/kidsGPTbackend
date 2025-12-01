package uk.gegc.kidsgptbackend.features.consent.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentCleanupResponse DTO Tests")
class ConsentCleanupResponseTest extends BaseUnitTest {

    @Test
    @DisplayName("should create response with all fields")
    void createResponse_allFields_valid() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        ConsentCleanupResponse response = new ConsentCleanupResponse(
                true,
                timestamp,
                100,
                50,
                10,
                List.of("Error1", "Error2"),
                "Cleanup completed successfully"
        );

        // Then
        assertThat(response.dryRun()).isTrue();
        assertThat(response.cleanupTimestamp()).isEqualTo(timestamp);
        assertThat(response.recordsProcessed()).isEqualTo(100);
        assertThat(response.recordsDeleted()).isEqualTo(50);
        assertThat(response.recordsArchived()).isEqualTo(10);
        assertThat(response.errors()).containsExactly("Error1", "Error2");
        assertThat(response.summary()).isEqualTo("Cleanup completed successfully");
    }

    @Test
    @DisplayName("should create response with null optional fields")
    void createResponse_nullOptionalFields_valid() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        ConsentCleanupResponse response = new ConsentCleanupResponse(
                false,
                timestamp,
                0,
                0,
                0,
                null, // errors is optional
                null  // summary is optional
        );

        // Then
        assertThat(response.dryRun()).isFalse();
        assertThat(response.cleanupTimestamp()).isEqualTo(timestamp);
        assertThat(response.recordsProcessed()).isEqualTo(0);
        assertThat(response.recordsDeleted()).isEqualTo(0);
        assertThat(response.recordsArchived()).isEqualTo(0);
        assertThat(response.errors()).isNull();
        assertThat(response.summary()).isNull();
    }

    @Test
    @DisplayName("should handle dryRun true and false")
    void createResponse_dryRunValues_valid() {
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Test with true
        ConsentCleanupResponse response1 = new ConsentCleanupResponse(
                true,
                timestamp,
                100,
                50,
                0,
                null,
                null
        );
        assertThat(response1.dryRun()).isTrue();

        // Test with false
        ConsentCleanupResponse response2 = new ConsentCleanupResponse(
                false,
                timestamp,
                100,
                50,
                0,
                null,
                null
        );
        assertThat(response2.dryRun()).isFalse();
    }

    @Test
    @DisplayName("should handle empty errors list")
    void createResponse_emptyErrorsList_valid() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        ConsentCleanupResponse response = new ConsentCleanupResponse(
                false,
                timestamp,
                100,
                50,
                0,
                List.of(),
                "Summary"
        );

        // Then
        assertThat(response.errors()).isEmpty();
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        ConsentCleanupResponse response1 = new ConsentCleanupResponse(
                true,
                timestamp,
                100,
                50,
                10,
                List.of("Error1"),
                "Summary"
        );

        ConsentCleanupResponse response2 = new ConsentCleanupResponse(
                true,
                timestamp,
                100,
                50,
                10,
                List.of("Error1"),
                "Summary"
        );

        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        ConsentCleanupResponse response = new ConsentCleanupResponse(
                true,
                timestamp,
                100,
                50,
                10,
                List.of("Error1"),
                "Summary"
        );

        // When
        String toString = response.toString();

        // Then
        assertThat(toString).contains("ConsentCleanupResponse");
        assertThat(toString).contains("dryRun=true");
        assertThat(toString).contains("100");
        assertThat(toString).contains("50");
    }
}

