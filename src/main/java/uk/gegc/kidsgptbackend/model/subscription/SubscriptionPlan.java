package uk.gegc.kidsgptbackend.model.subscription;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "plan_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "max_kids", nullable = false)
    private Integer maxKids = 1;

    @Column(name = "features", columnDefinition = "TEXT")
    private String features; // JSON string of features

    @Column(name = "google_play_product_id", unique = true)
    private String googlePlayProductId; // Nullable for free plans

    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "USD";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "subscriptionPlan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserSubscription> userSubscriptions;

    public enum BillingCycle {
        MONTHLY,
        YEARLY
    }
}
