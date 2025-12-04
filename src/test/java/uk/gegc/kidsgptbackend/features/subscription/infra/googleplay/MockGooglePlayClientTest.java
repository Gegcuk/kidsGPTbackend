package uk.gegc.kidsgptbackend.features.subscription.infra.googleplay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class MockGooglePlayClientTest extends BaseUnitTest {

    private MockGooglePlayClient client;

    @BeforeEach
    void setUpClient() {
        Clock clock = createDefaultFixedClock();
        client = new MockGooglePlayClient(clock);
        org.springframework.test.util.ReflectionTestUtils.setField(client, "mockProductId", "test_monthly");
        org.springframework.test.util.ReflectionTestUtils.setField(client, "mockExpiryDays", 30L);
    }

    @Test
    @DisplayName("Mock client always returns active purchase and validates tokens")
    void mockClientReturnsActivePurchase() {
        GooglePlaySubscriptionPurchase purchase = client.getSubscriptionPurchase("test_monthly", "dummy_token");

        assertThat(purchase.getProductId()).isEqualTo("test_monthly");
        assertThat(purchase.getPurchaseToken()).isEqualTo("dummy_token");
        assertThat(purchase.isPurchased()).isTrue();
        assertThat(purchase.getExpiryTimeMillis()).isGreaterThan(purchase.getStartTimeMillis());

        boolean valid = client.verifyPurchaseToken("test_monthly", "dummy_token");
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Mock client allows acknowledgment without side effects")
    void mockClientAcknowledgeNoop() {
        client.acknowledgeSubscription("test_monthly", "dummy_token", "payload");
        // No exception expected
    }
}
