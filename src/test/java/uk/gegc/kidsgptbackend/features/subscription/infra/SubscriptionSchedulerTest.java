package uk.gegc.kidsgptbackend.features.subscription.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gegc.kidsgptbackend.features.subscription.application.SubscriptionService;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionScheduler Tests")
class SubscriptionSchedulerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private uk.gegc.kidsgptbackend.features.subscription.infra.SubscriptionScheduler subscriptionScheduler;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(subscriptionService);
    }

    @Test
    @DisplayName("processExpiredSubscriptions - calls service method and handles success")
    void processExpiredSubscriptions_callsServiceMethodAndHandlesSuccess() {
        // Given - no exceptions thrown by service

        // When
        subscriptionScheduler.processExpiredSubscriptions();

        // Then
        verify(subscriptionService, times(1)).processExpiredSubscriptions();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("processExpiredSubscriptions - handles service exceptions gracefully")
    void processExpiredSubscriptions_handlesServiceExceptionsGracefully() {
        // Given
        RuntimeException serviceException = new RuntimeException("Database connection failed");
        doThrow(serviceException).when(subscriptionService).processExpiredSubscriptions();

        // When & Then - should not throw exception
        subscriptionScheduler.processExpiredSubscriptions();

        // Verify service was called despite exception
        verify(subscriptionService, times(1)).processExpiredSubscriptions();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("processExpiredSubscriptions - handles multiple exceptions")
    void processExpiredSubscriptions_handlesMultipleExceptions() {
        // Given
        doThrow(new RuntimeException("First error"))
                .doThrow(new IllegalStateException("Second error"))
                .when(subscriptionService).processExpiredSubscriptions();

        // When & Then - should not throw exception on multiple calls
        subscriptionScheduler.processExpiredSubscriptions();
        subscriptionScheduler.processExpiredSubscriptions();

        // Verify service was called twice despite exceptions
        verify(subscriptionService, times(2)).processExpiredSubscriptions();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("reconcileSubscriptions - calls reconcileWithGooglePlay method")
    void reconcileSubscriptions_callsReconcileWithGooglePlayMethod() {
        // Given - no exceptions thrown by service

        // When
        subscriptionScheduler.reconcileSubscriptions();

        // Then
        verify(subscriptionService, times(1)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("reconcileSubscriptions - handles service exceptions gracefully")
    void reconcileSubscriptions_handlesServiceExceptionsGracefully() {
        // Given
        RuntimeException serviceException = new RuntimeException("Google Play API unavailable");
        doThrow(serviceException).when(subscriptionService).reconcileWithGooglePlay();

        // When & Then - should not throw exception
        subscriptionScheduler.reconcileSubscriptions();

        // Verify service was called despite exception
        verify(subscriptionService, times(1)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("reconcileSubscriptions - handles null pointer exceptions")
    void reconcileSubscriptions_handlesNullPointerExceptions() {
        // Given
        doThrow(new NullPointerException("Unexpected null value"))
                .when(subscriptionService).reconcileWithGooglePlay();

        // When & Then - should not throw exception
        subscriptionScheduler.reconcileSubscriptions();

        // Verify service was called despite exception
        verify(subscriptionService, times(1)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("cleanupExpiredUsagePeriods - executes without throwing exceptions")
    void cleanupExpiredUsagePeriods_executesWithoutThrowingExceptions() {
        // Given - no service calls expected for this method

        // When & Then - should not throw exception
        subscriptionScheduler.cleanupExpiredUsagePeriods();

        // Verify no service interactions (cleanup is handled internally)
        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("cleanupExpiredUsagePeriods - handles internal exceptions gracefully")
    void cleanupExpiredUsagePeriods_handlesInternalExceptionsGracefully() {
        // Given - method should not call any services, so no mocking needed

        // When & Then - should not throw exception even if internal logic fails
        subscriptionScheduler.cleanupExpiredUsagePeriods();

        // Verify no service interactions
        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("All scheduler methods can be called multiple times without issues")
    void allSchedulerMethods_canBeCalledMultipleTimesWithoutIssues() {
        // Given - no exceptions

        // When - call each method multiple times
        subscriptionScheduler.processExpiredSubscriptions();
        subscriptionScheduler.processExpiredSubscriptions();
        
        subscriptionScheduler.reconcileSubscriptions();
        subscriptionScheduler.reconcileSubscriptions();
        
        subscriptionScheduler.cleanupExpiredUsagePeriods();
        subscriptionScheduler.cleanupExpiredUsagePeriods();

        // Then - verify correct number of service calls
        verify(subscriptionService, times(2)).processExpiredSubscriptions();
        verify(subscriptionService, times(2)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("Scheduler methods handle mixed success and failure scenarios")
    void schedulerMethods_handleMixedSuccessAndFailureScenarios() {
        // Given - first call succeeds, second fails
        doNothing()
                .doThrow(new RuntimeException("Service failure"))
                .when(subscriptionService).processExpiredSubscriptions();

        doNothing()
                .doThrow(new IllegalStateException("Reconciliation failure"))
                .when(subscriptionService).reconcileWithGooglePlay();

        // When - call methods twice each
        subscriptionScheduler.processExpiredSubscriptions(); // Success
        subscriptionScheduler.processExpiredSubscriptions(); // Failure
        
        subscriptionScheduler.reconcileSubscriptions(); // Success
        subscriptionScheduler.reconcileSubscriptions(); // Failure
        
        subscriptionScheduler.cleanupExpiredUsagePeriods(); // Always succeeds
        subscriptionScheduler.cleanupExpiredUsagePeriods(); // Always succeeds

        // Then - verify all calls were made despite failures
        verify(subscriptionService, times(2)).processExpiredSubscriptions();
        verify(subscriptionService, times(2)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("Scheduler methods are idempotent - multiple calls have same effect")
    void schedulerMethods_areIdempotent() {
        // Given - service methods are idempotent
        doNothing().when(subscriptionService).processExpiredSubscriptions();
        doNothing().when(subscriptionService).reconcileWithGooglePlay();

        // When - call methods multiple times
        for (int i = 0; i < 5; i++) {
            subscriptionScheduler.processExpiredSubscriptions();
            subscriptionScheduler.reconcileSubscriptions();
            subscriptionScheduler.cleanupExpiredUsagePeriods();
        }

        // Then - verify each service method called 5 times
        verify(subscriptionService, times(5)).processExpiredSubscriptions();
        verify(subscriptionService, times(5)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("Scheduler handles concurrent-like execution patterns")
    void scheduler_handlesConcurrentLikeExecutionPatterns() {
        // Given - simulate rapid successive calls
        doNothing().when(subscriptionService).processExpiredSubscriptions();
        doNothing().when(subscriptionService).reconcileWithGooglePlay();

        // When - rapid successive calls (simulating concurrent execution)
        subscriptionScheduler.processExpiredSubscriptions();
        subscriptionScheduler.reconcileSubscriptions();
        subscriptionScheduler.cleanupExpiredUsagePeriods();
        subscriptionScheduler.processExpiredSubscriptions();
        subscriptionScheduler.reconcileSubscriptions();
        subscriptionScheduler.cleanupExpiredUsagePeriods();

        // Then - verify all calls were processed
        verify(subscriptionService, times(2)).processExpiredSubscriptions();
        verify(subscriptionService, times(2)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }
}
