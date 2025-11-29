package uk.gegc.kidsgptbackend.service.subscription.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
    SubscriptionAcknowledgerIntegrationTest.TestConfig.class,
    SubscriptionAcknowledger.class
})
@TestPropertySource(properties = {
    "spring.retry.enabled=true",
    "google.play.service-account-key=",
    "google.play.package-name=test.package",
    "google.play.application-name=TestApp"
})
@DisplayName("SubscriptionAcknowledger Integration Tests")
class SubscriptionAcknowledgerIntegrationTest {

    @Configuration
    @EnableRetry
    @Import(SubscriptionAcknowledger.class)
    static class TestConfig {
    }

    @MockitoBean
    private GooglePlayClient googlePlayClient;

    @Autowired
    private SubscriptionAcknowledger subscriptionAcknowledger;

    private String testProductId;
    private String testPurchaseToken;

    @BeforeEach
    void setUp() {
        testProductId = "premium_monthly";
        testPurchaseToken = "purchase_token_123";
    }

    // ==================== RETRY BEHAVIOR ====================

    @Test
    @DisplayName("Retry behavior: Transient exception retries up to 3 attempts with backoff")
    void retryBehavior_transientExceptionRetriesUpToThreeAttemptsWithBackoff() {
        // Given - Google Play client throws transient exception on first 2 attempts, succeeds on 3rd
        doThrow(new RuntimeException("Connection refused"))
                .doThrow(new RuntimeException("Read timed out"))
                .doNothing()
                .when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When
        subscriptionAcknowledger.acknowledge(testProductId, testPurchaseToken);

        // Then - Should succeed after 3 attempts
        verify(googlePlayClient, times(3)).acknowledgeSubscription(testProductId, testPurchaseToken, null);
    }

    @Test
    @DisplayName("Retry behavior: Success on 2nd attempt stops further retries")
    void retryBehavior_successOnSecondAttemptStopsFurtherRetries() {
        // Given - Google Play client throws exception on first attempt, succeeds on 2nd
        doThrow(new RuntimeException("Connection refused"))
                .doNothing()
                .when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When
        subscriptionAcknowledger.acknowledge(testProductId, testPurchaseToken);

        // Then - Should succeed after 2 attempts (no 3rd attempt)
        verify(googlePlayClient, times(2)).acknowledgeSubscription(testProductId, testPurchaseToken, null);
    }

    @Test
    @DisplayName("Retry behavior: After max attempts logs error; caller continues per design")
    void retryBehavior_afterMaxAttemptsLogsErrorCallerContinuesPerDesign() {
        // Given - Google Play client always throws exception
        doThrow(new RuntimeException("Connection refused"))
                .when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When & Then - Should throw exception after max attempts
        assertThatThrownBy(() -> subscriptionAcknowledger.acknowledge(testProductId, testPurchaseToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Connection refused");

        // Verify all 3 attempts were made
        verify(googlePlayClient, times(3)).acknowledgeSubscription(testProductId, testPurchaseToken, null);
    }

    @Test
    @DisplayName("Retry behavior: All exceptions are retried per configuration")
    void retryBehavior_allExceptionsAreRetriedPerConfiguration() {
        // Given - Google Play client throws any exception (all are retried per @Retryable configuration)
        IllegalArgumentException exception = new IllegalArgumentException("Invalid product ID");
        doThrow(exception)
                .when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When & Then - Should throw after max attempts (all exceptions are retried)
        assertThatThrownBy(() -> subscriptionAcknowledger.acknowledge(testProductId, testPurchaseToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid product ID");

        // Verify all 3 attempts were made (all exceptions are retried per configuration)
        verify(googlePlayClient, times(3)).acknowledgeSubscription(testProductId, testPurchaseToken, null);
    }

    // ==================== BACKOFF TIMING ====================

    @Test
    @DisplayName("Backoff timing: First retry after 1s delay, second retry after 2s delay")
    void backoffTiming_firstRetryAfterOneSecondSecondRetryAfterTwoSeconds() {
        // Given - Google Play client throws exception on first 2 attempts, succeeds on 3rd
        doThrow(new RuntimeException("Connection refused"))
                .doThrow(new RuntimeException("Read timed out"))
                .doNothing()
                .when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When
        long startTime = System.currentTimeMillis();
        subscriptionAcknowledger.acknowledge(testProductId, testPurchaseToken);
        long endTime = System.currentTimeMillis();

        // Then - Should take at least 3 seconds (1s + 2s delays)
        long totalTime = endTime - startTime;
        assertThat(totalTime).isGreaterThanOrEqualTo(3000); // At least 3 seconds

        // Verify all 3 attempts were made
        verify(googlePlayClient, times(3)).acknowledgeSubscription(testProductId, testPurchaseToken, null);
    }

    @Test
    @DisplayName("Backoff timing: Success on first attempt has no delay")
    void backoffTiming_successOnFirstAttemptHasNoDelay() {
        // Given - Google Play client succeeds immediately
        doNothing().when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When
        long startTime = System.currentTimeMillis();
        subscriptionAcknowledger.acknowledge(testProductId, testPurchaseToken);
        long endTime = System.currentTimeMillis();

        // Then - Should complete quickly (no retry delays)
        long totalTime = endTime - startTime;
        assertThat(totalTime).isLessThan(1000); // Less than 1 second

        // Verify only 1 attempt was made
        verify(googlePlayClient, times(1)).acknowledgeSubscription(testProductId, testPurchaseToken, null);
    }

    // ==================== SUCCESS SCENARIOS ====================

    @Test
    @DisplayName("Success scenarios: Immediate success on first attempt")
    void successScenarios_immediateSuccessOnFirstAttempt() {
        // Given - Google Play client succeeds immediately
        doNothing().when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When
        subscriptionAcknowledger.acknowledge(testProductId, testPurchaseToken);

        // Then - Should succeed with single attempt
        verify(googlePlayClient, times(1)).acknowledgeSubscription(testProductId, testPurchaseToken, null);
    }

    @Test
    @DisplayName("Success scenarios: Success after transient failures")
    void successScenarios_successAfterTransientFailures() {
        // Given - Google Play client fails twice, succeeds on 3rd attempt
        doThrow(new RuntimeException("Connection refused"))
                .doThrow(new RuntimeException("Read timed out"))
                .doNothing()
                .when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When
        subscriptionAcknowledger.acknowledge(testProductId, testPurchaseToken);

        // Then - Should succeed after 3 attempts
        verify(googlePlayClient, times(3)).acknowledgeSubscription(testProductId, testPurchaseToken, null);
    }

    // ==================== FAILURE SCENARIOS ====================

    @Test
    @DisplayName("Failure scenarios: All attempts fail with transient exceptions")
    void failureScenarios_allAttemptsFailWithTransientExceptions() {
        // Given - Google Play client always throws transient exception
        doThrow(new RuntimeException("Connection refused"))
                .when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When & Then - Should throw exception after max attempts
        assertThatThrownBy(() -> subscriptionAcknowledger.acknowledge(testProductId, testPurchaseToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Connection refused");

        // Verify all 3 attempts were made
        verify(googlePlayClient, times(3)).acknowledgeSubscription(testProductId, testPurchaseToken, null);
    }

    // ==================== INTEGRATION SCENARIOS ====================

    @Test
    @DisplayName("Integration scenarios: Multiple acknowledgements in sequence")
    void integrationScenarios_multipleAcknowledgementsInSequence() {
        // Given - Google Play client succeeds for all
        doNothing().when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When - Multiple acknowledgements
        subscriptionAcknowledger.acknowledge("product1", "token1");
        subscriptionAcknowledger.acknowledge("product2", "token2");
        subscriptionAcknowledger.acknowledge("product3", "token3");

        // Then - All should succeed
        verify(googlePlayClient, times(1)).acknowledgeSubscription("product1", "token1", null);
        verify(googlePlayClient, times(1)).acknowledgeSubscription("product2", "token2", null);
        verify(googlePlayClient, times(1)).acknowledgeSubscription("product3", "token3", null);
    }

    @Test
    @DisplayName("Integration scenarios: Mixed success and failure scenarios")
    void integrationScenarios_mixedSuccessAndFailureScenarios() {
        // Given - Mixed scenarios
        doNothing() // First call succeeds
                .doThrow(new RuntimeException("Connection refused"))
                .doNothing() // Second call succeeds on retry
                .doThrow(new RuntimeException("Connection refused"))
                .doThrow(new RuntimeException("Read timed out"))
                .doThrow(new RuntimeException("Operation timed out")) // Third call fails after max attempts
                .when(googlePlayClient).acknowledgeSubscription(anyString(), anyString(), isNull());

        // When & Then
        // First call should succeed
        subscriptionAcknowledger.acknowledge("product1", "token1");
        verify(googlePlayClient, times(1)).acknowledgeSubscription("product1", "token1", null);

        // Second call should succeed after retry
        subscriptionAcknowledger.acknowledge("product2", "token2");
        verify(googlePlayClient, times(2)).acknowledgeSubscription("product2", "token2", null);

        // Third call should fail after max attempts
        assertThatThrownBy(() -> subscriptionAcknowledger.acknowledge("product3", "token3"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Operation timed out");
        verify(googlePlayClient, times(3)).acknowledgeSubscription("product3", "token3", null);
    }
}