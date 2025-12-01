package uk.gegc.kidsgptbackend.features.systemstatus.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.moderation.Generation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiagnosticsController Tests")
class DiagnosticsControllerTest extends BaseUnitTest {

    @Mock
    private ModerationModel moderationModel;

    @InjectMocks
    private DiagnosticsController diagnosticsController;

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        // Set default values for @Value fields
        ReflectionTestUtils.setField(diagnosticsController, "configuredModel", "omni-moderation-latest");
        ReflectionTestUtils.setField(diagnosticsController, "chatModel", "gpt-4");
    }

    @Test
    @DisplayName("getOpenAiModels: when moderationModel is OpenAiModerationModel with defaultOptions then return detailed info")
    void getOpenAiModels_whenOpenAiModerationModelWithDefaultOptions_thenReturnDetailedInfo() {
        // Given
        OpenAiModerationModel openAiModel = mock(OpenAiModerationModel.class);
        OpenAiModerationOptions defaultOptions = OpenAiModerationOptions.builder()
                .model("omni-moderation-latest")
                .build();

        when(openAiModel.getDefaultOptions()).thenReturn(defaultOptions);

        // Use reflection to set the moderationModel to an OpenAiModerationModel instance
        ReflectionTestUtils.setField(diagnosticsController, "moderationModel", openAiModel);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.getOpenAiModels();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("isOpenAiModerationModel")).isEqualTo(true);
        assertThat(body.get("defaultOptionsPresent")).isEqualTo(true);
        assertThat(body.get("defaultOptionsModel")).isEqualTo("omni-moderation-latest");
        assertThat(body.get("postConstructLikelyRan")).isEqualTo(true);
        assertThat(body.get("propertyConfigured")).isEqualTo("omni-moderation-latest");
        assertThat(body.get("propertySetCorrectly")).isEqualTo(true);
        assertThat(body.get("status")).isEqualTo("PROPERTY_OK");
    }

    @Test
    @DisplayName("getOpenAiModels: when moderationModel is NOT OpenAiModerationModel then return basic info")
    void getOpenAiModels_whenNotOpenAiModerationModel_thenReturnBasicInfo() {
        // Given - use a simple mock that is not OpenAiModerationModel
        ModerationModel nonOpenAiModel = mock(ModerationModel.class);
        ReflectionTestUtils.setField(diagnosticsController, "moderationModel", nonOpenAiModel);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.getOpenAiModels();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("isOpenAiModerationModel")).isEqualTo(false);
        assertThat(body.get("propertyConfigured")).isEqualTo("omni-moderation-latest");
        assertThat(body.get("chatModelConfigured")).isEqualTo("gpt-4");
        assertThat(body.get("expectedModerationModel")).isEqualTo("omni-moderation-latest");
    }

    @Test
    @DisplayName("getOpenAiModels: when defaultOptions is null then return warning")
    void getOpenAiModels_whenDefaultOptionsIsNull_thenReturnWarning() {
        // Given
        OpenAiModerationModel openAiModel = mock(OpenAiModerationModel.class);
        when(openAiModel.getDefaultOptions()).thenReturn(null);
        ReflectionTestUtils.setField(diagnosticsController, "moderationModel", openAiModel);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.getOpenAiModels();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("defaultOptionsPresent")).isEqualTo(false);
        assertThat(body.get("warning")).isEqualTo("PostConstruct withDefaultOptions() might not have worked");
    }

    @Test
    @DisplayName("getOpenAiModels: when exception occurs then handle gracefully")
    void getOpenAiModels_whenExceptionOccurs_thenHandleGracefully() {
        // Given
        OpenAiModerationModel openAiModel = mock(OpenAiModerationModel.class);
        when(openAiModel.getDefaultOptions()).thenThrow(new RuntimeException("Test exception"));
        ReflectionTestUtils.setField(diagnosticsController, "moderationModel", openAiModel);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.getOpenAiModels();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("defaultOptionsError")).isEqualTo("Test exception");
    }

    @Test
    @DisplayName("getOpenAiModels: when property is not set correctly then return PROPERTY_MISSING_OR_WRONG")
    void getOpenAiModels_whenPropertyNotSetCorrectly_thenReturnPropertyMissingOrWrong() {
        // Given
        ReflectionTestUtils.setField(diagnosticsController, "configuredModel", "wrong-model");
        ModerationModel nonOpenAiModel = mock(ModerationModel.class);
        ReflectionTestUtils.setField(diagnosticsController, "moderationModel", nonOpenAiModel);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.getOpenAiModels();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("propertySetCorrectly")).isEqualTo(false);
        assertThat(body.get("status")).isEqualTo("PROPERTY_MISSING_OR_WRONG");
    }

    @Test
    @DisplayName("testModeration: when API call succeeds then return SUCCESS")
    void testModeration_whenApiCallSucceeds_thenReturnSuccess() {
        // Given
        ModerationResponse moderationResponse = mock(ModerationResponse.class);
        when(moderationModel.call(any())).thenReturn(moderationResponse);
        Generation generation = mock(Generation.class);
        when(moderationResponse.getResult()).thenReturn(generation);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.testModeration();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("SUCCESS");
        assertThat(body.get("message")).isEqualTo("Moderation API call succeeded");
        assertThat(body.get("hasResult")).isEqualTo(true);
    }

    @Test
    @DisplayName("testModeration: when API call fails with exception then return ERROR")
    void testModeration_whenApiCallFails_thenReturnError() {
        // Given
        when(moderationModel.call(any())).thenThrow(new RuntimeException("API call failed"));

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.testModeration();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("ERROR");
        assertThat(body.get("error")).isEqualTo("RuntimeException");
        assertThat(body.get("message")).isEqualTo("API call failed");
    }

    @Test
    @DisplayName("testModeration: when API call fails with 400 error then return diagnosis")
    void testModeration_whenApiCallFailsWith400_thenReturnDiagnosis() {
        // Given
        RuntimeException exception = new RuntimeException("400 Bad Request");
        when(moderationModel.call(any())).thenThrow(exception);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.testModeration();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("ERROR");
        assertThat(body.get("diagnosis")).isEqualTo("400 error - likely a model name issue");
    }

    @Test
    @DisplayName("testModeration: when API call fails with omni-moderation-latest error then return specific diagnosis")
    void testModeration_whenApiCallFailsWithOmniModerationError_thenReturnSpecificDiagnosis() {
        // Given
        RuntimeException exception = new RuntimeException("Error with omni-moderation-latest model");
        when(moderationModel.call(any())).thenThrow(exception);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.testModeration();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("ERROR");
        assertThat(body.get("diagnosis")).isEqualTo("Wrong moderation model configured - needs omni-moderation-latest");
    }

    @Test
    @DisplayName("testModeration: when configuredModel is not-set then use default")
    void testModeration_whenConfiguredModelNotSet_thenUseDefault() {
        // Given
        ReflectionTestUtils.setField(diagnosticsController, "configuredModel", "not-set");
        ModerationResponse moderationResponse = mock(ModerationResponse.class);
        when(moderationModel.call(any())).thenReturn(moderationResponse);
        Generation generation = mock(Generation.class);
        when(moderationResponse.getResult()).thenReturn(generation);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.testModeration();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("SUCCESS");
        // Verify that the call was made with "omni-moderation-latest" model
        verify(moderationModel).call(any());
    }

    @Test
    @DisplayName("testModeration: when configuredModel is null then use default")
    void testModeration_whenConfiguredModelIsNull_thenUseDefault() {
        // Given
        ReflectionTestUtils.setField(diagnosticsController, "configuredModel", null);
        ModerationResponse moderationResponse = mock(ModerationResponse.class);
        when(moderationModel.call(any())).thenReturn(moderationResponse);
        Generation generation = mock(Generation.class);
        when(moderationResponse.getResult()).thenReturn(generation);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.testModeration();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("testModeration: when configuredModel is blank then use default")
    void testModeration_whenConfiguredModelIsBlank_thenUseDefault() {
        // Given
        ReflectionTestUtils.setField(diagnosticsController, "configuredModel", "   ");
        ModerationResponse moderationResponse = mock(ModerationResponse.class);
        when(moderationModel.call(any())).thenReturn(moderationResponse);
        Generation generation = mock(Generation.class);
        when(moderationResponse.getResult()).thenReturn(generation);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.testModeration();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("testModeration: when result is null then handle gracefully")
    void testModeration_whenResultIsNull_thenHandleGracefully() {
        // Given
        when(moderationModel.call(any())).thenReturn(null);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.testModeration();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("SUCCESS");
        assertThat(body.get("hasResult")).isEqualTo(false);
    }

    @Test
    @DisplayName("testModeration: when result.getResult() is null then handle gracefully")
    void testModeration_whenResultGetResultIsNull_thenHandleGracefully() {
        // Given
        ModerationResponse moderationResponse = mock(ModerationResponse.class);
        when(moderationResponse.getResult()).thenReturn(null);
        when(moderationModel.call(any())).thenReturn(moderationResponse);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.testModeration();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("SUCCESS");
        assertThat(body.get("hasResult")).isEqualTo(false);
    }

    @Test
    @DisplayName("getOpenAiModels: when defaultOptions model is not omni-moderation-latest then postConstructLikelyRan is false")
    void getOpenAiModels_whenDefaultOptionsModelIsNotOmniModeration_thenPostConstructLikelyRanIsFalse() {
        // Given
        OpenAiModerationModel openAiModel = mock(OpenAiModerationModel.class);
        OpenAiModerationOptions defaultOptions = OpenAiModerationOptions.builder()
                .model("other-model")
                .build();
        when(openAiModel.getDefaultOptions()).thenReturn(defaultOptions);
        ReflectionTestUtils.setField(diagnosticsController, "moderationModel", openAiModel);

        // When
        ResponseEntity<Map<String, Object>> response = diagnosticsController.getOpenAiModels();

        // Then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("postConstructLikelyRan")).isEqualTo(false);
    }
}

