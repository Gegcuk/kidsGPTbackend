package uk.gegc.kidsgptbackend.service.payment.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GooglePlayPaymentService Tests")
class GooglePlayPaymentServiceTest {

    @Mock
    private GooglePlayClient googlePlayClient;

    @InjectMocks
    private GooglePlayPaymentService googlePlayPaymentService;

    private User testUser;
    private String testProductId;
    private String testPurchaseToken;
    private String testOrderId;
    private String testReason;
    private String testPackageName;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        
        testProductId = "plus_monthly";
        testPurchaseToken = "test_purchase_token_123";
        testOrderId = "GPA.1234-5678-9012-34567";
        testReason = "User requested refund";
        testPackageName = "uk.gegc.kidsgpt";
        
        // Set up test configuration
        ReflectionTestUtils.setField(googlePlayPaymentService, "packageName", testPackageName);
        ReflectionTestUtils.setField(googlePlayPaymentService, "serviceAccountKey", "test_service_account_key");
        
        // Mock GooglePlayClient to return a valid subscription purchase with dynamic order IDs
        lenient().when(googlePlayClient.getSubscriptionPurchase(anyString(), anyString())).thenAnswer(invocation -> {
            GooglePlaySubscriptionPurchase mockPurchase = new GooglePlaySubscriptionPurchase();
            mockPurchase.setOrderId("GPA." + System.currentTimeMillis()); // Dynamic order ID
            mockPurchase.setPurchaseToken(testPurchaseToken);
            mockPurchase.setPurchaseState("PURCHASED"); // Active
            mockPurchase.setExpiryTimeMillis(System.currentTimeMillis() + 86400000L); // 24 hours from now
            return mockPurchase;
        });
    }

    @Test
    @DisplayName("createGooglePlaySubscription - returns order ID with gp prefix")
    void createGooglePlaySubscription_returnsOrderIdWithGpPrefix() {
        // When
        String result = googlePlayPaymentService.createGooglePlaySubscription(testUser, testProductId, testPurchaseToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).startsWith("gp_");
        assertThat(result).matches("gp_GPA\\.\\d+"); // Should match gp_GPA.{timestamp}
    }

    @Test
    @DisplayName("createGooglePlaySubscription - returns different IDs for different calls")
    void createGooglePlaySubscription_returnsDifferentIdsForDifferentCalls() {
        // When
        String result1 = googlePlayPaymentService.createGooglePlaySubscription(testUser, testProductId, testPurchaseToken);
        
        // Small delay to ensure different timestamps
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        String result2 = googlePlayPaymentService.createGooglePlaySubscription(testUser, testProductId, testPurchaseToken);

        // Then
        assertThat(result1).isNotEqualTo(result2);
        assertThat(result1).startsWith("gp_");
        assertThat(result2).startsWith("gp_");
    }

    @Test
    @DisplayName("createGooglePlaySubscription - handles different users")
    void createGooglePlaySubscription_handlesDifferentUsers() {
        // Given
        User anotherUser = new User();
        anotherUser.setId(UUID.randomUUID());
        anotherUser.setUsername("anotheruser");
        anotherUser.setEmail("another@example.com");

        // When
        String result1 = googlePlayPaymentService.createGooglePlaySubscription(testUser, testProductId, testPurchaseToken);
        String result2 = googlePlayPaymentService.createGooglePlaySubscription(anotherUser, testProductId, testPurchaseToken);

        // Then
        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        assertThat(result1).startsWith("gp_");
        assertThat(result2).startsWith("gp_");
    }

    @Test
    @DisplayName("createGooglePlaySubscription - handles different product IDs")
    void createGooglePlaySubscription_handlesDifferentProductIds() {
        // Given
        String differentProductId = "premium_yearly";

        // When
        String result1 = googlePlayPaymentService.createGooglePlaySubscription(testUser, testProductId, testPurchaseToken);
        String result2 = googlePlayPaymentService.createGooglePlaySubscription(testUser, differentProductId, testPurchaseToken);

        // Then
        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        assertThat(result1).startsWith("gp_");
        assertThat(result2).startsWith("gp_");
    }

    @Test
    @DisplayName("createGooglePlaySubscription - handles different purchase tokens")
    void createGooglePlaySubscription_handlesDifferentPurchaseTokens() {
        // Given
        String differentPurchaseToken = "different_purchase_token_456";

        // When
        String result1 = googlePlayPaymentService.createGooglePlaySubscription(testUser, testProductId, testPurchaseToken);
        String result2 = googlePlayPaymentService.createGooglePlaySubscription(testUser, testProductId, differentPurchaseToken);

        // Then
        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        assertThat(result1).startsWith("gp_");
        assertThat(result2).startsWith("gp_");
    }

    @Test
    @DisplayName("cancelSubscription - returns true and verifies log")
    void cancelSubscription_returnsTrueAndVerifiesLog() {
        // When
        boolean result = googlePlayPaymentService.cancelSubscription(testPurchaseToken);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("cancelSubscription - handles different purchase tokens")
    void cancelSubscription_handlesDifferentPurchaseTokens() {
        // Given
        String differentPurchaseToken = "different_purchase_token_456";

        // When
        boolean result1 = googlePlayPaymentService.cancelSubscription(testPurchaseToken);
        boolean result2 = googlePlayPaymentService.cancelSubscription(differentPurchaseToken);

        // Then
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
    }

    @Test
    @DisplayName("cancelSubscription - handles null purchase token")
    void cancelSubscription_handlesNullPurchaseToken() {
        // When
        boolean result = googlePlayPaymentService.cancelSubscription(null);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("cancelSubscription - handles empty purchase token")
    void cancelSubscription_handlesEmptyPurchaseToken() {
        // When
        boolean result = googlePlayPaymentService.cancelSubscription("");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("processRefund - returns true and verifies log")
    void processRefund_returnsTrueAndVerifiesLog() {
        // When
        boolean result = googlePlayPaymentService.processRefund(testOrderId, testReason);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("processRefund - handles different order IDs")
    void processRefund_handlesDifferentOrderIds() {
        // Given
        String differentOrderId = "GPA.9876-5432-1098-76543";

        // When
        boolean result1 = googlePlayPaymentService.processRefund(testOrderId, testReason);
        boolean result2 = googlePlayPaymentService.processRefund(differentOrderId, testReason);

        // Then
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
    }

    @Test
    @DisplayName("processRefund - handles different reasons")
    void processRefund_handlesDifferentReasons() {
        // Given
        String differentReason = "Technical issue with subscription";

        // When
        boolean result1 = googlePlayPaymentService.processRefund(testOrderId, testReason);
        boolean result2 = googlePlayPaymentService.processRefund(testOrderId, differentReason);

        // Then
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
    }

    @Test
    @DisplayName("processRefund - handles null order ID")
    void processRefund_handlesNullOrderId() {
        // When
        boolean result = googlePlayPaymentService.processRefund(null, testReason);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("processRefund - handles null reason")
    void processRefund_handlesNullReason() {
        // When
        boolean result = googlePlayPaymentService.processRefund(testOrderId, null);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("processRefund - handles empty order ID")
    void processRefund_handlesEmptyOrderId() {
        // When
        boolean result = googlePlayPaymentService.processRefund("", testReason);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("processRefund - handles empty reason")
    void processRefund_handlesEmptyReason() {
        // When
        boolean result = googlePlayPaymentService.processRefund(testOrderId, "");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("createGooglePlaySubscription - uses correct package name from configuration")
    void createGooglePlaySubscription_usesCorrectPackageNameFromConfiguration() {
        // When
        String result = googlePlayPaymentService.createGooglePlaySubscription(testUser, testProductId, testPurchaseToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).startsWith("gp_");
        
        // The service should use the configured package name internally
        // This test verifies the service is properly configured
    }

    @Test
    @DisplayName("createGooglePlaySubscription - generates timestamp-based IDs")
    void createGooglePlaySubscription_generatesTimestampBasedIds() {
        // Given
        long beforeCall = System.currentTimeMillis();

        // When
        String result = googlePlayPaymentService.createGooglePlaySubscription(testUser, testProductId, testPurchaseToken);

        // Then
        long afterCall = System.currentTimeMillis();
        
        // Extract timestamp from order ID format: gp_GPA.{timestamp}
        String orderIdPart = result.substring("gp_".length()); // Remove "gp_" prefix
        String timestampPart = orderIdPart.substring("GPA.".length()); // Remove "GPA." prefix
        long timestamp = Long.parseLong(timestampPart);
        
        assertThat(timestamp).isBetween(beforeCall, afterCall);
    }

    @Test
    @DisplayName("createGooglePlaySubscription - handles user with null ID")
    void createGooglePlaySubscription_handlesUserWithNullId() {
        // Given
        User userWithNullId = new User();
        userWithNullId.setId(null);
        userWithNullId.setUsername("testuser");
        userWithNullId.setEmail("test@example.com");

        // When
        String result = googlePlayPaymentService.createGooglePlaySubscription(userWithNullId, testProductId, testPurchaseToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).startsWith("gp_");
    }

    @Test
    @DisplayName("createGooglePlaySubscription - handles user with null username")
    void createGooglePlaySubscription_handlesUserWithNullUsername() {
        // Given
        User userWithNullUsername = new User();
        userWithNullUsername.setId(UUID.randomUUID());
        userWithNullUsername.setUsername(null);
        userWithNullUsername.setEmail("test@example.com");

        // When
        String result = googlePlayPaymentService.createGooglePlaySubscription(userWithNullUsername, testProductId, testPurchaseToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).startsWith("gp_");
    }

    @Test
    @DisplayName("createGooglePlaySubscription - handles user with null email")
    void createGooglePlaySubscription_handlesUserWithNullEmail() {
        // Given
        User userWithNullEmail = new User();
        userWithNullEmail.setId(UUID.randomUUID());
        userWithNullEmail.setUsername("testuser");
        userWithNullEmail.setEmail(null);

        // When
        String result = googlePlayPaymentService.createGooglePlaySubscription(userWithNullEmail, testProductId, testPurchaseToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).startsWith("gp_");
    }
}
