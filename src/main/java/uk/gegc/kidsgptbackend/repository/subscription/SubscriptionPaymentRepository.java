package uk.gegc.kidsgptbackend.repository.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPayment;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {

    List<SubscriptionPayment> findByUserSubscriptionOrderByCreatedAtDesc(UserSubscription userSubscription);

    Optional<SubscriptionPayment> findByExternalPaymentId(String externalPaymentId);

    @Query("SELECT sp FROM SubscriptionPayment sp WHERE sp.userSubscription = :subscription AND sp.status = 'SUCCEEDED' ORDER BY sp.createdAt DESC")
    List<SubscriptionPayment> findSuccessfulPaymentsBySubscription(@Param("subscription") UserSubscription subscription);

    @Query("SELECT sp FROM SubscriptionPayment sp WHERE sp.userSubscription = :subscription AND sp.status = 'SUCCEEDED' AND sp.billingPeriodStart >= :startDate AND sp.billingPeriodEnd <= :endDate")
    List<SubscriptionPayment> findPaymentsInBillingPeriod(@Param("subscription") UserSubscription subscription,
                                                         @Param("startDate") Instant startDate,
                                                         @Param("endDate") Instant endDate);

    @Query("SELECT sp FROM SubscriptionPayment sp WHERE sp.paymentProvider = :provider AND sp.externalPaymentId = :externalId")
    Optional<SubscriptionPayment> findByPaymentProviderAndExternalId(@Param("provider") SubscriptionPayment.PaymentProvider provider,
                                                                    @Param("externalId") String externalId);

    @Query("SELECT sp FROM SubscriptionPayment sp WHERE sp.status = 'PENDING' AND sp.createdAt < :cutoffTime")
    List<SubscriptionPayment> findPendingPaymentsOlderThan(@Param("cutoffTime") Instant cutoffTime);
}
