package uk.gegc.kidsgptbackend.features.subscription.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {

    Optional<UserSubscription> findByUserAndStatusIn(User user, List<UserSubscription.SubscriptionStatus> statuses);

    @Query("SELECT us FROM UserSubscription us WHERE us.user = :user AND us.status IN :statuses ORDER BY us.createdAt DESC")
    List<UserSubscription> findByUserAndStatusInOrderByCreatedAtDesc(@Param("user") User user, 
                                                                     @Param("statuses") List<UserSubscription.SubscriptionStatus> statuses);

    @Query("SELECT us FROM UserSubscription us WHERE us.user = :user AND us.status = 'ACTIVE'")
    Optional<UserSubscription> findActiveSubscriptionByUser(@Param("user") User user);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT us FROM UserSubscription us WHERE us.user = :user AND us.status IN ('ACTIVE', 'TRIALING')")
    List<UserSubscription> findActiveSubscriptionsWithLock(@Param("user") User user);

    @Query("SELECT us FROM UserSubscription us WHERE us.status = 'ACTIVE' AND us.currentPeriodEnd < :now")
    List<UserSubscription> findExpiredActiveSubscriptions(@Param("now") Instant now);

    @Query("SELECT us FROM UserSubscription us WHERE us.status = 'ACTIVE' AND us.nextBillingDate <= :now")
    List<UserSubscription> findSubscriptionsDueForBilling(@Param("now") Instant now);

    @Query("SELECT us FROM UserSubscription us WHERE us.status = 'TRIALING' AND us.trialEndDate <= :now")
    List<UserSubscription> findExpiredTrialSubscriptions(@Param("now") Instant now);

    @Query("SELECT COUNT(us) FROM UserSubscription us WHERE us.user = :user AND us.status IN ('ACTIVE', 'TRIALING')")
    long countActiveSubscriptionsByUser(@Param("user") User user);

    @Query("SELECT us FROM UserSubscription us WHERE us.paymentProvider = :provider AND us.externalSubscriptionId = :externalId")
    Optional<UserSubscription> findByPaymentProviderAndExternalSubscriptionId(@Param("provider") UserSubscription.PaymentProvider provider, 
                                                                              @Param("externalId") String externalId);
}
