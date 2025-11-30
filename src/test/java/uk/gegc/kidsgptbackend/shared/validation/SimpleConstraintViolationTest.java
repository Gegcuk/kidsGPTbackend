package uk.gegc.kidsgptbackend.shared.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SimpleConstraintViolation}.
 * <p>
 * Tests the minimal ConstraintViolation implementation used for programmatically
 * constructing validation errors without a full validation framework.
 */
@DisplayName("SimpleConstraintViolation Tests")
class SimpleConstraintViolationTest extends BaseUnitTest {

    @Test
    @DisplayName("getMessage: should return the provided message")
    void getMessage_shouldReturnProvidedMessage() {
        // Given
        String message = "Test validation message";
        SimpleConstraintViolation violation = new SimpleConstraintViolation(message);

        // When
        String result = violation.getMessage();

        // Then
        assertThat(result).isEqualTo(message);
    }

    @Test
    @DisplayName("getMessageTemplate: should return the message as template")
    void getMessageTemplate_shouldReturnMessageAsTemplate() {
        // Given
        String message = "Test validation message";
        SimpleConstraintViolation violation = new SimpleConstraintViolation(message);

        // When
        String result = violation.getMessageTemplate();

        // Then
        assertThat(result).isEqualTo(message);
    }

    @Test
    @DisplayName("getRootBean: should return null")
    void getRootBean_shouldReturnNull() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("message");

        // When
        Object result = violation.getRootBean();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getRootBeanClass: should return Object.class")
    void getRootBeanClass_shouldReturnObjectClass() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("message");

        // When
        Class<Object> result = violation.getRootBeanClass();

        // Then
        assertThat(result).isEqualTo(Object.class);
    }

    @Test
    @DisplayName("getLeafBean: should return null")
    void getLeafBean_shouldReturnNull() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("message");

        // When
        Object result = violation.getLeafBean();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getExecutableParameters: should return null")
    void getExecutableParameters_shouldReturnNull() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("message");

        // When
        Object[] result = violation.getExecutableParameters();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getExecutableReturnValue: should return null")
    void getExecutableReturnValue_shouldReturnNull() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("message");

        // When
        Object result = violation.getExecutableReturnValue();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getPropertyPath: should return null")
    void getPropertyPath_shouldReturnNull() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("message");

        // When
        Path result = violation.getPropertyPath();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getInvalidValue: should return null")
    void getInvalidValue_shouldReturnNull() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("message");

        // When
        Object result = violation.getInvalidValue();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getConstraintDescriptor: should return null")
    void getConstraintDescriptor_shouldReturnNull() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("message");

        // When
        ConstraintDescriptor<?> result = violation.getConstraintDescriptor();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("unwrap: should throw UnsupportedOperationException")
    void unwrap_shouldThrowUnsupportedOperationException() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("message");

        // When/Then
        assertThatThrownBy(() -> violation.unwrap(String.class))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("unwrap: should throw UnsupportedOperationException for any type")
    void unwrap_anyType_shouldThrowUnsupportedOperationException() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("message");

        // When/Then
        assertThatThrownBy(() -> violation.unwrap(Integer.class))
                .isInstanceOf(UnsupportedOperationException.class);
        
        assertThatThrownBy(() -> violation.unwrap(ConstraintViolation.class))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("record equality: should be equal when messages are equal")
    void recordEquality_equalMessages_shouldBeEqual() {
        // Given
        String message = "Same message";
        SimpleConstraintViolation violation1 = new SimpleConstraintViolation(message);
        SimpleConstraintViolation violation2 = new SimpleConstraintViolation(message);

        // When/Then
        assertThat(violation1).isEqualTo(violation2);
        assertThat(violation1.hashCode()).isEqualTo(violation2.hashCode());
    }

    @Test
    @DisplayName("record equality: should not be equal when messages differ")
    void recordEquality_differentMessages_shouldNotBeEqual() {
        // Given
        SimpleConstraintViolation violation1 = new SimpleConstraintViolation("Message 1");
        SimpleConstraintViolation violation2 = new SimpleConstraintViolation("Message 2");

        // When/Then
        assertThat(violation1).isNotEqualTo(violation2);
    }

    @Test
    @DisplayName("toString: should include message")
    void toString_shouldIncludeMessage() {
        // Given
        String message = "Test message";
        SimpleConstraintViolation violation = new SimpleConstraintViolation(message);

        // When
        String result = violation.toString();

        // Then
        assertThat(result).contains(message);
    }

    @Test
    @DisplayName("constructor: should handle empty message")
    void constructor_emptyMessage_shouldWork() {
        // Given
        String message = "";

        // When
        SimpleConstraintViolation violation = new SimpleConstraintViolation(message);

        // Then
        assertThat(violation.getMessage()).isEmpty();
        assertThat(violation.getMessageTemplate()).isEmpty();
    }

    @Test
    @DisplayName("constructor: should handle null message")
    void constructor_nullMessage_shouldWork() {
        // Given
        String message = null;

        // When
        SimpleConstraintViolation violation = new SimpleConstraintViolation(message);

        // Then
        assertThat(violation.getMessage()).isNull();
        assertThat(violation.getMessageTemplate()).isNull();
    }

    @Test
    @DisplayName("implements ConstraintViolation interface correctly")
    void implementsConstraintViolationInterface_correctly() {
        // Given
        SimpleConstraintViolation violation = new SimpleConstraintViolation("test");

        // When/Then - verify it implements the interface
        assertThat(violation).isInstanceOf(ConstraintViolation.class);
        assertThat(violation).isInstanceOf(ConstraintViolation.class);
    }
}

