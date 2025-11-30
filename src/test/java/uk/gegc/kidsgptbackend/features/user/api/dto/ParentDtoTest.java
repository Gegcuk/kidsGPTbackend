package uk.gegc.kidsgptbackend.features.user.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ParentDto Tests")
class ParentDtoTest extends BaseUnitTest {

    @Test
    @DisplayName("Should create ParentDto with all fields")
    void parentDto_WithAllFields_CreatesCorrectly() {
        // Given
        UUID id = UUID.randomUUID();
        String firstName = "John";
        String lastName = "Doe";
        String email = "john.doe@example.com";
        String phoneNumber = "+1234567890";

        // When
        ParentDto dto = new ParentDto(id, firstName, lastName, email, phoneNumber);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.firstName()).isEqualTo(firstName);
        assertThat(dto.lastName()).isEqualTo(lastName);
        assertThat(dto.email()).isEqualTo(email);
        assertThat(dto.phoneNumber()).isEqualTo(phoneNumber);
    }

    @Test
    @DisplayName("Should create ParentDto with null phone number")
    void parentDto_WithNullPhoneNumber_CreatesCorrectly() {
        // Given
        UUID id = UUID.randomUUID();
        String firstName = "Jane";
        String lastName = "Smith";
        String email = "jane.smith@example.com";

        // When
        ParentDto dto = new ParentDto(id, firstName, lastName, email, null);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.firstName()).isEqualTo(firstName);
        assertThat(dto.lastName()).isEqualTo(lastName);
        assertThat(dto.email()).isEqualTo(email);
        assertThat(dto.phoneNumber()).isNull();
    }
}

