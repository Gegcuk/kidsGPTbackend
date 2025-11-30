package uk.gegc.kidsgptbackend.model.subscription;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_subscriptions", indexes = {
    @Index(name = "idx_user_subscription_status", columnList = "user_id,status"),
    @Index(name = "idx_subscription_provider_external", columnList = "payment_provider,external_subscription_id", unique = true),
    @Index(name = "idx_subscription_external_customer", columnList = "external_customer_id")
})
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "subscription_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "next_billing_date")
    private Instant nextBillingDate;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart; // From provider

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd; // From provider

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "grace_period_end")
    private Instant gracePeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider")
    private PaymentProvider paymentProvider;

    @Column(name = "external_subscription_id", nullable = false)
    private String externalSubscriptionId; // ID from payment provider

    @Column(name = "external_customer_id")
    private String externalCustomerId; // Customer ID from payment provider

    @Column(name = "provider_status_raw")
    private String providerStatusRaw; // Raw status from provider for debugging

    @Column(name = "trial_end_date")
    private Instant trialEndDate;

    @Column(name = "is_trial", nullable = false)
    private boolean isTrial = false;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum PaymentProvider {
        GOOGLE_PLAY
    }

    public enum SubscriptionStatus {
        ACTIVE,
        CANCELLED,
        EXPIRED,
        PAST_DUE,
        TRIALING,
        INCOMPLETE,
        INCOMPLETE_EXPIRED,
        UNPAID
    }

    // Helper methods
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING;
    }

    public boolean isExpired() {
        return currentPeriodEnd != null && currentPeriodEnd.isBefore(Instant.now());
    }

    public boolean isInTrial() {
        return isTrial && trialEndDate != null && trialEndDate.isAfter(Instant.now());
    }
}
