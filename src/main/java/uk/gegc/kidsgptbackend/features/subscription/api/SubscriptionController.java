package uk.gegc.kidsgptbackend.features.subscription.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uk.gegc.kidsgptbackend.features.subscription.api.dto.*;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.auth.application.CurrentUserResolver;
import uk.gegc.kidsgptbackend.features.subscription.application.SubscriptionService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Subscription Management", description = "APIs for managing user subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/plans")
    @Operation(summary = "Get available subscription plans", description = "Retrieve all active subscription plans")
    public ResponseEntity<List<SubscriptionPlanDto>> getAvailablePlans() {
        List<SubscriptionPlanDto> plans = subscriptionService.getAvailablePlans();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/plans/{planId}")
    @Operation(summary = "Get subscription plan by ID", description = "Retrieve a specific subscription plan")
    public ResponseEntity<SubscriptionPlanDto> getPlanById(@PathVariable UUID planId) {
        SubscriptionPlanDto plan = subscriptionService.getPlanById(planId);
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new subscription", description = "Create a new subscription for the authenticated user")
    public ResponseEntity<UserSubscriptionDto> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User user = currentUserResolver.getCurrentUser(principal);
            UserSubscription savedSubscription = subscriptionService.createSubscription(user, request);
            
            // Map the saved subscription to DTO
            UserSubscriptionDto dto = mapToUserSubscriptionDto(savedSubscription);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (Exception e) {
            log.error("Error creating subscription for user {}", principal.getUsername(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/status")
    @Operation(summary = "Get user subscription status", description = "Get the current subscription status for the authenticated user")
    public ResponseEntity<SubscriptionStatusDto> getSubscriptionStatus(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User user = currentUserResolver.getCurrentUser(principal);
            SubscriptionStatusDto status = subscriptionService.getUserSubscriptionStatus(user);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting subscription status for user {}", principal.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/history")
    @Operation(summary = "Get subscription history", description = "Get the subscription history for the authenticated user")
    public ResponseEntity<List<UserSubscriptionDto>> getSubscriptionHistory(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User user = currentUserResolver.getCurrentUser(principal);
            List<UserSubscriptionDto> history = subscriptionService.getUserSubscriptionHistory(user);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error getting subscription history for user {}", principal.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel subscription", description = "Cancel the current subscription for the authenticated user")
    public ResponseEntity<UserSubscriptionDto> cancelSubscription(
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User user = currentUserResolver.getCurrentUser(principal);
            UserSubscriptionDto cancelledSubscription = subscriptionService.cancelSubscription(user, reason);
            return ResponseEntity.ok(cancelledSubscription);
        } catch (IllegalStateException e) {
            log.error("Error cancelling subscription for user {}: {}", principal.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error cancelling subscription for user {}", principal.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/reactivate")
    @Operation(summary = "Reactivate subscription", description = "Reactivate a cancelled subscription for the authenticated user")
    public ResponseEntity<UserSubscriptionDto> reactivateSubscription(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User user = currentUserResolver.getCurrentUser(principal);
            UserSubscriptionDto reactivatedSubscription = subscriptionService.reactivateSubscription(user);
            return ResponseEntity.ok(reactivatedSubscription);
        } catch (IllegalStateException e) {
            log.error("Error reactivating subscription for user {}: {}", principal.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error reactivating subscription for user {}", principal.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/can-add-kids")
    @Operation(summary = "Check if user can add more kids", description = "Check if the user can add more kids based on their subscription")
    public ResponseEntity<Boolean> canAddMoreKids(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User user = currentUserResolver.getCurrentUser(principal);
            boolean canAdd = subscriptionService.canAddMoreKids(user);
            return ResponseEntity.ok(canAdd);
        } catch (Exception e) {
            log.error("Error checking kid limit for user {}", principal.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private UserSubscriptionDto mapToUserSubscriptionDto(UserSubscription subscription) {
        return new UserSubscriptionDto(
                subscription.getId(),
                subscription.getUser().getId(),
                subscription.getSubscriptionPlan().getId(),
                subscription.getSubscriptionPlan().getName(),
                subscription.getStatus(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                subscription.getCurrentPeriodEnd(), // nextBillingDate = currentPeriodEnd for auto-renewing
                subscription.getCancelledAt(),
                null, // cancellationReason - not stored in entity
                subscription.getPaymentProvider(),
                subscription.getExternalSubscriptionId(),
                subscription.getTrialEndDate(),
                subscription.getTrialEndDate() != null && subscription.getTrialEndDate().isAfter(Instant.now()),
                subscription.isAutoRenew(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
