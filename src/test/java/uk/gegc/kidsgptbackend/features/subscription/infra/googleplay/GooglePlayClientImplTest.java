package uk.gegc.kidsgptbackend.features.subscription.infra.googleplay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class GooglePlayClientImplTest extends BaseUnitTest {

    @Test
    @DisplayName("When service account key is missing, client returns mock purchase and skips ack")
    void returnsMockPurchaseAndSkipsAckWithoutServiceAccount() {
        GooglePlayClientImpl client = new GooglePlayClientImpl();
        ReflectionTestUtils.setField(client, "serviceAccountKey", "");
        ReflectionTestUtils.setField(client, "packageName", "com.example.test");
        ReflectionTestUtils.setField(client, "applicationName", "TestApp");

        client.initializeAndroidPublisher(); // leaves androidPublisher null

        GooglePlaySubscriptionPurchase purchase = client.getSubscriptionPurchase("test_monthly", "test_token");
        assertThat(purchase.getProductId()).isEqualTo("test_monthly");
        assertThat(purchase.getPurchaseToken()).isEqualTo("test_token");
        assertThat(purchase.isEntitlementActive()).isTrue();

        boolean valid = client.verifyPurchaseToken("test_monthly", "test_token");
        assertThat(valid).isTrue();

        // Should not throw when acknowledging without real publisher
        client.acknowledgeSubscription("test_monthly", "test_token", "payload");
    }
}
