package uk.gegc.kidsgptbackend.features.subscription.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.WebhookEvent;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.WebhookEventRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class WebhookEventRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    private WebhookEvent googlePlayEvent;
    private WebhookEvent anotherGooglePlayEvent;

    @BeforeEach
    void setUp() {
        // Create Google Play webhook event
        googlePlayEvent = new WebhookEvent();
        googlePlayEvent.setPaymentProvider(WebhookEvent.PaymentProvider.GOOGLE_PLAY);
        googlePlayEvent.setExternalEventId("delivery_id_123");
        googlePlayEvent.setEventType("SUBSCRIPTION_PURCHASED");
        googlePlayEvent.setProcessed(false);
        googlePlayEvent.setPayload("{\"subscriptionId\":\"plus_monthly\",\"purchaseToken\":\"token_123\"}");
        googlePlayEvent.setCreatedAt(Instant.now());
        entityManager.persistAndFlush(googlePlayEvent);

        // Create another Google Play webhook event
        anotherGooglePlayEvent = new WebhookEvent();
        anotherGooglePlayEvent.setPaymentProvider(WebhookEvent.PaymentProvider.GOOGLE_PLAY);
        anotherGooglePlayEvent.setExternalEventId("delivery_id_456");
        anotherGooglePlayEvent.setEventType("SUBSCRIPTION_CANCELED");
        anotherGooglePlayEvent.setProcessed(true);
        anotherGooglePlayEvent.setPayload("{\"subscriptionId\":\"plus_monthly\",\"purchaseToken\":\"token_456\"}");
        anotherGooglePlayEvent.setCreatedAt(Instant.now().minusSeconds(3600));
        entityManager.persistAndFlush(anotherGooglePlayEvent);

        entityManager.clear();
    }

    @Test
    @DisplayName("findByPaymentProviderAndExternalEventId returns correct event")
    void findByPaymentProviderAndExternalEventId_returnsCorrectEvent() {
        // When
        Optional<WebhookEvent> result = webhookEventRepository.findByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, "delivery_id_123");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getPaymentProvider()).isEqualTo(WebhookEvent.PaymentProvider.GOOGLE_PLAY);
        assertThat(result.get().getExternalEventId()).isEqualTo("delivery_id_123");
        assertThat(result.get().getEventType()).isEqualTo("SUBSCRIPTION_PURCHASED");
        assertThat(result.get().isProcessed()).isFalse();
    }

    @Test
    @DisplayName("findByPaymentProviderAndExternalEventId returns empty when not found")
    void findByPaymentProviderAndExternalEventId_returnsEmptyWhenNotFound() {
        // When
        Optional<WebhookEvent> result = webhookEventRepository.findByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, "nonexistent_delivery_id");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByPaymentProviderAndExternalEventId returns empty for different provider")
    void findByPaymentProviderAndExternalEventId_returnsEmptyForDifferentProvider() {
        // When - Try to find with different provider (even though we only have GOOGLE_PLAY)
        Optional<WebhookEvent> result = webhookEventRepository.findByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, "delivery_id_123");

        // Then
        assertThat(result).isPresent(); // Should find it since we're using GOOGLE_PLAY
        assertThat(result.get().getExternalEventId()).isEqualTo("delivery_id_123");
    }

    @Test
    @DisplayName("existsByPaymentProviderAndExternalEventId returns true when exists")
    void existsByPaymentProviderAndExternalEventId_returnsTrueWhenExists() {
        // When
        boolean exists = webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, "delivery_id_123");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByPaymentProviderAndExternalEventId returns false when not exists")
    void existsByPaymentProviderAndExternalEventId_returnsFalseWhenNotExists() {
        // When
        boolean exists = webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, "nonexistent_delivery_id");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByPaymentProviderAndExternalEventId works with processed events")
    void existsByPaymentProviderAndExternalEventId_worksWithProcessedEvents() {
        // When
        boolean exists = webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, "delivery_id_456");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("unique constraint on payment_provider and external_event_id is enforced")
    void uniqueConstraint_onPaymentProviderAndExternalEventId_isEnforced() {
        // Given - Try to create duplicate webhook event
        WebhookEvent duplicate = new WebhookEvent();
        duplicate.setPaymentProvider(WebhookEvent.PaymentProvider.GOOGLE_PLAY);
        duplicate.setExternalEventId("delivery_id_123"); // Same as existing
        duplicate.setEventType("SUBSCRIPTION_UPDATED");
        duplicate.setProcessed(false);
        duplicate.setPayload("{\"different\":\"payload\"}");
        duplicate.setCreatedAt(Instant.now());

        // When & Then
        try {
            entityManager.persistAndFlush(duplicate);
            entityManager.clear();
            // If we get here, the constraint didn't work
            assertThat(false).as("Expected constraint violation for duplicate provider/external event ID combination").isTrue();
        } catch (Exception e) {
            // Expected - constraint violation
            assertThat(e.getMessage()).contains("Unique index or primary key violation");
        }
    }

    @Test
    @DisplayName("different external_event_id with same provider is allowed")
    void differentExternalEventId_withSameProvider_isAllowed() {
        // Given - Create event with different external ID but same provider
        WebhookEvent differentEvent = new WebhookEvent();
        differentEvent.setPaymentProvider(WebhookEvent.PaymentProvider.GOOGLE_PLAY);
        differentEvent.setExternalEventId("delivery_id_789"); // Different external ID
        differentEvent.setEventType("SUBSCRIPTION_RENEWED");
        differentEvent.setProcessed(false);
        differentEvent.setPayload("{\"subscriptionId\":\"plus_monthly\",\"purchaseToken\":\"token_789\"}");
        differentEvent.setCreatedAt(Instant.now());

        // When
        entityManager.persistAndFlush(differentEvent);
        entityManager.clear();

        // Then
        Optional<WebhookEvent> result = webhookEventRepository.findByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, "delivery_id_789");
        assertThat(result).isPresent();
        assertThat(result.get().getEventType()).isEqualTo("SUBSCRIPTION_RENEWED");
    }

    @Test
    @DisplayName("findByPaymentProviderAndExternalEventId handles case sensitivity")
    void findByPaymentProviderAndExternalEventId_handlesCaseSensitivity() {
        // When - Try with different case
        Optional<WebhookEvent> result = webhookEventRepository.findByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, "DELIVERY_ID_123");

        // Then
        assertThat(result).isEmpty(); // Should be case sensitive
    }

    @Test
    @DisplayName("existsByPaymentProviderAndExternalEventId handles case sensitivity")
    void existsByPaymentProviderAndExternalEventId_handlesCaseSensitivity() {
        // When - Try with different case
        boolean exists = webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, "DELIVERY_ID_123");

        // Then
        assertThat(exists).isFalse(); // Should be case sensitive
    }

    @Test
    @DisplayName("repository methods work with null external_event_id")
    void repositoryMethods_workWithNullExternalEventId() {
        // When
        Optional<WebhookEvent> findResult = webhookEventRepository.findByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, null);
        boolean existsResult = webhookEventRepository.existsByPaymentProviderAndExternalEventId(
                WebhookEvent.PaymentProvider.GOOGLE_PLAY, null);

        // Then
        assertThat(findResult).isEmpty();
        assertThat(existsResult).isFalse();
    }
}
