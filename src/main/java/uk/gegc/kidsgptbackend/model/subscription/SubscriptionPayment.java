package uk.gegc.kidsgptbackend.model.subscription;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "subscription_payments", indexes = {
    @Index(name = "idx_payment_external_id", columnList = "external_payment_id", unique = true),
    @Index(name = "idx_payment_subscription_created", columnList = "subscription_id,created_at")
})
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private UserSubscription userSubscription;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false)
    private PaymentProvider paymentProvider;

    @Column(name = "external_payment_id", nullable = false, unique = true)
    private String externalPaymentId;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    @Column(name = "invoice_id")
    private String invoiceId; // Stripe invoice ID or Google Play order ID

    @Column(name = "receipt_json", columnDefinition = "TEXT")
    private String receiptJson; // Raw provider receipt

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "billing_period_start")
    private Instant billingPeriodStart;

    @Column(name = "billing_period_end")
    private Instant billingPeriodEnd;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason")
    private String refundReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum PaymentProvider {
        GOOGLE_PLAY
    }

    public enum PaymentStatus {
        PENDING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        REFUNDED,
        PARTIALLY_REFUNDED
    }
}
