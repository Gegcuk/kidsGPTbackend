package uk.gegc.kidsgptbackend.service.subscription.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.repository.subscription.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookProcessingServiceImpl Tests")
class WebhookProcessingServiceImplTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private GooglePlayClient googlePlayClient;

    @InjectMocks
    private WebhookProcessingServiceImpl webhookProcessingService;

    private UserSubscription testSubscription;
    private SubscriptionPlan testPlan;
    private GooglePlaySubscriptionPurchase testPurchase;
    private String testPayload;
    private String testEventId;
    private String testEventType;

    @BeforeEach
    void setUp() {
        // Setup test subscription
        testPlan = new SubscriptionPlan();
        testPlan.setId(UUID.randomUUID());
        testPlan.setName("Plus Monthly");
        testPlan.setGooglePlayProductId("plus_monthly");

        testSubscription = new UserSubscription();
        testSubscription.setId(UUID.randomUUID());
        testSubscription.setUser(null); // Not needed for these tests
        testSubscription.setSubscriptionPlan(testPlan);
        testSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        testSubscription.setExternalSubscriptionId("test_purchase_token");
        testSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        testSubscription.setCurrentPeriodStart(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));
        testSubscription.setCurrentPeriodEnd(Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));
        testSubscription.setAutoRenew(true);
        testSubscription.setStartDate(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));

        // Setup test purchase
        testPurchase = new GooglePlaySubscriptionPurchase();
        testPurchase.setPurchaseState("PURCHASED");
        testPurchase.setStartTimeMillis(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS).toEpochMilli());
        testPurchase.setExpiryTimeMillis(Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS).toEpochMilli());
        testPurchase.setAutoRenewing(true);

        // Setup test payload
        testEventId = "test_message_id_123";
        testEventType = "SUBSCRIPTION_RENEWED";
        
        String notificationData = """
            {
                "subscriptionNotification": {
                    "version": "1.0",
                    "notificationType": 2,
                    "purchaseToken": "test_purchase_token",
                    "subscriptionId": "plus_monthly"
                }
            }
            """;
        
        String encodedData = Base64.getEncoder().encodeToString(notificationData.getBytes());
        
        testPayload = """
            {
                "message": {
                    "messageId": "test_message_id_123",
                    "data": "%s"
                }
            }
            """.formatted(encodedData);
    }

    @Test
    @DisplayName("verifyGooglePlaySignature - returns false when authorization is null")
    void verifyGooglePlaySignature_returnsFalseWhenAuthorizationIsNull() {
        // When
        boolean result = webhookProcessingService.verifyGooglePlaySignature(null, testPayload);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyGooglePlaySignature - returns false when authorization does not start with Bearer")
    void verifyGooglePlaySignature_returnsFalseWhenAuthorizationDoesNotStartWithBearer() {
        // When
        boolean result = webhookProcessingService.verifyGooglePlaySignature("Invalid token", testPayload);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyGooglePlaySignature - returns false when Bearer token is invalid JWT")
    void verifyGooglePlaySignature_returnsFalseWhenBearerTokenIsInvalidJWT() {
        // When
        boolean result = webhookProcessingService.verifyGooglePlaySignature("Bearer invalid_jwt_token", testPayload);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("extractGooglePlayEventId - returns messageId from valid payload")
    void extractGooglePlayEventId_returnsMessageIdFromValidPayload() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode messageIdNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.get("messageId")).thenReturn(messageIdNode);
        when(messageIdNode.asText()).thenReturn(testEventId);

        // When
        String result = webhookProcessingService.extractGooglePlayEventId(testPayload);

        // Then
        assertThat(result).isEqualTo(testEventId);
    }

    @Test
    @DisplayName("extractGooglePlayEventId - returns null when message is missing")
    void extractGooglePlayEventId_returnsNullWhenMessageIsMissing() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(null);

        // When
        String result = webhookProcessingService.extractGooglePlayEventId(testPayload);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("extractGooglePlayEventId - returns null and logs error when payload is malformed")
    void extractGooglePlayEventId_returnsNullAndLogsErrorWhenPayloadIsMalformed() throws Exception {
        // Given
        when(objectMapper.readTree("invalid json")).thenThrow(new RuntimeException("Invalid JSON"));

        // When
        String result = webhookProcessingService.extractGooglePlayEventId("invalid json");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("extractGooglePlayEventType - returns correct event type for notification type 2")
    void extractGooglePlayEventType_returnsCorrectEventTypeForNotificationType2() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        JsonNode notificationNode = mock(JsonNode.class);
        JsonNode notificationTypeNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.has("data")).thenReturn(true);
        when(messageNode.get("data")).thenReturn(dataNode);
        when(dataNode.asText()).thenReturn(Base64.getEncoder().encodeToString("test data".getBytes()));
        
        // Mock decoded data
        JsonNode decodedDataNode = mock(JsonNode.class);
        when(objectMapper.readTree(any(byte[].class))).thenReturn(decodedDataNode);
        when(decodedDataNode.has("subscriptionNotification")).thenReturn(true);
        when(decodedDataNode.get("subscriptionNotification")).thenReturn(notificationNode);
        when(notificationNode.get("notificationType")).thenReturn(notificationTypeNode);
        when(notificationTypeNode.asInt()).thenReturn(2);

        // When
        String result = webhookProcessingService.extractGooglePlayEventType(testPayload);

        // Then
        assertThat(result).isEqualTo("SUBSCRIPTION_RENEWED");
    }

    @Test
    @DisplayName("extractGooglePlayEventType - returns null when subscriptionNotification is missing")
    void extractGooglePlayEventType_returnsNullWhenSubscriptionNotificationIsMissing() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.has("data")).thenReturn(true);
        when(messageNode.get("data")).thenReturn(dataNode);
        when(dataNode.asText()).thenReturn(Base64.getEncoder().encodeToString("test data".getBytes()));
        
        JsonNode decodedDataNode = mock(JsonNode.class);
        when(objectMapper.readTree(any(byte[].class))).thenReturn(decodedDataNode);
        when(decodedDataNode.has("subscriptionNotification")).thenReturn(false);

        // When
        String result = webhookProcessingService.extractGooglePlayEventType(testPayload);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("extractGooglePlayEventType - returns null and logs error when Base64 is malformed")
    void extractGooglePlayEventType_returnsNullAndLogsErrorWhenBase64IsMalformed() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.has("data")).thenReturn(true);
        when(messageNode.get("data")).thenReturn(dataNode);
        when(dataNode.asText()).thenReturn("invalid_base64");

        // When
        String result = webhookProcessingService.extractGooglePlayEventType(testPayload);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("processGooglePlayWebhook - processes SUBSCRIPTION_RENEWED event successfully")
    void processGooglePlayWebhook_processesSubscriptionRenewedEventSuccessfully() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        JsonNode notificationNode = mock(JsonNode.class);
        JsonNode subscriptionIdNode = mock(JsonNode.class);
        JsonNode purchaseTokenNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.has("data")).thenReturn(true);
        when(messageNode.get("data")).thenReturn(dataNode);
        when(dataNode.asText()).thenReturn(Base64.getEncoder().encodeToString("test data".getBytes()));
        
        JsonNode decodedDataNode = mock(JsonNode.class);
        when(objectMapper.readTree(any(byte[].class))).thenReturn(decodedDataNode);
        when(decodedDataNode.has("subscriptionNotification")).thenReturn(true);
        when(decodedDataNode.get("subscriptionNotification")).thenReturn(notificationNode);
        when(notificationNode.get("subscriptionId")).thenReturn(subscriptionIdNode);
        when(notificationNode.get("purchaseToken")).thenReturn(purchaseTokenNode);
        when(subscriptionIdNode.asText()).thenReturn("plus_monthly");
        when(purchaseTokenNode.asText()).thenReturn("test_purchase_token");
        
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                eq(UserSubscription.PaymentProvider.GOOGLE_PLAY), eq("test_purchase_token")))
                .thenReturn(Optional.of(testSubscription));
        
        when(googlePlayClient.getSubscriptionPurchase("plus_monthly", "test_purchase_token"))
                .thenReturn(testPurchase);

        // When
        webhookProcessingService.processGooglePlayWebhook(testEventId, testEventType, testPayload);

        // Then
        ArgumentCaptor<UserSubscription> subscriptionCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(subscriptionCaptor.capture());
        
        UserSubscription savedSubscription = subscriptionCaptor.getValue();
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.ACTIVE);
        assertThat(savedSubscription.getCurrentPeriodStart()).isEqualTo(Instant.ofEpochMilli(testPurchase.getStartTimeMillis()));
        assertThat(savedSubscription.getCurrentPeriodEnd()).isEqualTo(Instant.ofEpochMilli(testPurchase.getExpiryTimeMillis()));
        assertThat(savedSubscription.isAutoRenew()).isEqualTo(testPurchase.getAutoRenewing());
        assertThat(savedSubscription.getProviderStatusRaw()).isEqualTo(testPurchase.getPurchaseState());
    }

    @Test
    @DisplayName("processGooglePlayWebhook - handles SUBSCRIPTION_CANCELED event")
    void processGooglePlayWebhook_handlesSubscriptionCanceledEvent() throws Exception {
        // Given
        testPurchase.setPurchaseState("CANCELED");
        
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        JsonNode notificationNode = mock(JsonNode.class);
        JsonNode subscriptionIdNode = mock(JsonNode.class);
        JsonNode purchaseTokenNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.has("data")).thenReturn(true);
        when(messageNode.get("data")).thenReturn(dataNode);
        when(dataNode.asText()).thenReturn(Base64.getEncoder().encodeToString("test data".getBytes()));
        
        JsonNode decodedDataNode = mock(JsonNode.class);
        when(objectMapper.readTree(any(byte[].class))).thenReturn(decodedDataNode);
        when(decodedDataNode.has("subscriptionNotification")).thenReturn(true);
        when(decodedDataNode.get("subscriptionNotification")).thenReturn(notificationNode);
        when(notificationNode.get("subscriptionId")).thenReturn(subscriptionIdNode);
        when(notificationNode.get("purchaseToken")).thenReturn(purchaseTokenNode);
        when(subscriptionIdNode.asText()).thenReturn("plus_monthly");
        when(purchaseTokenNode.asText()).thenReturn("test_purchase_token");
        
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                eq(UserSubscription.PaymentProvider.GOOGLE_PLAY), eq("test_purchase_token")))
                .thenReturn(Optional.of(testSubscription));
        
        when(googlePlayClient.getSubscriptionPurchase("plus_monthly", "test_purchase_token"))
                .thenReturn(testPurchase);

        // When
        webhookProcessingService.processGooglePlayWebhook(testEventId, "SUBSCRIPTION_CANCELED", testPayload);

        // Then
        ArgumentCaptor<UserSubscription> subscriptionCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(subscriptionCaptor.capture());
        
        UserSubscription savedSubscription = subscriptionCaptor.getValue();
        assertThat(savedSubscription.getStatus()).isEqualTo(UserSubscription.SubscriptionStatus.CANCELLED);
        assertThat(savedSubscription.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("processGooglePlayWebhook - handles SUBSCRIPTION_IN_GRACE_PERIOD event")
    void processGooglePlayWebhook_handlesSubscriptionInGracePeriodEvent() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        JsonNode notificationNode = mock(JsonNode.class);
        JsonNode subscriptionIdNode = mock(JsonNode.class);
        JsonNode purchaseTokenNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.has("data")).thenReturn(true);
        when(messageNode.get("data")).thenReturn(dataNode);
        when(dataNode.asText()).thenReturn(Base64.getEncoder().encodeToString("test data".getBytes()));
        
        JsonNode decodedDataNode = mock(JsonNode.class);
        when(objectMapper.readTree(any(byte[].class))).thenReturn(decodedDataNode);
        when(decodedDataNode.has("subscriptionNotification")).thenReturn(true);
        when(decodedDataNode.get("subscriptionNotification")).thenReturn(notificationNode);
        when(notificationNode.get("subscriptionId")).thenReturn(subscriptionIdNode);
        when(notificationNode.get("purchaseToken")).thenReturn(purchaseTokenNode);
        when(subscriptionIdNode.asText()).thenReturn("plus_monthly");
        when(purchaseTokenNode.asText()).thenReturn("test_purchase_token");
        
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                eq(UserSubscription.PaymentProvider.GOOGLE_PLAY), eq("test_purchase_token")))
                .thenReturn(Optional.of(testSubscription));
        
        when(googlePlayClient.getSubscriptionPurchase("plus_monthly", "test_purchase_token"))
                .thenReturn(testPurchase);

        // When
        webhookProcessingService.processGooglePlayWebhook(testEventId, "SUBSCRIPTION_IN_GRACE_PERIOD", testPayload);

        // Then
        ArgumentCaptor<UserSubscription> subscriptionCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(subscriptionCaptor.capture());
        
        UserSubscription savedSubscription = subscriptionCaptor.getValue();
        assertThat(savedSubscription.getGracePeriodEnd()).isNotNull();
        assertThat(savedSubscription.getGracePeriodEnd()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("processGooglePlayWebhook - handles SUBSCRIPTION_PAUSED event")
    void processGooglePlayWebhook_handlesSubscriptionPausedEvent() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        JsonNode notificationNode = mock(JsonNode.class);
        JsonNode subscriptionIdNode = mock(JsonNode.class);
        JsonNode purchaseTokenNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.has("data")).thenReturn(true);
        when(messageNode.get("data")).thenReturn(dataNode);
        when(dataNode.asText()).thenReturn(Base64.getEncoder().encodeToString("test data".getBytes()));
        
        JsonNode decodedDataNode = mock(JsonNode.class);
        when(objectMapper.readTree(any(byte[].class))).thenReturn(decodedDataNode);
        when(decodedDataNode.has("subscriptionNotification")).thenReturn(true);
        when(decodedDataNode.get("subscriptionNotification")).thenReturn(notificationNode);
        when(notificationNode.get("subscriptionId")).thenReturn(subscriptionIdNode);
        when(notificationNode.get("purchaseToken")).thenReturn(purchaseTokenNode);
        when(subscriptionIdNode.asText()).thenReturn("plus_monthly");
        when(purchaseTokenNode.asText()).thenReturn("test_purchase_token");
        
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                eq(UserSubscription.PaymentProvider.GOOGLE_PLAY), eq("test_purchase_token")))
                .thenReturn(Optional.of(testSubscription));
        
        when(googlePlayClient.getSubscriptionPurchase("plus_monthly", "test_purchase_token"))
                .thenReturn(testPurchase);

        // When
        webhookProcessingService.processGooglePlayWebhook(testEventId, "SUBSCRIPTION_PAUSED", testPayload);

        // Then
        ArgumentCaptor<UserSubscription> subscriptionCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(subscriptionCaptor.capture());
        
        UserSubscription savedSubscription = subscriptionCaptor.getValue();
        assertThat(savedSubscription.getPausedAt()).isNotNull();
    }

    @Test
    @DisplayName("processGooglePlayWebhook - handles SUBSCRIPTION_REVOKED event")
    void processGooglePlayWebhook_handlesSubscriptionRevokedEvent() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        JsonNode notificationNode = mock(JsonNode.class);
        JsonNode subscriptionIdNode = mock(JsonNode.class);
        JsonNode purchaseTokenNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.has("data")).thenReturn(true);
        when(messageNode.get("data")).thenReturn(dataNode);
        when(dataNode.asText()).thenReturn(Base64.getEncoder().encodeToString("test data".getBytes()));
        
        JsonNode decodedDataNode = mock(JsonNode.class);
        when(objectMapper.readTree(any(byte[].class))).thenReturn(decodedDataNode);
        when(decodedDataNode.has("subscriptionNotification")).thenReturn(true);
        when(decodedDataNode.get("subscriptionNotification")).thenReturn(notificationNode);
        when(notificationNode.get("subscriptionId")).thenReturn(subscriptionIdNode);
        when(notificationNode.get("purchaseToken")).thenReturn(purchaseTokenNode);
        when(subscriptionIdNode.asText()).thenReturn("plus_monthly");
        when(purchaseTokenNode.asText()).thenReturn("test_purchase_token");
        
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                eq(UserSubscription.PaymentProvider.GOOGLE_PLAY), eq("test_purchase_token")))
                .thenReturn(Optional.of(testSubscription));
        
        when(googlePlayClient.getSubscriptionPurchase("plus_monthly", "test_purchase_token"))
                .thenReturn(testPurchase);

        // When
        webhookProcessingService.processGooglePlayWebhook(testEventId, "SUBSCRIPTION_REVOKED", testPayload);

        // Then
        ArgumentCaptor<UserSubscription> subscriptionCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(subscriptionCaptor.capture());
        
        UserSubscription savedSubscription = subscriptionCaptor.getValue();
        assertThat(savedSubscription.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("processGooglePlayWebhook - logs warning when subscription not found")
    void processGooglePlayWebhook_logsWarningWhenSubscriptionNotFound() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        JsonNode notificationNode = mock(JsonNode.class);
        JsonNode subscriptionIdNode = mock(JsonNode.class);
        JsonNode purchaseTokenNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.has("data")).thenReturn(true);
        when(messageNode.get("data")).thenReturn(dataNode);
        when(dataNode.asText()).thenReturn(Base64.getEncoder().encodeToString("test data".getBytes()));
        
        JsonNode decodedDataNode = mock(JsonNode.class);
        when(objectMapper.readTree(any(byte[].class))).thenReturn(decodedDataNode);
        when(decodedDataNode.has("subscriptionNotification")).thenReturn(true);
        when(decodedDataNode.get("subscriptionNotification")).thenReturn(notificationNode);
        when(notificationNode.get("subscriptionId")).thenReturn(subscriptionIdNode);
        when(notificationNode.get("purchaseToken")).thenReturn(purchaseTokenNode);
        when(subscriptionIdNode.asText()).thenReturn("plus_monthly");
        when(purchaseTokenNode.asText()).thenReturn("test_purchase_token");
        
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                eq(UserSubscription.PaymentProvider.GOOGLE_PLAY), eq("test_purchase_token")))
                .thenReturn(Optional.empty());

        // When
        webhookProcessingService.processGooglePlayWebhook(testEventId, testEventType, testPayload);

        // Then
        verify(userSubscriptionRepository, never()).save(any(UserSubscription.class));
        verify(googlePlayClient, never()).getSubscriptionPurchase(anyString(), anyString());
    }

    @Test
    @DisplayName("processGooglePlayWebhook - saves providerStatusRaw when GooglePlayClient throws exception")
    void processGooglePlayWebhook_savesProviderStatusRawWhenGooglePlayClientThrowsException() throws Exception {
        // Given
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        JsonNode notificationNode = mock(JsonNode.class);
        JsonNode subscriptionIdNode = mock(JsonNode.class);
        JsonNode purchaseTokenNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(testPayload)).thenReturn(rootNode);
        when(rootNode.get("message")).thenReturn(messageNode);
        when(messageNode.has("data")).thenReturn(true);
        when(messageNode.get("data")).thenReturn(dataNode);
        when(dataNode.asText()).thenReturn(Base64.getEncoder().encodeToString("test data".getBytes()));
        
        JsonNode decodedDataNode = mock(JsonNode.class);
        when(objectMapper.readTree(any(byte[].class))).thenReturn(decodedDataNode);
        when(decodedDataNode.has("subscriptionNotification")).thenReturn(true);
        when(decodedDataNode.get("subscriptionNotification")).thenReturn(notificationNode);
        when(notificationNode.get("subscriptionId")).thenReturn(subscriptionIdNode);
        when(notificationNode.get("purchaseToken")).thenReturn(purchaseTokenNode);
        when(subscriptionIdNode.asText()).thenReturn("plus_monthly");
        when(purchaseTokenNode.asText()).thenReturn("test_purchase_token");
        
        when(userSubscriptionRepository.findByPaymentProviderAndExternalSubscriptionId(
                eq(UserSubscription.PaymentProvider.GOOGLE_PLAY), eq("test_purchase_token")))
                .thenReturn(Optional.of(testSubscription));
        
        when(googlePlayClient.getSubscriptionPurchase("plus_monthly", "test_purchase_token"))
                .thenThrow(new RuntimeException("Google Play API error"));

        // When
        webhookProcessingService.processGooglePlayWebhook(testEventId, testEventType, testPayload);

        // Then
        ArgumentCaptor<UserSubscription> subscriptionCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionRepository).save(subscriptionCaptor.capture());
        
        UserSubscription savedSubscription = subscriptionCaptor.getValue();
        assertThat(savedSubscription.getProviderStatusRaw()).isEqualTo(testEventType);
    }

    @Test
    @DisplayName("processGooglePlayWebhook - throws RuntimeException when payload is malformed")
    void processGooglePlayWebhook_throwsRuntimeExceptionWhenPayloadIsMalformed() throws Exception {
        // Given
        when(objectMapper.readTree("invalid json")).thenThrow(new RuntimeException("Invalid JSON"));

        // When & Then
        assertThatThrownBy(() -> webhookProcessingService.processGooglePlayWebhook(testEventId, testEventType, "invalid json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to process Google Play webhook");
    }
}
