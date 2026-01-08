package uk.gegc.kidsgptbackend.shared.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionPlanRepository;

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
            freePlan.setMaxKids(5); // Align with FREE_TIER_MAX_KIDS in KidCountingServiceImpl
            freePlan.setFeatures("{\"chat_limit\": 15}"); // 15 messages in first 3 days
            freePlan.setGooglePlayProductId(null);
            freePlan.setActive(true);
            
            subscriptionPlanRepository.save(freePlan);
            log.info("Created Free Plan");
        }

        // Deactivate old plus_monthly plan if it exists (legacy SKU)
        subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")
                .ifPresent(oldPlan -> {
                    if (oldPlan.isActive()) {
                        oldPlan.setActive(false);
                        subscriptionPlanRepository.save(oldPlan);
                        log.info("Deactivated legacy plus_monthly plan");
                    }
                });

        // 5 Subscription Tiers (1-5 kids per parent)
        createOrUpdatePlanIfMissing("kids_1_monthly", "KidsGPT 1 Kid", "Monthly subscription for 1 kid", 1);
        createOrUpdatePlanIfMissing("kids_2_monthly", "KidsGPT 2 Kids", "Monthly subscription for 2 kids", 2);
        createOrUpdatePlanIfMissing("kids_3_monthly", "KidsGPT 3 Kids", "Monthly subscription for 3 kids", 3);
        createOrUpdatePlanIfMissing("kids_4_monthly", "KidsGPT 4 Kids", "Monthly subscription for 4 kids", 4);
        createOrUpdatePlanIfMissing("kids_5_monthly", "KidsGPT 5 Kids", "Monthly subscription for 5 kids", 5);
    }

    private void createOrUpdatePlanIfMissing(String googlePlayProductId, String name, String description, int maxKids) {
        subscriptionPlanRepository.findByGooglePlayProductId(googlePlayProductId)
                .ifPresentOrElse(
                        existingPlan -> {
                            // Update existing plan if needed
                            boolean updated = false;
                            if (!existingPlan.getMaxKids().equals(maxKids)) {
                                existingPlan.setMaxKids(maxKids);
                                updated = true;
                            }
                            String expectedFeatures = String.format("{\"chat_limit\": -1, \"image_generation\": 2}");
                            if (!expectedFeatures.equals(existingPlan.getFeatures())) {
                                existingPlan.setFeatures(expectedFeatures);
                                updated = true;
                            }
                            if (!existingPlan.isActive()) {
                                existingPlan.setActive(true);
                                updated = true;
                            }
                            if (updated) {
                                subscriptionPlanRepository.save(existingPlan);
                                log.info("Updated {} plan (maxKids: {})", googlePlayProductId, maxKids);
                            }
                        },
                        () -> {
                            // Create new plan
                            SubscriptionPlan plan = new SubscriptionPlan();
                            plan.setName(name);
                            plan.setDescription(description);
                            // Note: Price should be set based on your store pricing. Default to 0, update via DB/admin
                            plan.setPrice(BigDecimal.ZERO.setScale(2));
                            plan.setCurrency("GBP");
                            plan.setBillingCycle(SubscriptionPlan.BillingCycle.MONTHLY);
                            plan.setMaxKids(maxKids);
                            plan.setFeatures(String.format("{\"chat_limit\": -1, \"image_generation\": 2}"));
                            plan.setGooglePlayProductId(googlePlayProductId);
                            plan.setActive(true);
                            
                            subscriptionPlanRepository.save(plan);
                            log.info("Created {} plan (maxKids: {})", googlePlayProductId, maxKids);
                        }
                );
    }
}
