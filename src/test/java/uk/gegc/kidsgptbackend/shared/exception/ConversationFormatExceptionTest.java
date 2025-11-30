package uk.gegc.kidsgptbackend.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ConversationFormatException}.
 * <p>
 * Tests exception behavior including:
 * - Message-only constructor
 * - Message and cause constructor
 * - Exception inheritance and behavior
 * - Message and cause propagation
 */
@DisplayName("ConversationFormatException Tests")
class ConversationFormatExceptionTest extends BaseUnitTest {

    @Test
    @DisplayName("Constructor with message: should create exception with message")
    void constructor_messageOnly_createsExceptionWithMessage() {
        // Given
        String message = "Invalid conversation format";

        // When
        ConversationFormatException exception = new ConversationFormatException(message);

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Constructor with message: should handle null message")
    void constructor_nullMessage_createsExceptionWithNullMessage() {
        // When
        ConversationFormatException exception = new ConversationFormatException((String) null);

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Constructor with message: should handle empty message")
    void constructor_emptyMessage_createsExceptionWithEmptyMessage() {
        // Given
        String message = "";

        // When
        ConversationFormatException exception = new ConversationFormatException(message);

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo("");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Constructor with message and cause: should create exception with message and cause")
    void constructor_messageAndCause_createsExceptionWithMessageAndCause() {
        // Given
        String message = "Invalid conversation format";
        Throwable cause = new IllegalArgumentException("Underlying error");

        // When
        ConversationFormatException exception = new ConversationFormatException(message, cause);

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Constructor with message and cause: should handle null message with cause")
    void constructor_nullMessageWithCause_createsExceptionWithNullMessageAndCause() {
        // Given
        Throwable cause = new RuntimeException("Underlying error");

        // When
        ConversationFormatException exception = new ConversationFormatException(null, cause);

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("Constructor with message and cause: should handle message with null cause")
    void constructor_messageWithNullCause_createsExceptionWithMessageAndNullCause() {
        // Given
        String message = "Invalid conversation format";

        // When
        ConversationFormatException exception = new ConversationFormatException(message, null);

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Constructor with message and cause: should handle both null")
    void constructor_bothNull_createsExceptionWithNullMessageAndNullCause() {
        // When
        ConversationFormatException exception = new ConversationFormatException(null, null);

        // Then
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Constructor with message and cause: should preserve cause chain")
    void constructor_messageAndCause_preservesCauseChain() {
        // Given
        String message = "Invalid conversation format";
        Throwable rootCause = new IllegalStateException("Root cause");
        Throwable intermediateCause = new RuntimeException("Intermediate", rootCause);
        Throwable cause = new IllegalArgumentException("Top level", intermediateCause);

        // When
        ConversationFormatException exception = new ConversationFormatException(message, cause);

        // Then
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCause().getCause()).isSameAs(intermediateCause);
        assertThat(exception.getCause().getCause().getCause()).isSameAs(rootCause);
    }

    @Test
    @DisplayName("Exception: should be instance of RuntimeException")
    void exception_shouldBeInstanceOfRuntimeException() {
        // When
        ConversationFormatException exception = new ConversationFormatException("Test");

        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Exception: should be throwable")
    void exception_shouldBeThrowable() {
        // Given
        ConversationFormatException exception = new ConversationFormatException("Test");

        // When/Then
        assertThatThrownBy(() -> {
            throw exception;
        })
                .isInstanceOf(ConversationFormatException.class)
                .hasMessage("Test");
    }

    @Test
    @DisplayName("Exception: should preserve stack trace")
    void exception_shouldPreserveStackTrace() {
        // Given
        String message = "Invalid conversation format";
        Throwable cause = new RuntimeException("Cause");

        // When
        ConversationFormatException exception = new ConversationFormatException(message, cause);

        // Then
        assertThat(exception.getStackTrace()).isNotNull();
        assertThat(exception.getStackTrace().length).isGreaterThan(0);
        assertThat(exception.getCause().getStackTrace()).isNotNull();
    }

    @Test
    @DisplayName("Exception: should have correct toString representation")
    void exception_shouldHaveCorrectToString() {
        // Given
        String message = "Invalid conversation format";
        ConversationFormatException exception = new ConversationFormatException(message);

        // When
        String toString = exception.toString();

        // Then
        assertThat(toString).contains("ConversationFormatException");
        assertThat(toString).contains(message);
    }

    @Test
    @DisplayName("Exception: should handle long message")
    void exception_shouldHandleLongMessage() {
        // Given
        String longMessage = "A".repeat(1000);

        // When
        ConversationFormatException exception = new ConversationFormatException(longMessage);

        // Then
        assertThat(exception.getMessage()).isEqualTo(longMessage);
        assertThat(exception.getMessage().length()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Exception: should handle special characters in message")
    void exception_shouldHandleSpecialCharactersInMessage() {
        // Given
        String messageWithSpecialChars = "Invalid format: <script>alert('xss')</script> & \"quotes\" 'apostrophes'";

        // When
        ConversationFormatException exception = new ConversationFormatException(messageWithSpecialChars);

        // Then
        assertThat(exception.getMessage()).isEqualTo(messageWithSpecialChars);
    }

    @Test
    @DisplayName("Exception: should handle unicode characters in message")
    void exception_shouldHandleUnicodeCharactersInMessage() {
        // Given
        String unicodeMessage = "Invalid format: 你好世界 🌍 🎉";

        // When
        ConversationFormatException exception = new ConversationFormatException(unicodeMessage);

        // Then
        assertThat(exception.getMessage()).isEqualTo(unicodeMessage);
    }

    @Test
    @DisplayName("Exception: should handle nested exceptions in cause")
    void exception_shouldHandleNestedExceptionsInCause() {
        // Given
        String message = "Invalid conversation format";
        Exception nested1 = new IllegalArgumentException("Nested 1");
        Exception nested2 = new RuntimeException("Nested 2", nested1);
        Exception cause = new IllegalStateException("Cause", nested2);

        // When
        ConversationFormatException exception = new ConversationFormatException(message, cause);

        // Then
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCause().getCause()).isSameAs(nested2);
        assertThat(exception.getCause().getCause().getCause()).isSameAs(nested1);
    }

    @Test
    @DisplayName("Exception: should be serializable")
    void exception_shouldBeSerializable() {
        // Given
        String message = "Invalid conversation format";
        ConversationFormatException exception = new ConversationFormatException(message);

        // Then
        assertThat(exception).isInstanceOf(java.io.Serializable.class);
    }

    @Test
    @DisplayName("Exception: should have correct class name")
    void exception_shouldHaveCorrectClassName() {
        // Given
        ConversationFormatException exception = new ConversationFormatException("Test");

        // Then
        assertThat(exception.getClass().getName())
                .isEqualTo("uk.gegc.kidsgptbackend.shared.exception.ConversationFormatException");
    }
}

