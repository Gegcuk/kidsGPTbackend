package uk.gegc.kidsgptbackend.model.subscription;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "webhook_events", indexes = {
    @Index(name = "idx_webhook_provider_event", columnList = "payment_provider,external_event_id", unique = true),
    @Index(name = "idx_webhook_created", columnList = "created_at")
})
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false)
    private PaymentProvider paymentProvider;

    @Column(name = "external_event_id", nullable = false)
    private String externalEventId; // Google Play RTDN delivery ID for idempotency

    @Column(name = "event_type", nullable = false)
    private String eventType; // e.g., "customer.subscription.updated"

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload; // Raw webhook payload for debugging

    @Column(name = "processing_error")
    private String processingError;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public enum PaymentProvider {
        GOOGLE_PLAY
    }
}
