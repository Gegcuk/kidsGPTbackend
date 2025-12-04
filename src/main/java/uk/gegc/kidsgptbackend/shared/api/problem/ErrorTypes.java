package uk.gegc.kidsgptbackend.shared.api.problem;

import java.net.URI;

/**
 * Centralized catalog of RFC 9457 Problem Detail type URIs.
 * Each constant points to documentation describing the error.
 *
 * <p>Example usage:
 * <pre>
 * ProblemDetail problem = ProblemDetailBuilder.create(
 *     HttpStatus.NOT_FOUND,
 *     ErrorTypes.RESOURCE_NOT_FOUND,
 *     "Resource Not Found",
 *     "The requested resource could not be found",
 *     request
 * );
 * </pre>
 *
 * @see ProblemDetailBuilder
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457</a>
 */
public final class ErrorTypes {

    private static final String BASE_URL = "https://kidsgpt.com/docs/errors";

    // ==================== Resource Errors ====================
    public static final URI RESOURCE_NOT_FOUND = URI.create(BASE_URL + "/resource-not-found");
    
    // ==================== Validation Errors ====================
    public static final URI VALIDATION_FAILED = URI.create(BASE_URL + "/validation-failed");
    public static final URI INVALID_ARGUMENT = URI.create(BASE_URL + "/invalid-argument");
    public static final URI CONSTRAINT_VIOLATION = URI.create(BASE_URL + "/constraint-violation");
    public static final URI TYPE_MISMATCH = URI.create(BASE_URL + "/type-mismatch");
    public static final URI MALFORMED_JSON = URI.create(BASE_URL + "/malformed-json");
    public static final URI UNSUPPORTED_OPERATION = URI.create(BASE_URL + "/unsupported-operation");

    // ==================== Content Errors ====================
    public static final URI CONTENT_TOO_LONG = URI.create(BASE_URL + "/content-too-long");
    public static final URI INAPPROPRIATE_CONTENT = URI.create(BASE_URL + "/inappropriate-content");
    public static final URI MODERATION_FAILED = URI.create(BASE_URL + "/moderation-failed");
    
    // ==================== Chat Errors ====================
    public static final URI CONVERSATION_NOT_FOUND = URI.create(BASE_URL + "/conversation-not-found");
    public static final URI CONVERSATION_FORMAT_ERROR = URI.create(BASE_URL + "/conversation-format-error");
    public static final URI MESSAGE_LIMIT_EXCEEDED = URI.create(BASE_URL + "/message-limit-exceeded");
    
    // ==================== Story Errors ====================
    public static final URI STORY_NOT_FOUND = URI.create(BASE_URL + "/story-not-found");
    public static final URI STORY_GENERATION_FAILED = URI.create(BASE_URL + "/story-generation-failed");
    public static final URI STORY_TOO_LONG = URI.create(BASE_URL + "/story-too-long");
    
    // ==================== Joke/Tip Errors ====================
    public static final URI JOKE_GENERATION_FAILED = URI.create(BASE_URL + "/joke-generation-failed");
    public static final URI TIP_GENERATION_FAILED = URI.create(BASE_URL + "/tip-generation-failed");
    public static final URI CATEGORY_NOT_FOUND = URI.create(BASE_URL + "/category-not-found");

    // ==================== Security Errors ====================
    public static final URI UNAUTHORIZED = URI.create(BASE_URL + "/unauthorized");
    public static final URI ACCESS_DENIED = URI.create(BASE_URL + "/access-denied");
    public static final URI AUTHENTICATION_FAILED = URI.create(BASE_URL + "/authentication-failed");
    public static final URI INVALID_TOKEN = URI.create(BASE_URL + "/invalid-token");
    public static final URI TOKEN_EXPIRED = URI.create(BASE_URL + "/token-expired");
    
    // ==================== User Errors ====================
    public static final URI USER_NOT_FOUND = URI.create(BASE_URL + "/user-not-found");
    public static final URI EMAIL_ALREADY_EXISTS = URI.create(BASE_URL + "/email-already-exists");
    public static final URI USERNAME_ALREADY_EXISTS = URI.create(BASE_URL + "/username-already-exists");
    public static final URI INVALID_CREDENTIALS = URI.create(BASE_URL + "/invalid-credentials");
    public static final URI CREDENTIAL_UPDATE_FAILED = URI.create(BASE_URL + "/credential-update-failed");

    // ==================== Consent Errors ====================
    public static final URI CONSENT_REQUIRED = URI.create(BASE_URL + "/consent-required");
    public static final URI PARENTAL_CONSENT_REQUIRED = URI.create(BASE_URL + "/parental-consent-required");
    public static final URI VERIFICATION_FAILED = URI.create(BASE_URL + "/verification-failed");
    public static final URI VERIFICATION_EXPIRED = URI.create(BASE_URL + "/verification-expired");

    // ==================== Subscription/Payment Errors ====================
    public static final URI SUBSCRIPTION_NOT_FOUND = URI.create(BASE_URL + "/subscription-not-found");
    public static final URI SUBSCRIPTION_INACTIVE = URI.create(BASE_URL + "/subscription-inactive");
    public static final URI SUBSCRIPTION_EXPIRED = URI.create(BASE_URL + "/subscription-expired");
    public static final URI PAYMENT_VERIFICATION_FAILED = URI.create(BASE_URL + "/payment-verification-failed");
    public static final URI WEBHOOK_VERIFICATION_FAILED = URI.create(BASE_URL + "/webhook-verification-failed");
    public static final URI WEBHOOK_PROCESSING_ERROR = URI.create(BASE_URL + "/webhook-processing-error");
    public static final URI IDEMPOTENCY_CONFLICT = URI.create(BASE_URL + "/idempotency-conflict");

    // ==================== State Errors ====================
    public static final URI ILLEGAL_STATE = URI.create(BASE_URL + "/illegal-state");
    public static final URI DATA_CONFLICT = URI.create(BASE_URL + "/data-conflict");
    public static final URI OPTIMISTIC_LOCK_CONFLICT = URI.create(BASE_URL + "/optimistic-lock-conflict");
    public static final URI DATA_INTEGRITY_VIOLATION = URI.create(BASE_URL + "/data-integrity-violation");

    // ==================== Rate Limiting ====================
    public static final URI RATE_LIMIT_EXCEEDED = URI.create(BASE_URL + "/rate-limit-exceeded");

    // ==================== AI Service Errors ====================
    public static final URI AI_SERVICE_UNAVAILABLE = URI.create(BASE_URL + "/ai-service-unavailable");
    public static final URI AI_SERVICE_ERROR = URI.create(BASE_URL + "/ai-service-error");
    public static final URI AI_CONTENT_FILTERED = URI.create(BASE_URL + "/ai-content-filtered");

    // ==================== Generic Errors ====================
    public static final URI INTERNAL_SERVER_ERROR = URI.create(BASE_URL + "/internal-server-error");
    public static final URI SERVICE_UNAVAILABLE = URI.create(BASE_URL + "/service-unavailable");
    public static final URI GENERIC_ERROR = URI.create(BASE_URL + "/error");

    private ErrorTypes() {
        throw new AssertionError("Utility class - do not instantiate");
    }
}

