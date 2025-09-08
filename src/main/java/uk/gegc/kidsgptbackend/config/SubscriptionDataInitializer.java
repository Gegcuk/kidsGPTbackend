package uk.gegc.kidsgptbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.repository.subscription.SubscriptionPlanRepository;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionDataInitializer implements CommandLineRunner {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Override
    public void run(String... args) throws Exception {
        initializeSubscriptionPlans();
    }

    private void initializeSubscriptionPlans() {
        // Free Plan - no provider IDs (not sold through stores)
        if (subscriptionPlanRepository.findAll().stream()
                .noneMatch(plan -> "Free".equals(plan.getName()))) {
            SubscriptionPlan freePlan = new SubscriptionPlan();
            freePlan.setName("Free");
            freePlan.setDescription("Limited messages for first 3 days");
            freePlan.setPrice(BigDecimal.ZERO.setScale(2));
            freePlan.setCurrency("GBP");
            freePlan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
            freePlan.setMaxKids(1);
            freePlan.setFeatures("{\"chat_limit\": 15}"); // 15 messages in first 3 days
            freePlan.setGooglePlayProductId(null);
            freePlan.setActive(true);
            
            subscriptionPlanRepository.save(freePlan);
            log.info("Created Free Plan");
        }

        // Plus Monthly Plan - Google Play only
        if (subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly").isEmpty()) {
            SubscriptionPlan plusMonthly = new SubscriptionPlan();
            plusMonthly.setName("Plus Monthly");
            plusMonthly.setDescription("Unlimited messaging");
            plusMonthly.setPrice(new BigDecimal("4.99").setScale(2));
            plusMonthly.setCurrency("GBP");
            plusMonthly.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
            plusMonthly.setMaxKids(10);
            plusMonthly.setFeatures("{\"chat_limit\": -1}"); // unlimited
            plusMonthly.setGooglePlayProductId("plus_monthly");
            plusMonthly.setActive(true);
            
            subscriptionPlanRepository.save(plusMonthly);
            log.info("Created Plus Monthly Plan");
        }
    }
}
