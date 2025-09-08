package uk.gegc.kidsgptbackend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.repository.subscription.SubscriptionPlanRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    }

    @Test
    @DisplayName("runAfterPropertiesSet creates Free plan when absent")
    void runAfterPropertiesSet_createsFreePlanWhenAbsent() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        when(subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")).thenReturn(Optional.empty());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(2)).save(captor.capture());

        SubscriptionPlan freePlan = captor.getAllValues().get(0);
        assertThat(freePlan.getName()).isEqualTo("Free");
        assertThat(freePlan.getDescription()).isEqualTo("Limited messages for first 3 days");
        assertThat(freePlan.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(freePlan.getCurrency()).isEqualTo("GBP");
        assertThat(freePlan.getBillingCycle()).isEqualTo(SubscriptionPlan.BillingCycle.MONTHLY);
        assertThat(freePlan.getMaxKids()).isEqualTo(1);
        assertThat(freePlan.getFeatures()).isEqualTo("{\"chat_limit\": 15}");
        assertThat(freePlan.getGooglePlayProductId()).isNull();
        assertThat(freePlan.isActive()).isTrue();
    }

    @Test
    @DisplayName("runAfterPropertiesSet creates Plus Monthly plan when absent")
    void runAfterPropertiesSet_createsPlusMonthlyPlanWhenAbsent() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        when(subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")).thenReturn(Optional.empty());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(2)).save(captor.capture());

        SubscriptionPlan plusMonthly = captor.getAllValues().get(1);
        assertThat(plusMonthly.getName()).isEqualTo("Plus Monthly");
        assertThat(plusMonthly.getDescription()).isEqualTo("Unlimited messaging");
        assertThat(plusMonthly.getPrice()).isEqualByComparingTo(new BigDecimal("4.99"));
        assertThat(plusMonthly.getCurrency()).isEqualTo("GBP");
        assertThat(plusMonthly.getBillingCycle()).isEqualTo(SubscriptionPlan.BillingCycle.MONTHLY);
        assertThat(plusMonthly.getMaxKids()).isEqualTo(10);
        assertThat(plusMonthly.getFeatures()).isEqualTo("{\"chat_limit\": -1}");
        assertThat(plusMonthly.getGooglePlayProductId()).isEqualTo("plus_monthly");
        assertThat(plusMonthly.isActive()).isTrue();
    }

    @Test
    @DisplayName("runAfterPropertiesSet is idempotent - no duplicates on subsequent runs")
    void runAfterPropertiesSet_isIdempotent() throws Exception {
        // Given - simulate existing plans
        SubscriptionPlan existingFree = new SubscriptionPlan();
        existingFree.setName("Free");
        
        SubscriptionPlan existingPlus = new SubscriptionPlan();
        existingPlus.setGooglePlayProductId("plus_monthly");

        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.List.of(existingFree));
        when(subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")).thenReturn(Optional.of(existingPlus));

        // When
        subscriptionDataInitializer.run();

        // Then
        verify(subscriptionPlanRepository, never()).save(any());
    }

    @Test
    @DisplayName("runAfterPropertiesSet creates only missing plans")
    void runAfterPropertiesSet_createsOnlyMissingPlans() throws Exception {
        // Given - Free plan exists, Plus Monthly doesn't
        SubscriptionPlan existingFree = new SubscriptionPlan();
        existingFree.setName("Free");

        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.List.of(existingFree));
        when(subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")).thenReturn(Optional.empty());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(1)).save(captor.capture());

        SubscriptionPlan createdPlan = captor.getValue();
        assertThat(createdPlan.getName()).isEqualTo("Plus Monthly");
        assertThat(createdPlan.getGooglePlayProductId()).isEqualTo("plus_monthly");
    }

    @Test
    @DisplayName("runAfterPropertiesSet creates only Free plan when Plus Monthly exists")
    void runAfterPropertiesSet_createsOnlyFreePlanWhenPlusMonthlyExists() throws Exception {
        // Given - Plus Monthly exists, Free doesn't
        SubscriptionPlan existingPlus = new SubscriptionPlan();
        existingPlus.setGooglePlayProductId("plus_monthly");

        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.List.of(existingPlus));
        when(subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")).thenReturn(Optional.of(existingPlus));

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(1)).save(captor.capture());

        SubscriptionPlan createdPlan = captor.getValue();
        assertThat(createdPlan.getName()).isEqualTo("Free");
        assertThat(createdPlan.getGooglePlayProductId()).isNull();
    }

    @Test
    @DisplayName("Free plan has correct currency and price values")
    void freePlan_hasCorrectCurrencyAndPriceValues() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        when(subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")).thenReturn(Optional.empty());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(2)).save(captor.capture());

        SubscriptionPlan freePlan = captor.getAllValues().get(0);
        assertThat(freePlan.getCurrency()).isEqualTo("GBP");
        assertThat(freePlan.getPrice()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    @DisplayName("Plus Monthly plan has correct currency and price values")
    void plusMonthlyPlan_hasCorrectCurrencyAndPriceValues() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        when(subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")).thenReturn(Optional.empty());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(2)).save(captor.capture());

        SubscriptionPlan plusMonthly = captor.getAllValues().get(1);
        assertThat(plusMonthly.getCurrency()).isEqualTo("GBP");
        assertThat(plusMonthly.getPrice()).isEqualByComparingTo(new BigDecimal("4.99").setScale(2));
    }

    @Test
    @DisplayName("Free plan has no provider IDs")
    void freePlan_hasNoProviderIds() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        when(subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")).thenReturn(Optional.empty());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(2)).save(captor.capture());

        SubscriptionPlan freePlan = captor.getAllValues().get(0);
        assertThat(freePlan.getGooglePlayProductId()).isNull();
    }

    @Test
    @DisplayName("Plus Monthly plan has correct Google Play product ID")
    void plusMonthlyPlan_hasCorrectGooglePlayProductId() throws Exception {
        // Given
        when(subscriptionPlanRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        when(subscriptionPlanRepository.findByGooglePlayProductId("plus_monthly")).thenReturn(Optional.empty());

        // When
        subscriptionDataInitializer.run();

        // Then
        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(2)).save(captor.capture());

        SubscriptionPlan plusMonthly = captor.getAllValues().get(1);
        assertThat(plusMonthly.getGooglePlayProductId()).isEqualTo("plus_monthly");
    }
}
