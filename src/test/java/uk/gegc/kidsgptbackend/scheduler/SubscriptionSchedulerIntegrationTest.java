package uk.gegc.kidsgptbackend.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gegc.kidsgptbackend.features.subscription.application.SubscriptionService;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionScheduler Unit Tests")
class SubscriptionSchedulerIntegrationTest {

    @Mock
    private SubscriptionService subscriptionService;

    private SubscriptionScheduler subscriptionScheduler;

    @BeforeEach
    void setUp() {
        // Create scheduler instance with mocked service
        subscriptionScheduler = new SubscriptionScheduler(subscriptionService);
        // Reset mock interactions before each test
        clearInvocations(subscriptionService);
    }

    @Test
    @DisplayName("processExpiredSubscriptions - manual trigger calls service method")
    void processExpiredSubscriptions_manualTriggerCallsServiceMethod() {
        // Given - mock is already set up

        // When - manually trigger the scheduled method
        subscriptionScheduler.processExpiredSubscriptions();

        // Then - verify service method was called
        verify(subscriptionService, times(1)).processExpiredSubscriptions();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("reconcileSubscriptions - manual trigger calls reconcileWithGooglePlay")
    void reconcileSubscriptions_manualTriggerCallsReconcileWithGooglePlay() {
        // Given - mock is already set up

        // When - manually trigger the scheduled method
        subscriptionScheduler.reconcileSubscriptions();

        // Then - verify service method was called
        verify(subscriptionService, times(1)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("cleanupExpiredUsagePeriods - manual trigger executes without service calls")
    void cleanupExpiredUsagePeriods_manualTriggerExecutesWithoutServiceCalls() {
        // Given - mock is already set up

        // When - manually trigger the scheduled method
        subscriptionScheduler.cleanupExpiredUsagePeriods();

        // Then - verify no service interactions (cleanup is internal)
        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("All scheduler methods can be triggered manually multiple times")
    void allSchedulerMethods_canBeTriggeredManuallyMultipleTimes() {
        // Given - mock is already set up

        // When - trigger each method multiple times
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
    @DisplayName("Scheduler methods handle exceptions without affecting other methods")
    void schedulerMethods_handleExceptionsWithoutAffectingOtherMethods() {
        // Given - make one service method throw exception
        doThrow(new RuntimeException("Simulated service failure"))
                .when(subscriptionService).processExpiredSubscriptions();

        // When - call all methods
        subscriptionScheduler.processExpiredSubscriptions(); // Should handle exception
        subscriptionScheduler.reconcileSubscriptions(); // Should work normally
        subscriptionScheduler.cleanupExpiredUsagePeriods(); // Should work normally

        // Then - verify all methods were called despite exception
        verify(subscriptionService, times(1)).processExpiredSubscriptions();
        verify(subscriptionService, times(1)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("Scheduler methods are resilient to service failures")
    void schedulerMethods_areResilientToServiceFailures() {
        // Given - make service methods throw different exceptions
        doThrow(new RuntimeException("Process expired failure"))
                .when(subscriptionService).processExpiredSubscriptions();
        
        doThrow(new IllegalStateException("Reconcile failure"))
                .when(subscriptionService).reconcileWithGooglePlay();

        // When - call methods that will fail
        subscriptionScheduler.processExpiredSubscriptions();
        subscriptionScheduler.reconcileSubscriptions();
        subscriptionScheduler.cleanupExpiredUsagePeriods(); // This should still work

        // Then - verify all methods were called despite failures
        verify(subscriptionService, times(1)).processExpiredSubscriptions();
        verify(subscriptionService, times(1)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("Scheduler methods can be called in any order")
    void schedulerMethods_canBeCalledInAnyOrder() {
        // Given - spy is already set up

        // When - call methods in different order
        subscriptionScheduler.cleanupExpiredUsagePeriods();
        subscriptionScheduler.reconcileSubscriptions();
        subscriptionScheduler.processExpiredSubscriptions();
        subscriptionScheduler.cleanupExpiredUsagePeriods();
        subscriptionScheduler.processExpiredSubscriptions();
        subscriptionScheduler.reconcileSubscriptions();

        // Then - verify all calls were made
        verify(subscriptionService, times(2)).processExpiredSubscriptions();
        verify(subscriptionService, times(2)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }

    @Test
    @DisplayName("Scheduler methods maintain state consistency across calls")
    void schedulerMethods_maintainStateConsistencyAcrossCalls() {
        // Given - spy is already set up

        // When - make multiple calls to verify state consistency
        for (int i = 0; i < 3; i++) {
            subscriptionScheduler.processExpiredSubscriptions();
            subscriptionScheduler.reconcileSubscriptions();
            subscriptionScheduler.cleanupExpiredUsagePeriods();
        }

        // Then - verify consistent behavior across calls
        verify(subscriptionService, times(3)).processExpiredSubscriptions();
        verify(subscriptionService, times(3)).reconcileWithGooglePlay();
        verifyNoMoreInteractions(subscriptionService);
    }
}
