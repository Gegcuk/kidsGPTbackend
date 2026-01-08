package uk.gegc.kidsgptbackend.shared.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionPlanRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
class SubscriptionDataInitializerTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @InjectMocks
    private SubscriptionDataInitializer subscriptionDataInitializer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        lenient()
                .when(subscriptionPlanRepository.findByGooglePlayProductId(anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("runAfterPropertiesSet creates Free plan when absent")
    void runAfterPropertiesSet_createsFreePlanWhenAbsent() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(6)).save(captor.capture()); // Free + 5 kids tiers

        SubscriptionPlan freePlan = captor.getAllValues().stream()
                .filter(plan -> "Free".equals(plan.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(freePlan.getName()).isEqualTo("Free");
        assertThat(freePlan.getDescription()).isEqualTo("Limited messages for first 3 days");
        assertThat(freePlan.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(freePlan.getCurrency()).isEqualTo("GBP");
        assertThat(freePlan.getBillingCycle()).isEqualTo(SubscriptionPlan.BillingCycle.MONTHLY);
        assertThat(freePlan.getMaxKids()).isEqualTo(5); // Aligned with enforcement
        assertThat(freePlan.getFeatures()).isEqualTo("{\"chat_limit\": 15}");
        assertThat(freePlan.getGooglePlayProductId()).isNull();
        assertThat(freePlan.isActive()).isTrue();
    }

    @Test
    @DisplayName("runAfterPropertiesSet creates kids_*_monthly plans when absent")
    void runAfterPropertiesSet_createsKidsTiersWhenAbsent() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(6)).save(captor.capture()); // Free + 5 tiers

        // Verify kids_1_monthly plan
        SubscriptionPlan kids1Plan = captor.getAllValues().stream()
                .filter(p -> "kids_1_monthly".equals(p.getGooglePlayProductId()))
                .findFirst()
                .orElseThrow();
        assertThat(kids1Plan.getName()).isEqualTo("KidsGPT 1 Kid");
        assertThat(kids1Plan.getMaxKids()).isEqualTo(1);
        assertThat(kids1Plan.getFeatures()).isEqualTo("{\"chat_limit\": -1, \"image_generation\": 2}");
        assertThat(kids1Plan.isActive()).isTrue();

        // Verify kids_5_monthly plan
        SubscriptionPlan kids5Plan = captor.getAllValues().stream()
                .filter(p -> "kids_5_monthly".equals(p.getGooglePlayProductId()))
                .findFirst()
                .orElseThrow();
        assertThat(kids5Plan.getName()).isEqualTo("KidsGPT 5 Kids");
        assertThat(kids5Plan.getMaxKids()).isEqualTo(5);
        assertThat(kids5Plan.getFeatures()).isEqualTo("{\"chat_limit\": -1, \"image_generation\": 2}");
        assertThat(kids5Plan.isActive()).isTrue();
    }

    @Test
    @DisplayName("runAfterPropertiesSet deactivates legacy plus_monthly plan")
    void runAfterPropertiesSet_deactivatesLegacyPlusMonthly() throws Exception {
        // Given - legacy plus_monthly plan exists
        SubscriptionPlan existingFree = new SubscriptionPlan();
        existingFree.setName("Free");
        
        SubscriptionPlan existingPlus = new SubscriptionPlan();
        existingPlus.setGooglePlayProductId("plus_monthly");
        existingPlus.setActive(true);

        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.List.of(existingFree));
        when(subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")).thenReturn(Optional.of(existingPlus));

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, atLeastOnce()).save(captor.capture());
        
        // Verify plus_monthly was deactivated
        SubscriptionPlan deactivatedPlan = captor.getAllValues().stream()
                .filter(p -> "plus_monthly".equals(p.getGooglePlayProductId()))
                .findFirst()
                .orElseThrow();
        assertThat(deactivatedPlan.isActive()).isFalse();
    }

    @Test
    @DisplayName("runAfterPropertiesSet creates only missing kids tiers")
    void runAfterPropertiesSet_createsOnlyMissingKidsTiers() throws Exception {
        // Given - Free plan exists, kids_1_monthly exists, others don't
        SubscriptionPlan existingFree = new SubscriptionPlan();
        existingFree.setName("Free");
        
        SubscriptionPlan existingKids1 = new SubscriptionPlan();
        existingKids1.setGooglePlayProductId("kids_1_monthly");
        existingKids1.setFeatures("{\"chat_limit\": -1, \"image_generation\": 2}");

        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.List.of(existingFree));
        when(subscriptionPlanRepository.findByGooglePlayProductId("kids_1_monthly")).thenReturn(Optional.of(existingKids1));

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(4)).save(captor.capture()); // 4 missing tiers

        // Verify only missing tiers were created
        assertThat(captor.getAllValues()).hasSize(4);
        assertThat(captor.getAllValues().stream()
                .map(SubscriptionPlan::getGooglePlayProductId)
                .filter(id -> id != null && id.startsWith("kids_"))
        ).containsExactlyInAnyOrder("kids_2_monthly", "kids_3_monthly", "kids_4_monthly", "kids_5_monthly");
    }

    @Test
    @DisplayName("runAfterPropertiesSet updates existing plans with correct features")
    void runAfterPropertiesSet_updatesExistingPlansWithCorrectFeatures() throws Exception {
        // Given - Existing kids_1_monthly plan with wrong features
        SubscriptionPlan existingKids1 = new SubscriptionPlan();
        existingKids1.setGooglePlayProductId("kids_1_monthly");
        existingKids1.setMaxKids(1);
        existingKids1.setFeatures("{\"chat_limit\": 100}"); // Wrong features
        existingKids1.setActive(true);

        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        when(subscriptionPlanRepository.findByGooglePlayProductId("kids_1_monthly")).thenReturn(Optional.of(existingKids1));

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, atLeastOnce()).save(captor.capture());
        
        // Verify existing plan was updated with correct features
        SubscriptionPlan updatedPlan = captor.getAllValues().stream()
                .filter(p -> "kids_1_monthly".equals(p.getGooglePlayProductId()))
                .findFirst()
                .orElseThrow();
        assertThat(updatedPlan.getFeatures()).isEqualTo("{\"chat_limit\": -1, \"image_generation\": 2}");
    }

    @Test
    @DisplayName("Free plan has correct currency and price values")
    void freePlan_hasCorrectCurrencyAndPriceValues() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(6)).save(captor.capture());

        SubscriptionPlan freePlan = captor.getAllValues().stream()
                .filter(plan -> "Free".equals(plan.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(freePlan.getCurrency()).isEqualTo("GBP");
        assertThat(freePlan.getPrice()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    @DisplayName("Kids 1 Monthly plan has correct currency and price values")
    void kids1MonthlyPlan_hasCorrectCurrencyAndPriceValues() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(6)).save(captor.capture());

        SubscriptionPlan kids1Plan = captor.getAllValues().stream()
                .filter(plan -> "kids_1_monthly".equals(plan.getGooglePlayProductId()))
                .findFirst()
                .orElseThrow();
        assertThat(kids1Plan.getCurrency()).isEqualTo("GBP");
        assertThat(kids1Plan.getPrice()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    @DisplayName("Free plan has no provider IDs")
    void freePlan_hasNoProviderIds() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(6)).save(captor.capture());

        SubscriptionPlan freePlan = captor.getAllValues().stream()
                .filter(plan -> "Free".equals(plan.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(freePlan.getGooglePlayProductId()).isNull();
    }

    @Test
    @DisplayName("Kids tiers have correct Google Play product IDs")
    void kidsTiers_haveCorrectGooglePlayProductIds() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(6)).save(captor.capture()); // Free + 5 tiers

        // Verify all product IDs are correct
        var kidsPlans = captor.getAllValues().stream()
                .filter(p -> p.getGooglePlayProductId() != null && p.getGooglePlayProductId().startsWith("kids_"))
                .toList();
        
        assertThat(kidsPlans).hasSize(5);
        assertThat(kidsPlans.stream().map(SubscriptionPlan::getGooglePlayProductId))
                .containsExactlyInAnyOrder("kids_1_monthly", "kids_2_monthly", "kids_3_monthly", "kids_4_monthly", "kids_5_monthly");
    }
}
