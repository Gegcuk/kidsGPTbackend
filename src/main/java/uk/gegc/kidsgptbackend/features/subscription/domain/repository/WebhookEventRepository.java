package uk.gegc.kidsgptbackend.features.subscription.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.WebhookEvent;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByPaymentProviderAndExternalEventId(
        WebhookEvent.PaymentProvider paymentProvider, 
        String externalEventId
    );

    boolean existsByPaymentProviderAndExternalEventId(
        WebhookEvent.PaymentProvider paymentProvider, 
        String externalEventId
    );
}
