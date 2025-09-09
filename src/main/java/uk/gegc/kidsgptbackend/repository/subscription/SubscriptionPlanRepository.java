package uk.gegc.kidsgptbackend.repository.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    List<SubscriptionPlan> findByIsActiveTrueOrderByPriceAsc();

    Optional<SubscriptionPlan> findByGooglePlayProductId(String googlePlayProductId);

    @Query("SELECT sp FROM SubscriptionPlan sp WHERE sp.isActive = true AND sp.billingCycle = :billingCycle")
    List<SubscriptionPlan> findActivePlansByBillingCycle(@Param("billingCycle") SubscriptionPlan.BillingCycle billingCycle);

    @Query("SELECT sp FROM SubscriptionPlan sp WHERE sp.isActive = true AND sp.maxKids >= :kidCount ORDER BY sp.price ASC")
    List<SubscriptionPlan> findActivePlansByMaxKids(@Param("kidCount") Integer kidCount);
}
