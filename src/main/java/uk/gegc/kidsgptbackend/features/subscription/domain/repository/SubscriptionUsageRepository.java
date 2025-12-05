package uk.gegc.kidsgptbackend.features.subscription.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionUsage;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionUsageRepository extends JpaRepository<SubscriptionUsage, UUID> {

    Optional<SubscriptionUsage> findByUserAndFeatureAndPeriodKey(User user, String feature, String periodKey);

    List<SubscriptionUsage> findByUserAndPeriodKey(User user, String periodKey);

    @Query("SELECT su FROM SubscriptionUsage su WHERE su.periodEnd < :now")
    List<SubscriptionUsage> findExpiredUsagePeriods(@Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE SubscriptionUsage su SET su.usedCount = su.usedCount + 1, su.updatedAt = :now " +
           "WHERE su.user = :user AND su.feature = :feature AND su.periodKey = :periodKey")
    int incrementUsage(@Param("user") User user, 
                      @Param("feature") String feature, 
                      @Param("periodKey") String periodKey,
                      @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM SubscriptionUsage su WHERE su.periodKey = :periodKey")
    void deleteByPeriodKey(@Param("periodKey") String periodKey);

    @Modifying
    @Query("DELETE FROM SubscriptionUsage su WHERE su.user = :user")
    void deleteByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(su.usedCount), 0) FROM SubscriptionUsage su " +
           "WHERE su.user = :user AND su.feature = :feature AND su.periodKey = :periodKey")
    Integer getTotalUsageForPeriod(@Param("user") User user, 
                                  @Param("feature") String feature, 
                                  @Param("periodKey") String periodKey);
}
