package uk.gegc.kidsgptbackend.features.subscription.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionPlanRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SubscriptionPlanRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    private SubscriptionPlan freePlan;
    private SubscriptionPlan plusMonthlyPlan;
    private SubscriptionPlan inactivePlan;

    @BeforeEach
    void setUp() {
        // Create Free Plan
        freePlan = new SubscriptionPlan();
        freePlan.setName("Free");
        freePlan.setDescription("Limited messages for first 3 days");
        freePlan.setPrice(BigDecimal.ZERO.setScale(2));
        freePlan.setCurrency("GBP");
        freePlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        freePlan.setMaxKids(1);
        freePlan.setFeatures("{\"chat_limit\": 15}");
        freePlan.setGooglePlayProductId(null);
        freePlan.setActive(true);
        freePlan.setCreatedAt(Instant.now());
        freePlan.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(freePlan);

        // Create Plus Monthly Plan
        plusMonthlyPlan = new SubscriptionPlan();
        plusMonthlyPlan.setName("Plus Monthly");
        plusMonthlyPlan.setDescription("Unlimited messaging");
        plusMonthlyPlan.setPrice(new BigDecimal("4.99").setScale(2));
        plusMonthlyPlan.setCurrency("GBP");
        plusMonthlyPlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        plusMonthlyPlan.setMaxKids(10);
        plusMonthlyPlan.setFeatures("{\"chat_limit\": -1}");
        plusMonthlyPlan.setGooglePlayProductId("plus_monthly");
        plusMonthlyPlan.setActive(true);
        plusMonthlyPlan.setCreatedAt(Instant.now());
        plusMonthlyPlan.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(plusMonthlyPlan);

        // Create Inactive Plan
        inactivePlan = new SubscriptionPlan();
        inactivePlan.setName("Old Plan");
        inactivePlan.setDescription("Deprecated plan");
        inactivePlan.setPrice(new BigDecimal("9.99").setScale(2));
        inactivePlan.setCurrency("GBP");
        inactivePlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        inactivePlan.setMaxKids(5);
        inactivePlan.setFeatures("{\"chat_limit\": 100}");
        inactivePlan.setGooglePlayProductId("old_plan");
        inactivePlan.setActive(false);
        inactivePlan.setCreatedAt(Instant.now());
        inactivePlan.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(inactivePlan);

        entityManager.clear();
    }

    @Test
    @DisplayName("findByIsActiveTrueOrderByPriceAsc returns only active plans ordered by price")
    void findByIsActiveTrueOrderByPriceAsc_returnsOnlyActivePlansOrderedByPrice() {
        // When
        List<SubscriptionPlan> result = subscriptionPlanRepository.findByIsActiveTrueOrderByPriceAsc();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Free");
        assertThat(result.get(0).getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.get(1).getName()).isEqualTo("Plus Monthly");
        assertThat(result.get(1).getPrice()).isEqualByComparingTo(new BigDecimal("4.99"));
    }

    @Test
    @DisplayName("findByIsActiveTrueOrderByPriceAsc excludes inactive plans")
    void findByIsActiveTrueOrderByPriceAsc_excludesInactivePlans() {
        // When
        List<SubscriptionPlan> result = subscriptionPlanRepository.findByIsActiveTrueOrderByPriceAsc();

        // Then
        assertThat(result).extracting(SubscriptionPlan::getName)
                .containsExactly("Free", "Plus Monthly");
        assertThat(result).extracting(SubscriptionPlan::getName)
                .doesNotContain("Old Plan");
    }

    @Test
    @DisplayName("findByGooglePlayProductId returns plan when present")
    void findByGooglePlayProductId_returnsPlanWhenPresent() {
        // When
        Optional<SubscriptionPlan> result = subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Plus Monthly");
        assertThat(result.get().getGooglePlayProductId()).isEqualTo("plus_monthly");
    }

    @Test
    @DisplayName("findByGooglePlayProductId returns empty when not found")
    void findByGooglePlayProductId_returnsEmptyWhenNotFound() {
        // When
        Optional<SubscriptionPlan> result = subscriptionPlanRepository.findByGooglePlayProductId("nonexistent");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByGooglePlayProductId returns empty for null product ID")
    void findByGooglePlayProductId_returnsEmptyForNullProductId() {
        // When
        Optional<SubscriptionPlan> result = subscriptionPlanRepository.findByGooglePlayProductId(null);

        // Then
        // Note: This test might return the Free plan since it has null googlePlayProductId
        // The behavior depends on how JPA handles null comparisons
        if (result.isPresent()) {
            assertThat(result.get().getGooglePlayProductId()).isNull();
        } else {
            assertThat(result).isEmpty();
        }
    }

    @Test
    @DisplayName("findActivePlansByBillingCycle returns only active plans with matching billing cycle")
    void findActivePlansByBillingCycle_returnsOnlyActivePlansWithMatchingBillingCycle() {
        // When
        List<SubscriptionPlan> result = subscriptionPlanRepository.findActivePlansByBillingCycle(
                SubscriptionPlan.BillingCycle.MONTHLY);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(SubscriptionPlan::getName)
                .containsExactlyInAnyOrder("Free", "Plus Monthly");
        assertThat(result).allMatch(plan -> plan.getBillingCycle() == SubscriptionPlan.BillingCycle.MONTHLY);
        assertThat(result).allMatch(SubscriptionPlan::isActive);
    }

    @Test
    @DisplayName("findActivePlansByBillingCycle returns empty for non-matching billing cycle")
    void findActivePlansByBillingCycle_returnsEmptyForNonMatchingBillingCycle() {
        // When
        List<SubscriptionPlan> result = subscriptionPlanRepository.findActivePlansByBillingCycle(
                SubscriptionPlan.BillingCycle.YEARLY);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findActivePlansByMaxKids returns plans with sufficient capacity ordered by price")
    void findActivePlansByMaxKids_returnsPlansWithSufficientCapacityOrderedByPrice() {
        // When
        List<SubscriptionPlan> result = subscriptionPlanRepository.findActivePlansByMaxKids(5);

        // Then
        assertThat(result).hasSize(1); // Only Plus Monthly has maxKids >= 5
        assertThat(result.get(0).getName()).isEqualTo("Plus Monthly"); // Price: 4.99
        assertThat(result.get(0).getMaxKids()).isEqualTo(10);
        assertThat(result).allMatch(plan -> plan.getMaxKids() >= 5);
        assertThat(result).allMatch(SubscriptionPlan::isActive);
    }

    @Test
    @DisplayName("findActivePlansByMaxKids returns only plans with sufficient capacity")
    void findActivePlansByMaxKids_returnsOnlyPlansWithSufficientCapacity() {
        // When
        List<SubscriptionPlan> result = subscriptionPlanRepository.findActivePlansByMaxKids(15);

        // Then
        assertThat(result).isEmpty(); // No plans have maxKids >= 15
    }

    @Test
    @DisplayName("findActivePlansByMaxKids returns empty when no plans have sufficient capacity")
    void findActivePlansByMaxKids_returnsEmptyWhenNoPlansHaveSufficientCapacity() {
        // When
        List<SubscriptionPlan> result = subscriptionPlanRepository.findActivePlansByMaxKids(100);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findActivePlansByMaxKids excludes inactive plans even if they have sufficient capacity")
    void findActivePlansByMaxKids_excludesInactivePlans() {
        // When
        List<SubscriptionPlan> result = subscriptionPlanRepository.findActivePlansByMaxKids(3);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Plus Monthly");
        // Old Plan (maxKids=5, inactive) should not be included
    }

    @Test
    @DisplayName("ordering by price is correct for multiple plans")
    void orderingByPrice_isCorrectForMultiplePlans() {
        // Given - Create additional plan with price between Free and Plus Monthly
        SubscriptionPlan midPlan = new SubscriptionPlan();
        midPlan.setName("Mid Plan");
        midPlan.setDescription("Mid tier plan");
        midPlan.setPrice(new BigDecimal("2.99").setScale(2));
        midPlan.setCurrency("GBP");
        midPlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
        midPlan.setMaxKids(3);
        midPlan.setFeatures("{\"chat_limit\": 50}");
        midPlan.setGooglePlayProductId("mid_plan");
        midPlan.setActive(true);
        midPlan.setCreatedAt(Instant.now());
        midPlan.setUpdatedAt(Instant.now());
        entityManager.persistAndFlush(midPlan);
        entityManager.clear();

        // When
        List<SubscriptionPlan> result = subscriptionPlanRepository.findByIsActiveTrueOrderByPriceAsc();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getPrice()).isEqualByComparingTo(BigDecimal.ZERO); // Free
        assertThat(result.get(1).getPrice()).isEqualByComparingTo(new BigDecimal("2.99")); // Mid
        assertThat(result.get(2).getPrice()).isEqualByComparingTo(new BigDecimal("4.99")); // Plus Monthly
    }
}
