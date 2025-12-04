package uk.gegc.kidsgptbackend.shared.api.problem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ErrorTypes Tests")
class ErrorTypesTest {

    private static final String BASE_URL = "https://kidsgpt.com/docs/errors";

    @Test
    @DisplayName("when accessing resource error types then return correct URIs")
    void whenAccessingResourceErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.RESOURCE_NOT_FOUND)
            .isEqualTo(URI.create(BASE_URL + "/resource-not-found"));
    }

    @Test
    @DisplayName("when accessing validation error types then return correct URIs")
    void whenAccessingValidationErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.VALIDATION_FAILED)
            .isEqualTo(URI.create(BASE_URL + "/validation-failed"));
        assertThat(ErrorTypes.INVALID_ARGUMENT)
            .isEqualTo(URI.create(BASE_URL + "/invalid-argument"));
        assertThat(ErrorTypes.CONSTRAINT_VIOLATION)
            .isEqualTo(URI.create(BASE_URL + "/constraint-violation"));
        assertThat(ErrorTypes.TYPE_MISMATCH)
            .isEqualTo(URI.create(BASE_URL + "/type-mismatch"));
        assertThat(ErrorTypes.MALFORMED_JSON)
            .isEqualTo(URI.create(BASE_URL + "/malformed-json"));
    }

    @Test
    @DisplayName("when accessing content error types then return correct URIs")
    void whenAccessingContentErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.CONTENT_TOO_LONG)
            .isEqualTo(URI.create(BASE_URL + "/content-too-long"));
        assertThat(ErrorTypes.INAPPROPRIATE_CONTENT)
            .isEqualTo(URI.create(BASE_URL + "/inappropriate-content"));
        assertThat(ErrorTypes.MODERATION_FAILED)
            .isEqualTo(URI.create(BASE_URL + "/moderation-failed"));
    }

    @Test
    @DisplayName("when accessing chat error types then return correct URIs")
    void whenAccessingChatErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.CONVERSATION_NOT_FOUND)
            .isEqualTo(URI.create(BASE_URL + "/conversation-not-found"));
        assertThat(ErrorTypes.CONVERSATION_FORMAT_ERROR)
            .isEqualTo(URI.create(BASE_URL + "/conversation-format-error"));
        assertThat(ErrorTypes.MESSAGE_LIMIT_EXCEEDED)
            .isEqualTo(URI.create(BASE_URL + "/message-limit-exceeded"));
    }

    @Test
    @DisplayName("when accessing story error types then return correct URIs")
    void whenAccessingStoryErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.STORY_NOT_FOUND)
            .isEqualTo(URI.create(BASE_URL + "/story-not-found"));
        assertThat(ErrorTypes.STORY_GENERATION_FAILED)
            .isEqualTo(URI.create(BASE_URL + "/story-generation-failed"));
        assertThat(ErrorTypes.STORY_TOO_LONG)
            .isEqualTo(URI.create(BASE_URL + "/story-too-long"));
    }

    @Test
    @DisplayName("when accessing joke and tip error types then return correct URIs")
    void whenAccessingJokeAndTipErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.JOKE_GENERATION_FAILED)
            .isEqualTo(URI.create(BASE_URL + "/joke-generation-failed"));
        assertThat(ErrorTypes.TIP_GENERATION_FAILED)
            .isEqualTo(URI.create(BASE_URL + "/tip-generation-failed"));
        assertThat(ErrorTypes.CATEGORY_NOT_FOUND)
            .isEqualTo(URI.create(BASE_URL + "/category-not-found"));
    }

    @Test
    @DisplayName("when accessing security error types then return correct URIs")
    void whenAccessingSecurityErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.UNAUTHORIZED)
            .isEqualTo(URI.create(BASE_URL + "/unauthorized"));
        assertThat(ErrorTypes.ACCESS_DENIED)
            .isEqualTo(URI.create(BASE_URL + "/access-denied"));
        assertThat(ErrorTypes.AUTHENTICATION_FAILED)
            .isEqualTo(URI.create(BASE_URL + "/authentication-failed"));
        assertThat(ErrorTypes.INVALID_TOKEN)
            .isEqualTo(URI.create(BASE_URL + "/invalid-token"));
        assertThat(ErrorTypes.TOKEN_EXPIRED)
            .isEqualTo(URI.create(BASE_URL + "/token-expired"));
    }

    @Test
    @DisplayName("when accessing user error types then return correct URIs")
    void whenAccessingUserErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.USER_NOT_FOUND)
            .isEqualTo(URI.create(BASE_URL + "/user-not-found"));
        assertThat(ErrorTypes.EMAIL_ALREADY_EXISTS)
            .isEqualTo(URI.create(BASE_URL + "/email-already-exists"));
        assertThat(ErrorTypes.USERNAME_ALREADY_EXISTS)
            .isEqualTo(URI.create(BASE_URL + "/username-already-exists"));
        assertThat(ErrorTypes.INVALID_CREDENTIALS)
            .isEqualTo(URI.create(BASE_URL + "/invalid-credentials"));
    }

    @Test
    @DisplayName("when accessing consent error types then return correct URIs")
    void whenAccessingConsentErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.CONSENT_REQUIRED)
            .isEqualTo(URI.create(BASE_URL + "/consent-required"));
        assertThat(ErrorTypes.PARENTAL_CONSENT_REQUIRED)
            .isEqualTo(URI.create(BASE_URL + "/parental-consent-required"));
        assertThat(ErrorTypes.VERIFICATION_FAILED)
            .isEqualTo(URI.create(BASE_URL + "/verification-failed"));
        assertThat(ErrorTypes.VERIFICATION_EXPIRED)
            .isEqualTo(URI.create(BASE_URL + "/verification-expired"));
    }

    @Test
    @DisplayName("when accessing subscription error types then return correct URIs")
    void whenAccessingSubscriptionErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.SUBSCRIPTION_NOT_FOUND)
            .isEqualTo(URI.create(BASE_URL + "/subscription-not-found"));
        assertThat(ErrorTypes.SUBSCRIPTION_INACTIVE)
            .isEqualTo(URI.create(BASE_URL + "/subscription-inactive"));
        assertThat(ErrorTypes.SUBSCRIPTION_EXPIRED)
            .isEqualTo(URI.create(BASE_URL + "/subscription-expired"));
        assertThat(ErrorTypes.PAYMENT_VERIFICATION_FAILED)
            .isEqualTo(URI.create(BASE_URL + "/payment-verification-failed"));
        assertThat(ErrorTypes.WEBHOOK_VERIFICATION_FAILED)
            .isEqualTo(URI.create(BASE_URL + "/webhook-verification-failed"));
        assertThat(ErrorTypes.IDEMPOTENCY_CONFLICT)
            .isEqualTo(URI.create(BASE_URL + "/idempotency-conflict"));
    }

    @Test
    @DisplayName("when accessing state error types then return correct URIs")
    void whenAccessingStateErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.ILLEGAL_STATE)
            .isEqualTo(URI.create(BASE_URL + "/illegal-state"));
        assertThat(ErrorTypes.DATA_CONFLICT)
            .isEqualTo(URI.create(BASE_URL + "/data-conflict"));
        assertThat(ErrorTypes.OPTIMISTIC_LOCK_CONFLICT)
            .isEqualTo(URI.create(BASE_URL + "/optimistic-lock-conflict"));
        assertThat(ErrorTypes.DATA_INTEGRITY_VIOLATION)
            .isEqualTo(URI.create(BASE_URL + "/data-integrity-violation"));
    }

    @Test
    @DisplayName("when accessing rate limit error types then return correct URIs")
    void whenAccessingRateLimitErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.RATE_LIMIT_EXCEEDED)
            .isEqualTo(URI.create(BASE_URL + "/rate-limit-exceeded"));
    }

    @Test
    @DisplayName("when accessing AI service error types then return correct URIs")
    void whenAccessingAiServiceErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.AI_SERVICE_UNAVAILABLE)
            .isEqualTo(URI.create(BASE_URL + "/ai-service-unavailable"));
        assertThat(ErrorTypes.AI_SERVICE_ERROR)
            .isEqualTo(URI.create(BASE_URL + "/ai-service-error"));
        assertThat(ErrorTypes.AI_CONTENT_FILTERED)
            .isEqualTo(URI.create(BASE_URL + "/ai-content-filtered"));
    }

    @Test
    @DisplayName("when accessing generic error types then return correct URIs")
    void whenAccessingGenericErrorTypes_thenReturnCorrectUris() {
        // Then
        assertThat(ErrorTypes.INTERNAL_SERVER_ERROR)
            .isEqualTo(URI.create(BASE_URL + "/internal-server-error"));
        assertThat(ErrorTypes.SERVICE_UNAVAILABLE)
            .isEqualTo(URI.create(BASE_URL + "/service-unavailable"));
        assertThat(ErrorTypes.GENERIC_ERROR)
            .isEqualTo(URI.create(BASE_URL + "/error"));
    }

    @Test
    @DisplayName("when instantiating utility class then throws AssertionError")
    void whenInstantiatingUtilityClass_thenThrowsAssertionError() {
        // Then
        assertThatThrownBy(() -> {
            var constructor = ErrorTypes.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .isInstanceOf(Exception.class) // Could be InvocationTargetException
        .cause()
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Utility class - do not instantiate");
    }

    @Test
    @DisplayName("when all error types accessed then no duplicates exist")
    void whenAllErrorTypesAccessed_thenNoDuplicatesExist() {
        // Given - collect all error type URIs
        var allErrorTypes = java.util.Set.of(
            ErrorTypes.RESOURCE_NOT_FOUND,
            ErrorTypes.VALIDATION_FAILED,
            ErrorTypes.INVALID_ARGUMENT,
            ErrorTypes.CONSTRAINT_VIOLATION,
            ErrorTypes.TYPE_MISMATCH,
            ErrorTypes.MALFORMED_JSON,
            ErrorTypes.UNSUPPORTED_OPERATION,
            ErrorTypes.CONTENT_TOO_LONG,
            ErrorTypes.INAPPROPRIATE_CONTENT,
            ErrorTypes.MODERATION_FAILED,
            ErrorTypes.CONVERSATION_NOT_FOUND,
            ErrorTypes.CONVERSATION_FORMAT_ERROR,
            ErrorTypes.MESSAGE_LIMIT_EXCEEDED,
            ErrorTypes.STORY_NOT_FOUND,
            ErrorTypes.STORY_GENERATION_FAILED,
            ErrorTypes.STORY_TOO_LONG,
            ErrorTypes.JOKE_GENERATION_FAILED,
            ErrorTypes.TIP_GENERATION_FAILED,
            ErrorTypes.CATEGORY_NOT_FOUND,
            ErrorTypes.UNAUTHORIZED,
            ErrorTypes.ACCESS_DENIED,
            ErrorTypes.AUTHENTICATION_FAILED,
            ErrorTypes.INVALID_TOKEN,
            ErrorTypes.TOKEN_EXPIRED,
            ErrorTypes.USER_NOT_FOUND,
            ErrorTypes.EMAIL_ALREADY_EXISTS,
            ErrorTypes.USERNAME_ALREADY_EXISTS,
            ErrorTypes.INVALID_CREDENTIALS,
            ErrorTypes.CREDENTIAL_UPDATE_FAILED,
            ErrorTypes.CONSENT_REQUIRED,
            ErrorTypes.PARENTAL_CONSENT_REQUIRED,
            ErrorTypes.VERIFICATION_FAILED,
            ErrorTypes.VERIFICATION_EXPIRED,
            ErrorTypes.SUBSCRIPTION_NOT_FOUND,
            ErrorTypes.SUBSCRIPTION_INACTIVE,
            ErrorTypes.SUBSCRIPTION_EXPIRED,
            ErrorTypes.PAYMENT_VERIFICATION_FAILED,
            ErrorTypes.WEBHOOK_VERIFICATION_FAILED,
            ErrorTypes.WEBHOOK_PROCESSING_ERROR,
            ErrorTypes.IDEMPOTENCY_CONFLICT,
            ErrorTypes.ILLEGAL_STATE,
            ErrorTypes.DATA_CONFLICT,
            ErrorTypes.OPTIMISTIC_LOCK_CONFLICT,
            ErrorTypes.DATA_INTEGRITY_VIOLATION,
            ErrorTypes.RATE_LIMIT_EXCEEDED,
            ErrorTypes.AI_SERVICE_UNAVAILABLE,
            ErrorTypes.AI_SERVICE_ERROR,
            ErrorTypes.AI_CONTENT_FILTERED,
            ErrorTypes.INTERNAL_SERVER_ERROR,
            ErrorTypes.SERVICE_UNAVAILABLE,
            ErrorTypes.GENERIC_ERROR
        );
        
        // Then - size should match if no duplicates
        // If there were duplicates, Set would have fewer elements
        assertThat(allErrorTypes).hasSize(51); // Verify we have 51 unique error types
    }
}

