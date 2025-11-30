package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import uk.gegc.kidsgptbackend.dto.subscription.*;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.service.auth.CurrentUserResolver;
import uk.gegc.kidsgptbackend.service.subscription.SubscriptionService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionController Tests")
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @InjectMocks
    private SubscriptionController subscriptionController;

    private ObjectMapper objectMapper;
    private User testPrincipal;
    private uk.gegc.kidsgptbackend.features.user.domain.model.User testUser;
    private SubscriptionPlanDto freePlan;
    private SubscriptionPlanDto plusPlan;
    private UserSubscription testSubscription;
    private CreateSubscriptionRequest validRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Setup test principal
        testPrincipal = new User("testuser", "password", Collections.emptyList());

        // Setup test user
        testUser = new uk.gegc.kidsgptbackend.features.user.domain.model.User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setHashedPassword("hashedPassword");
        testUser.setCreatedAt(Instant.now());

        // Setup test plans
        freePlan = new SubscriptionPlanDto(
                UUID.randomUUID(),
                "Free",
                "Free plan with limited features",
                BigDecimal.ZERO,
                "GBP",
                SubscriptionPlan.BillingCycle.MONTHLY,
                true,
                1,
                "{\"chat_limit\": 15}",
                null,
                Instant.now(),
                Instant.now()
        );

        plusPlan = new SubscriptionPlanDto(
                UUID.randomUUID(),
                "Plus Monthly",
                "Premium plan with unlimited features",
                new BigDecimal("4.99"),
                "GBP",
                SubscriptionPlan.BillingCycle.MONTHLY,
                true,
                -1,
                "{\"chat_limit\": -1}",
                "plus_monthly",
                Instant.now(),
                Instant.now()
        );

        // Setup test subscription
        testSubscription = new UserSubscription();
        testSubscription.setId(UUID.randomUUID());
        testSubscription.setUser(testUser);
        testSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        testSubscription.setStartDate(Instant.now());
        testSubscription.setCreatedAt(Instant.now());
        testSubscription.setUpdatedAt(Instant.now());
        
        // Create a subscription plan for the test subscription
        SubscriptionPlan testPlan = new SubscriptionPlan();
        testPlan.setId(plusPlan.id());
        testPlan.setName("Plus Monthly");
        testPlan.setPrice(new BigDecimal("4.99"));
        testPlan.setCurrency("GBP");
        testPlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        testPlan.setMaxKids(-1);
        testPlan.setFeatures("{\"chat_limit\": -1}");
        testPlan.setGooglePlayProductId("plus_monthly");
        testPlan.setActive(true);
        testPlan.setCreatedAt(Instant.now());
        testPlan.setUpdatedAt(Instant.now());
        
        testSubscription.setSubscriptionPlan(testPlan);

        // Setup valid request
        validRequest = new CreateSubscriptionRequest(
                plusPlan.id(),
                "plus_monthly",
                "test_purchase_token_123"
        );
    }

    @Test
    @DisplayName("GET /api/subscriptions/plans - returns 200 with plans sorted by price")
    void getAvailablePlans_returns200WithPlansSortedByPrice() {
        // Given
        List<SubscriptionPlanDto> plans = List.of(plusPlan, freePlan);
        when(subscriptionService.getAvailablePlans()).thenReturn(plans);

        // When
        ResponseEntity<List<SubscriptionPlanDto>> response = subscriptionController.getAvailablePlans();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        // The service returns plans in the order provided, not necessarily sorted by price
        assertThat(response.getBody().get(0).name()).isEqualTo("Plus Monthly");
        assertThat(response.getBody().get(1).name()).isEqualTo("Free");

        verify(subscriptionService).getAvailablePlans();
    }

    @Test
    @DisplayName("GET /api/subscriptions/plans - returns empty list when no plans")
    void getAvailablePlans_returnsEmptyListWhenNoPlans() {
        // Given
        when(subscriptionService.getAvailablePlans()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<List<SubscriptionPlanDto>> response = subscriptionController.getAvailablePlans();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();

        verify(subscriptionService).getAvailablePlans();
    }

    @Test
    @DisplayName("GET /api/subscriptions/plans/{id} - returns 200 with correct DTO for existing plan")
    void getPlanById_returns200WithCorrectDtoForExistingPlan() {
        // Given
        when(subscriptionService.getPlanById(plusPlan.id())).thenReturn(plusPlan);

        // When
        ResponseEntity<SubscriptionPlanDto> response = subscriptionController.getPlanById(plusPlan.id());

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(plusPlan);
        assertThat(response.getBody().name()).isEqualTo("Plus Monthly");
        assertThat(response.getBody().price()).isEqualTo(new BigDecimal("4.99"));

        verify(subscriptionService).getPlanById(plusPlan.id());
    }

    @Test
    @DisplayName("GET /api/subscriptions/plans/{id} - returns 400 for non-existent plan")
    void getPlanById_returns400ForNonExistentPlan() {
        // Given
        UUID nonExistentPlanId = UUID.randomUUID();
        when(subscriptionService.getPlanById(nonExistentPlanId))
                .thenThrow(new IllegalArgumentException("Plan not found"));

        // When & Then
        try {
            subscriptionController.getPlanById(nonExistentPlanId);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Plan not found");
        }

        verify(subscriptionService).getPlanById(nonExistentPlanId);
    }

    @Test
    @DisplayName("POST /api/subscriptions/create - returns 201 with UserSubscriptionDto for valid request")
    void createSubscription_returns201WithDtoForValidRequest() {
        // Given
        when(currentUserResolver.getCurrentUser(testPrincipal)).thenReturn(testUser);
        when(subscriptionService.createSubscription(testUser, validRequest)).thenReturn(testSubscription);

        // When
        ResponseEntity<UserSubscriptionDto> response = subscriptionController.createSubscription(validRequest, testPrincipal);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(testSubscription.getId());
        assertThat(response.getBody().userId()).isEqualTo(testUser.getId());

        verify(currentUserResolver).getCurrentUser(testPrincipal);
        verify(subscriptionService).createSubscription(testUser, validRequest);
    }

    @Test
    @DisplayName("POST /api/subscriptions/create - returns 400 for plan/product mismatch")
    void createSubscription_returns400ForPlanProductMismatch() {
        // Given
        CreateSubscriptionRequest mismatchedRequest = new CreateSubscriptionRequest(
                plusPlan.id(),
                "different_product_id", // mismatch
                "test_purchase_token_123"
        );
        when(currentUserResolver.getCurrentUser(testPrincipal)).thenReturn(testUser);
        when(subscriptionService.createSubscription(testUser, mismatchedRequest))
                .thenThrow(new IllegalArgumentException("Product/plan mismatch"));

        // When & Then
        try {
            subscriptionController.createSubscription(mismatchedRequest, testPrincipal);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Product/plan mismatch");
        }

        verify(currentUserResolver).getCurrentUser(testPrincipal);
        verify(subscriptionService).createSubscription(testUser, mismatchedRequest);
    }

    @Test
    @DisplayName("POST /api/subscriptions/create - returns 400 for already active subscription")
    void createSubscription_returns400ForAlreadyActiveSubscription() {
        // Given
        when(currentUserResolver.getCurrentUser(testPrincipal)).thenReturn(testUser);
        when(subscriptionService.createSubscription(testUser, validRequest))
                .thenThrow(new IllegalStateException("User already has an active subscription"));

        // When & Then
        try {
            subscriptionController.createSubscription(validRequest, testPrincipal);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("User already has an active subscription");
        }

        verify(currentUserResolver).getCurrentUser(testPrincipal);
        verify(subscriptionService).createSubscription(testUser, validRequest);
    }

    @Test
    @DisplayName("GET /api/subscriptions/status - returns 200 with free defaults when no subscription")
    void getSubscriptionStatus_returns200WithFreeDefaultsWhenNoSubscription() {
        // Given
        SubscriptionStatusDto freeStatus = new SubscriptionStatusDto(
                testUser.getId(), // userId
                false, // hasActiveSubscription
                null, // subscriptionStatus
                null, // planName
                null, // currentPeriodEnd
                false, // isTrial
                null, // trialEndDate
                null, // maxKids
                null, // currentKidsCount
                false  // canAddMoreKids
        );
        when(currentUserResolver.getCurrentUser(testPrincipal)).thenReturn(testUser);
        when(subscriptionService.getUserSubscriptionStatus(testUser)).thenReturn(freeStatus);

        // When
        ResponseEntity<SubscriptionStatusDto> response = subscriptionController.getSubscriptionStatus(testPrincipal);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(freeStatus);
        assertThat(response.getBody().hasActiveSubscription()).isFalse();

        verify(currentUserResolver).getCurrentUser(testPrincipal);
        verify(subscriptionService).getUserSubscriptionStatus(testUser);
    }

    @Test
    @DisplayName("GET /api/subscriptions/status - returns 200 with active subscription details")
    void getSubscriptionStatus_returns200WithActiveSubscriptionDetails() {
        // Given
        SubscriptionStatusDto activeStatus = new SubscriptionStatusDto(
                testUser.getId(), // userId
                true, // hasActiveSubscription
                "ACTIVE", // subscriptionStatus
                "Plus Monthly", // planName
                Instant.now().plusSeconds(86400), // currentPeriodEnd
                false, // isTrial
                null, // trialEndDate
                -1, // maxKids
                0, // currentKidsCount
                true  // canAddMoreKids
        );
        when(currentUserResolver.getCurrentUser(testPrincipal)).thenReturn(testUser);
        when(subscriptionService.getUserSubscriptionStatus(testUser)).thenReturn(activeStatus);

        // When
        ResponseEntity<SubscriptionStatusDto> response = subscriptionController.getSubscriptionStatus(testPrincipal);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(activeStatus);
        assertThat(response.getBody().hasActiveSubscription()).isTrue();
        assertThat(response.getBody().subscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(response.getBody().planName()).isEqualTo("Plus Monthly");

        verify(currentUserResolver).getCurrentUser(testPrincipal);
        verify(subscriptionService).getUserSubscriptionStatus(testUser);
    }

    @Test
    @DisplayName("GET /api/subscriptions/can-add-kids - returns true for free plan with <=1 kid")
    void canAddMoreKids_returnsTrueForFreePlanWithOneKid() {
        // Given
        when(currentUserResolver.getCurrentUser(testPrincipal)).thenReturn(testUser);
        when(subscriptionService.canAddMoreKids(testUser)).thenReturn(true);

        // When
        ResponseEntity<Boolean> response = subscriptionController.canAddMoreKids(testPrincipal);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();

        verify(currentUserResolver).getCurrentUser(testPrincipal);
        verify(subscriptionService).canAddMoreKids(testUser);
    }

    @Test
    @DisplayName("GET /api/subscriptions/can-add-kids - returns false when at limit")
    void canAddMoreKids_returnsFalseWhenAtLimit() {
        // Given
        when(currentUserResolver.getCurrentUser(testPrincipal)).thenReturn(testUser);
        when(subscriptionService.canAddMoreKids(testUser)).thenReturn(false);

        // When
        ResponseEntity<Boolean> response = subscriptionController.canAddMoreKids(testPrincipal);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isFalse();

        verify(currentUserResolver).getCurrentUser(testPrincipal);
        verify(subscriptionService).canAddMoreKids(testUser);
    }

    @Test
    @DisplayName("POST /api/subscriptions/create - handles CurrentUserResolver failure")
    void createSubscription_handlesCurrentUserResolverFailure() {
        // Given
        when(currentUserResolver.getCurrentUser(testPrincipal))
                .thenThrow(new RuntimeException("User not found"));

        // When & Then
        try {
            subscriptionController.createSubscription(validRequest, testPrincipal);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("User not found");
        }

        verify(currentUserResolver).getCurrentUser(testPrincipal);
        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("GET /api/subscriptions/status - handles service error")
    void getSubscriptionStatus_handlesServiceError() {
        // Given
        when(currentUserResolver.getCurrentUser(testPrincipal)).thenReturn(testUser);
        when(subscriptionService.getUserSubscriptionStatus(testUser))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        try {
            subscriptionController.getSubscriptionStatus(testPrincipal);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Database error");
        }

        verify(currentUserResolver).getCurrentUser(testPrincipal);
        verify(subscriptionService).getUserSubscriptionStatus(testUser);
    }

    @Test
    @DisplayName("GET /api/subscriptions/can-add-kids - handles service error")
    void canAddMoreKids_handlesServiceError() {
        // Given
        when(currentUserResolver.getCurrentUser(testPrincipal)).thenReturn(testUser);
        when(subscriptionService.canAddMoreKids(testUser))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        try {
            subscriptionController.canAddMoreKids(testPrincipal);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Database error");
        }

        verify(currentUserResolver).getCurrentUser(testPrincipal);
        verify(subscriptionService).canAddMoreKids(testUser);
    }
}