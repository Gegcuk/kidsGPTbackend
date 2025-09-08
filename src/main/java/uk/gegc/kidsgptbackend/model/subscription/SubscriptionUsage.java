package uk.gegc.kidsgptbackend.model.subscription;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import uk.gegc.kidsgptbackend.model.user.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "subscription_usage", indexes = {
    @Index(name = "idx_usage_user_feature_period", columnList = "user_id,feature,period_key", unique = true),
    @Index(name = "idx_usage_period", columnList = "period_key")
})
public class SubscriptionUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "feature", nullable = false)
    private String feature; // e.g., "chat_limit", "image_generation"

    @Column(name = "period_key", nullable = false)
    private String periodKey; // e.g., "2025-09" for monthly, or provider period ID

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Column(name = "limit_count")
    private Integer limitCount; // Cache the limit for this period

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Helper methods
    public boolean hasReachedLimit() {
        return limitCount != null && limitCount > 0 && usedCount >= limitCount;
    }

    public int getRemainingUsage() {
        if (limitCount == null || limitCount == -1) {
            return Integer.MAX_VALUE; // Unlimited
        }
        return Math.max(0, limitCount - usedCount);
    }
}
