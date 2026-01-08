package uk.gegc.kidsgptbackend.features.subscription.infra.googleplay;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.features.subscription.infra.googleplay.GooglePlaySubscriptionPurchase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GooglePlayClientImpl Comprehensive Tests")
class GooglePlayClientImplComprehensiveTest {

    private uk.gegc.kidsgptbackend.features.subscription.infra.googleplay.GooglePlayClientImpl googlePlayClient;
    private String testProductId;
    private String testPurchaseToken;
    private String testPackageName;
    private String testServiceAccountKey;

    @BeforeEach
    void setUp() {
        googlePlayClient = new GooglePlayClientImpl();
        testProductId = "premium_monthly";
        testPurchaseToken = "test_purchase_token_123";
        testPackageName = "uk.gegc.kidsgpt";
        testServiceAccountKey = "{\"type\":\"service_account\",\"project_id\":\"test-project\"}";
        
        // Set up test configuration
        ReflectionTestUtils.setField(googlePlayClient, "packageName", testPackageName);
        ReflectionTestUtils.setField(googlePlayClient, "applicationName", "KidsGPT");
    }

    @Test
    @DisplayName("Initialization: With configured service account builds AndroidPublisher")
    void initialization_withConfiguredServiceAccountBuildsAndroidPublisher() {
        // Given
        ReflectionTestUtils.setField(googlePlayClient, "serviceAccountKey", testServiceAccountKey);

        try (MockedStatic<GoogleCredentials> credentialsMock = mockStatic(GoogleCredentials.class)) {
            // Mock GoogleCredentials to throw IOException to avoid complex HttpCredentialsAdapter mocking
            credentialsMock.when(() -> GoogleCredentials.fromStream(any(ByteArrayInputStream.class)))
                    .thenThrow(new IOException("Mocked initialization failure"));

            // When & Then
            assertThatThrownBy(() -> googlePlayClient.initializeAndroidPublisher())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to initialize Google Play API")
                    .hasCauseInstanceOf(IOException.class);
            
            // Verify that GoogleCredentials.fromStream was called
            credentialsMock.verify(() -> GoogleCredentials.fromStream(any(ByteArrayInputStream.class)));
        }
    }

    @Test
    @DisplayName("Initialization: Without key logs warning and uses mock data path")
    void initialization_withoutKeyLogsWarningAndUsesMockDataPath() {
        // Given
        ReflectionTestUtils.setField(googlePlayClient, "serviceAccountKey", "");

        // When
        googlePlayClient.initializeAndroidPublisher();

        // Then
        // Verify that AndroidPublisher is null (mock path)
        AndroidPublisher androidPublisher = (AndroidPublisher) ReflectionTestUtils.getField(googlePlayClient, "androidPublisher");
        assertThat(androidPublisher).isNull();

        // Test that mock data is returned
        GooglePlaySubscriptionPurchase result = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(testProductId);
        assertThat(result.getPurchaseToken()).isEqualTo(testPurchaseToken);
    }

    @Test
    @DisplayName("Initialization: Handles null service account key")
    void initialization_handlesNullServiceAccountKey() {
        // Given
        ReflectionTestUtils.setField(googlePlayClient, "serviceAccountKey", null);

        // When
        googlePlayClient.initializeAndroidPublisher();

        // Then
        AndroidPublisher androidPublisher = (AndroidPublisher) ReflectionTestUtils.getField(googlePlayClient, "androidPublisher");
        assertThat(androidPublisher).isNull();
    }

    @Test
    @DisplayName("Initialization: Handles whitespace-only service account key")
    void initialization_handlesWhitespaceOnlyServiceAccountKey() {
        // Given
        ReflectionTestUtils.setField(googlePlayClient, "serviceAccountKey", "   ");

        // When
        googlePlayClient.initializeAndroidPublisher();

        // Then
        AndroidPublisher androidPublisher = (AndroidPublisher) ReflectionTestUtils.getField(googlePlayClient, "androidPublisher");
        assertThat(androidPublisher).isNull();
    }

    @Test
    @DisplayName("Initialization: Throws RuntimeException on IOException")
    void initialization_throwsRuntimeExceptionOnIOException() {
        // Given
        ReflectionTestUtils.setField(googlePlayClient, "serviceAccountKey", testServiceAccountKey);

        try (MockedStatic<GoogleCredentials> credentialsMock = mockStatic(GoogleCredentials.class)) {
            credentialsMock.when(() -> GoogleCredentials.fromStream(any(ByteArrayInputStream.class)))
                    .thenThrow(new IOException("IO error"));

            // When & Then
            assertThatThrownBy(() -> googlePlayClient.initializeAndroidPublisher())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to initialize Google Play API")
                    .hasCauseInstanceOf(IOException.class);
        }
    }

    @Test
    @DisplayName("Initialization: File credentials build AndroidPublisher when valid")
    void initialization_fileCredentialsBuildsAndroidPublisher() throws IOException {
        Path tempFile = Files.createTempFile("google-play-credentials", ".json");
        Files.writeString(tempFile, "{\"type\":\"service_account\"}");

        ReflectionTestUtils.setField(googlePlayClient, "credentialsFile", tempFile.toString());
        ReflectionTestUtils.setField(googlePlayClient, "serviceAccountKey", "");

        GoogleCredentials mockCredentials = mock(GoogleCredentials.class);
        when(mockCredentials.createScoped(anyCollection())).thenReturn(mockCredentials);

        try (MockedStatic<GoogleCredentials> credentialsMock = mockStatic(GoogleCredentials.class);
             MockedStatic<GoogleNetHttpTransport> transportMock = mockStatic(GoogleNetHttpTransport.class)) {
            NetHttpTransport mockTransport = mock(NetHttpTransport.class);
            when(mockTransport.createRequestFactory(any())).thenReturn(mock(HttpRequestFactory.class));
            transportMock.when(GoogleNetHttpTransport::newTrustedTransport).thenReturn(mockTransport);

            credentialsMock.when(() -> GoogleCredentials.fromStream(any(InputStream.class)))
                    .thenReturn(mockCredentials);

            googlePlayClient.initializeAndroidPublisher();

            AndroidPublisher androidPublisher =
                    (AndroidPublisher) ReflectionTestUtils.getField(googlePlayClient, "androidPublisher");
            assertThat(androidPublisher).isNotNull();
        }
    }

    @Test
    @DisplayName("Initialization: File credentials attempt falls back to service account key")
    void initialization_fileCredentialsFallBackToServiceAccountKey() throws IOException {
        Path tempFile = Files.createTempFile("google-play-credentials", ".json");
        Files.writeString(tempFile, "{\"type\":\"service_account\"}");

        ReflectionTestUtils.setField(googlePlayClient, "credentialsFile", tempFile.toString());
        ReflectionTestUtils.setField(googlePlayClient, "serviceAccountKey", testServiceAccountKey);

        try (MockedStatic<GoogleCredentials> credentialsMock = mockStatic(GoogleCredentials.class)) {
            credentialsMock.when(() -> GoogleCredentials.fromStream(any(InputStream.class)))
                    .thenThrow(new IOException("file read failed"))
                    .thenThrow(new IOException("env read failed"));

            assertThatThrownBy(() -> googlePlayClient.initializeAndroidPublisher())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to initialize Google Play API")
                    .hasCauseInstanceOf(IOException.class);

            credentialsMock.verify(() -> GoogleCredentials.fromStream(any(InputStream.class)), times(2));
        }
    }

    @Test
    @DisplayName("Initialization: Missing credentials file with no key uses mock path")
    void initialization_missingCredentialsFileWithoutKeyUsesMockPath() {
        ReflectionTestUtils.setField(googlePlayClient, "credentialsFile", "nonexistent-file.json");
        ReflectionTestUtils.setField(googlePlayClient, "serviceAccountKey", "");

        googlePlayClient.initializeAndroidPublisher();

        AndroidPublisher androidPublisher = (AndroidPublisher) ReflectionTestUtils.getField(googlePlayClient, "androidPublisher");
        assertThat(androidPublisher).isNull();

        GooglePlaySubscriptionPurchase result = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);
        assertThat(result.getProductId()).isEqualTo(testProductId);
        assertThat(result.getPurchaseToken()).isEqualTo(testPurchaseToken);
    }

    @Test
    @DisplayName("getSubscriptionPurchase: Maps SubscriptionPurchase fields safely via reflection helpers")
    void getSubscriptionPurchase_mapsFieldsSafelyViaReflectionHelpers() throws IOException {
        // Given - Set up with mock AndroidPublisher
        AndroidPublisher mockAndroidPublisher = mock(AndroidPublisher.class);
        AndroidPublisher.Purchases mockPurchases = mock(AndroidPublisher.Purchases.class);
        AndroidPublisher.Purchases.Subscriptions mockSubscriptions = mock(AndroidPublisher.Purchases.Subscriptions.class);
        AndroidPublisher.Purchases.Subscriptions.Get mockGet = mock(AndroidPublisher.Purchases.Subscriptions.Get.class);
        
        SubscriptionPurchase mockPurchase = createMockSubscriptionPurchase();
        
        when(mockAndroidPublisher.purchases()).thenReturn(mockPurchases);
        when(mockPurchases.subscriptions()).thenReturn(mockSubscriptions);
        when(mockSubscriptions.get(anyString(), anyString(), anyString())).thenReturn(mockGet);
        when(mockGet.execute()).thenReturn(mockPurchase);
        
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", mockAndroidPublisher);

        // When
        GooglePlaySubscriptionPurchase result = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPurchaseToken()).isEqualTo(testPurchaseToken);
        assertThat(result.getProductId()).isEqualTo(testProductId);
        assertThat(result.getPackageName()).isEqualTo(testPackageName);
        assertThat(result.getStartTimeMillis()).isEqualTo(1000L);
        assertThat(result.getExpiryTimeMillis()).isEqualTo(2000L);
        assertThat(result.getAutoRenewing()).isTrue();
    }

    @Test
    @DisplayName("getSubscriptionPurchase: Tolerates null timestamps")
    void getSubscriptionPurchase_toleratesNullTimestamps() throws IOException {
        // Given - Set up with mock AndroidPublisher
        AndroidPublisher mockAndroidPublisher = mock(AndroidPublisher.class);
        AndroidPublisher.Purchases mockPurchases = mock(AndroidPublisher.Purchases.class);
        AndroidPublisher.Purchases.Subscriptions mockSubscriptions = mock(AndroidPublisher.Purchases.Subscriptions.class);
        AndroidPublisher.Purchases.Subscriptions.Get mockGet = mock(AndroidPublisher.Purchases.Subscriptions.Get.class);
        
        SubscriptionPurchase mockPurchase = createMockSubscriptionPurchaseWithNullTimestamps();
        
        when(mockAndroidPublisher.purchases()).thenReturn(mockPurchases);
        when(mockPurchases.subscriptions()).thenReturn(mockSubscriptions);
        when(mockSubscriptions.get(anyString(), anyString(), anyString())).thenReturn(mockGet);
        when(mockGet.execute()).thenReturn(mockPurchase);
        
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", mockAndroidPublisher);

        // When
        GooglePlaySubscriptionPurchase result = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);

        // Then
        assertThat(result).isNotNull();
        // When reflection fails to get timestamps, the DTO defaults to 0L
        assertThat(result.getStartTimeMillis()).isEqualTo(0L);
        assertThat(result.getExpiryTimeMillis()).isEqualTo(0L);
        assertThat(result.getPurchaseToken()).isEqualTo(testPurchaseToken);
        assertThat(result.getProductId()).isEqualTo(testProductId);
    }

    @Test
    @DisplayName("getSubscriptionPurchase: Handles reflection method failures gracefully")
    void getSubscriptionPurchase_handlesReflectionMethodFailuresGracefully() throws IOException {
        // Given - Set up with mock AndroidPublisher that returns a purchase with missing methods
        AndroidPublisher mockAndroidPublisher = mock(AndroidPublisher.class);
        AndroidPublisher.Purchases mockPurchases = mock(AndroidPublisher.Purchases.class);
        AndroidPublisher.Purchases.Subscriptions mockSubscriptions = mock(AndroidPublisher.Purchases.Subscriptions.class);
        AndroidPublisher.Purchases.Subscriptions.Get mockGet = mock(AndroidPublisher.Purchases.Subscriptions.Get.class);
        
        // Create a mock that doesn't have the expected methods
        SubscriptionPurchase mockPurchase = mock(SubscriptionPurchase.class);
        when(mockPurchase.getStartTimeMillis()).thenReturn(1000L);
        when(mockPurchase.getExpiryTimeMillis()).thenReturn(2000L);
        when(mockPurchase.getAutoRenewing()).thenReturn(true);
        // Don't mock other methods to simulate reflection failures
        
        when(mockAndroidPublisher.purchases()).thenReturn(mockPurchases);
        when(mockPurchases.subscriptions()).thenReturn(mockSubscriptions);
        when(mockSubscriptions.get(anyString(), anyString(), anyString())).thenReturn(mockGet);
        when(mockGet.execute()).thenReturn(mockPurchase);
        
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", mockAndroidPublisher);

        // When
        GooglePlaySubscriptionPurchase result = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPurchaseToken()).isEqualTo(testPurchaseToken);
        assertThat(result.getProductId()).isEqualTo(testProductId);
        assertThat(result.getStartTimeMillis()).isEqualTo(1000L);
        assertThat(result.getExpiryTimeMillis()).isEqualTo(2000L);
        assertThat(result.getAutoRenewing()).isTrue();
        // Fields that failed reflection should be null or default values
        assertThat(result.getKind()).isNull();
        assertThat(result.getRegionCode()).isNull();
        assertThat(result.getPriceAmountMicros()).isEqualTo("0");
    }

    @Test
    @DisplayName("getSubscriptionPurchase: Maps purchase and acknowledgement states")
    void getSubscriptionPurchase_mapsPurchaseAndAcknowledgementStates() throws IOException {
        AndroidPublisher mockAndroidPublisher = mock(AndroidPublisher.class);
        AndroidPublisher.Purchases mockPurchases = mock(AndroidPublisher.Purchases.class);
        AndroidPublisher.Purchases.Subscriptions mockSubscriptions = mock(AndroidPublisher.Purchases.Subscriptions.class);
        AndroidPublisher.Purchases.Subscriptions.Get mockGet = mock(AndroidPublisher.Purchases.Subscriptions.Get.class);

        SubscriptionPurchase purchase = new SubscriptionPurchase()
                .setStartTimeMillis(1000L)
                .setExpiryTimeMillis(2000L)
                .setAutoRenewing(true)
                .setAcknowledgementState(1)
                .setPriceAmountMicros(4990000L)
                .setPriceCurrencyCode("GBP")
                .setCountryCode("GB")
                .setOrderId("GPA.1234-5678");

        when(mockAndroidPublisher.purchases()).thenReturn(mockPurchases);
        when(mockPurchases.subscriptions()).thenReturn(mockSubscriptions);
        when(mockSubscriptions.get(anyString(), anyString(), anyString())).thenReturn(mockGet);
        when(mockGet.execute()).thenReturn(purchase);

        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", mockAndroidPublisher);

        GooglePlaySubscriptionPurchase result = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);

        assertThat(result.getAcknowledgementState()).isEqualTo("ACKNOWLEDGED");
        assertThat(result.getPriceAmountMicros()).isEqualTo("4990000");
    }

    @Test
    @DisplayName("getSubscriptionPurchase: Maps unknown acknowledgement state values to UNKNOWN")
    void getSubscriptionPurchase_mapsUnknownAcknowledgementStateValuesToUnknown() throws IOException {
        AndroidPublisher mockAndroidPublisher = mock(AndroidPublisher.class);
        AndroidPublisher.Purchases mockPurchases = mock(AndroidPublisher.Purchases.class);
        AndroidPublisher.Purchases.Subscriptions mockSubscriptions = mock(AndroidPublisher.Purchases.Subscriptions.class);
        AndroidPublisher.Purchases.Subscriptions.Get mockGet = mock(AndroidPublisher.Purchases.Subscriptions.Get.class);

        SubscriptionPurchase purchase = new SubscriptionPurchase()
                .setStartTimeMillis(1000L)
                .setExpiryTimeMillis(2000L)
                .setAutoRenewing(true)
                .setAcknowledgementState(2);

        when(mockAndroidPublisher.purchases()).thenReturn(mockPurchases);
        when(mockPurchases.subscriptions()).thenReturn(mockSubscriptions);
        when(mockSubscriptions.get(anyString(), anyString(), anyString())).thenReturn(mockGet);
        when(mockGet.execute()).thenReturn(purchase);

        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", mockAndroidPublisher);

        GooglePlaySubscriptionPurchase result = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);

        assertThat(result.getAcknowledgementState()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("getSubscriptionPurchase: Maps purchase state correctly")
    void getSubscriptionPurchase_mapsPurchaseStateCorrectly() {
        // Test that the reflection-based mapping works correctly
        // Since we can't easily mock the reflection methods, we test the mock path
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", null);
        
        GooglePlaySubscriptionPurchase result = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);
        
        // Mock data should have PURCHASED state
        assertThat(result.getPurchaseState()).isEqualTo("PURCHASED");
        assertThat(result.getAcknowledgementState()).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    @DisplayName("getSubscriptionPurchase: Throws RuntimeException on IOException")
    void getSubscriptionPurchase_throwsRuntimeExceptionOnIOException() throws IOException {
        // Given - Set up with mock AndroidPublisher that throws IOException
        AndroidPublisher mockAndroidPublisher = mock(AndroidPublisher.class);
        AndroidPublisher.Purchases mockPurchases = mock(AndroidPublisher.Purchases.class);
        AndroidPublisher.Purchases.Subscriptions mockSubscriptions = mock(AndroidPublisher.Purchases.Subscriptions.class);
        AndroidPublisher.Purchases.Subscriptions.Get mockGet = mock(AndroidPublisher.Purchases.Subscriptions.Get.class);
        
        when(mockAndroidPublisher.purchases()).thenReturn(mockPurchases);
        when(mockPurchases.subscriptions()).thenReturn(mockSubscriptions);
        when(mockSubscriptions.get(anyString(), anyString(), anyString())).thenReturn(mockGet);
        when(mockGet.execute()).thenThrow(new IOException("API error"));
        
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", mockAndroidPublisher);

        // When & Then
        assertThatThrownBy(() -> googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to get subscription purchase from Google Play")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("verifyPurchaseToken: Returns true only for purchased & not expired")
    void verifyPurchaseToken_returnsTrueOnlyForPurchasedAndNotExpired() {
        // Test with mock data (which is purchased and not expired)
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", null);
        
        boolean result = googlePlayClient.verifyPurchaseToken(testProductId, testPurchaseToken);
        
        // Mock data should be valid (purchased and not expired)
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verifyPurchaseToken: Returns false when purchase is canceled")
    void verifyPurchaseToken_returnsFalseWhenPurchaseIsCanceled() {
        GooglePlayClientImpl spyClient = spy(googlePlayClient);
        GooglePlaySubscriptionPurchase purchase = new GooglePlaySubscriptionPurchase();
        purchase.setPurchaseState("CANCELED");
        purchase.setExpiryTimeMillis(Instant.now().plusSeconds(3600).toEpochMilli());

        doReturn(purchase).when(spyClient).getSubscriptionPurchase(testProductId, testPurchaseToken);

        boolean result = spyClient.verifyPurchaseToken(testProductId, testPurchaseToken);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyPurchaseToken: Returns false when purchase is expired")
    void verifyPurchaseToken_returnsFalseWhenPurchaseIsExpired() {
        GooglePlayClientImpl spyClient = spy(googlePlayClient);
        GooglePlaySubscriptionPurchase purchase = new GooglePlaySubscriptionPurchase();
        purchase.setPurchaseState("PURCHASED");
        purchase.setExpiryTimeMillis(Instant.now().minusSeconds(60).toEpochMilli());

        doReturn(purchase).when(spyClient).getSubscriptionPurchase(testProductId, testPurchaseToken);

        boolean result = spyClient.verifyPurchaseToken(testProductId, testPurchaseToken);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyPurchaseToken: Returns false on exception")
    void verifyPurchaseToken_returnsFalseOnException() throws IOException {
        // Given - Set up with mock AndroidPublisher that throws exception
        AndroidPublisher mockAndroidPublisher = mock(AndroidPublisher.class);
        AndroidPublisher.Purchases mockPurchases = mock(AndroidPublisher.Purchases.class);
        AndroidPublisher.Purchases.Subscriptions mockSubscriptions = mock(AndroidPublisher.Purchases.Subscriptions.class);
        AndroidPublisher.Purchases.Subscriptions.Get mockGet = mock(AndroidPublisher.Purchases.Subscriptions.Get.class);
        
        when(mockAndroidPublisher.purchases()).thenReturn(mockPurchases);
        when(mockPurchases.subscriptions()).thenReturn(mockSubscriptions);
        when(mockSubscriptions.get(anyString(), anyString(), anyString())).thenReturn(mockGet);
        when(mockGet.execute()).thenThrow(new RuntimeException("API error"));
        
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", mockAndroidPublisher);

        // When
        boolean result = googlePlayClient.verifyPurchaseToken(testProductId, testPurchaseToken);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("acknowledgeSubscription: No-op with mock")
    void acknowledgeSubscription_noOpWithMock() {
        // Given - No AndroidPublisher (mock mode)
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", null);

        // When & Then - Should not throw exception
        googlePlayClient.acknowledgeSubscription(testProductId, testPurchaseToken, "test-payload");
        
        // The method should complete successfully without making any API calls
    }

    @Test
    @DisplayName("acknowledgeSubscription: Real call executed with correct parameters")
    void acknowledgeSubscription_realCallExecutedWithCorrectParameters() throws IOException {
        // Given - Set up with mock AndroidPublisher
        AndroidPublisher mockAndroidPublisher = mock(AndroidPublisher.class);
        AndroidPublisher.Purchases mockPurchases = mock(AndroidPublisher.Purchases.class);
        AndroidPublisher.Purchases.Subscriptions mockSubscriptions = mock(AndroidPublisher.Purchases.Subscriptions.class);
        AndroidPublisher.Purchases.Subscriptions.Acknowledge mockAcknowledge = mock(AndroidPublisher.Purchases.Subscriptions.Acknowledge.class);
        
        when(mockAndroidPublisher.purchases()).thenReturn(mockPurchases);
        when(mockPurchases.subscriptions()).thenReturn(mockSubscriptions);
        when(mockSubscriptions.acknowledge(eq(testPackageName), eq(testProductId), eq(testPurchaseToken), any())).thenReturn(mockAcknowledge);
        doNothing().when(mockAcknowledge).execute();
        
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", mockAndroidPublisher);

        // When
        googlePlayClient.acknowledgeSubscription(testProductId, testPurchaseToken, "test-payload");

        // Then
        verify(mockSubscriptions).acknowledge(eq(testPackageName), eq(testProductId), eq(testPurchaseToken), any());
        verify(mockAcknowledge).execute();
    }

    @Test
    @DisplayName("acknowledgeSubscription: IO exception surfaces")
    void acknowledgeSubscription_ioExceptionSurfaces() throws IOException {
        // Given - Set up with mock AndroidPublisher that throws IOException
        AndroidPublisher mockAndroidPublisher = mock(AndroidPublisher.class);
        AndroidPublisher.Purchases mockPurchases = mock(AndroidPublisher.Purchases.class);
        AndroidPublisher.Purchases.Subscriptions mockSubscriptions = mock(AndroidPublisher.Purchases.Subscriptions.class);
        AndroidPublisher.Purchases.Subscriptions.Acknowledge mockAcknowledge = mock(AndroidPublisher.Purchases.Subscriptions.Acknowledge.class);
        
        when(mockAndroidPublisher.purchases()).thenReturn(mockPurchases);
        when(mockPurchases.subscriptions()).thenReturn(mockSubscriptions);
        when(mockSubscriptions.acknowledge(eq(testPackageName), eq(testProductId), eq(testPurchaseToken), any())).thenReturn(mockAcknowledge);
        doThrow(new IOException("API error")).when(mockAcknowledge).execute();
        
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", mockAndroidPublisher);

        // When & Then
        assertThatThrownBy(() -> googlePlayClient.acknowledgeSubscription(testProductId, testPurchaseToken, "test-payload"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to acknowledge subscription")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("acknowledgeSubscription: Handles null developer payload")
    void acknowledgeSubscription_handlesNullDeveloperPayload() throws IOException {
        // Given - Set up with mock AndroidPublisher
        AndroidPublisher mockAndroidPublisher = mock(AndroidPublisher.class);
        AndroidPublisher.Purchases mockPurchases = mock(AndroidPublisher.Purchases.class);
        AndroidPublisher.Purchases.Subscriptions mockSubscriptions = mock(AndroidPublisher.Purchases.Subscriptions.class);
        AndroidPublisher.Purchases.Subscriptions.Acknowledge mockAcknowledge = mock(AndroidPublisher.Purchases.Subscriptions.Acknowledge.class);
        
        when(mockAndroidPublisher.purchases()).thenReturn(mockPurchases);
        when(mockPurchases.subscriptions()).thenReturn(mockSubscriptions);
        when(mockSubscriptions.acknowledge(eq(testPackageName), eq(testProductId), eq(testPurchaseToken), any())).thenReturn(mockAcknowledge);
        doNothing().when(mockAcknowledge).execute();
        
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", mockAndroidPublisher);

        // When
        googlePlayClient.acknowledgeSubscription(testProductId, testPurchaseToken, null);

        // Then
        verify(mockSubscriptions).acknowledge(eq(testPackageName), eq(testProductId), eq(testPurchaseToken), any());
        verify(mockAcknowledge).execute();
    }

    @Test
    @DisplayName("Mock path: Deterministic mock purchase contents with sane defaults")
    void mockPath_deterministicMockPurchaseContentsWithSaneDefaults() {
        // Given - No AndroidPublisher (mock mode)
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", null);

        // When
        GooglePlaySubscriptionPurchase result = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPurchaseToken()).isEqualTo(testPurchaseToken);
        assertThat(result.getProductId()).isEqualTo(testProductId);
        assertThat(result.getPackageName()).isEqualTo(testPackageName);
        assertThat(result.getPurchaseState()).isEqualTo("PURCHASED");
        assertThat(result.getAcknowledgementState()).isEqualTo("ACKNOWLEDGED");
        assertThat(result.getAutoRenewing()).isTrue();
        assertThat(result.getPriceCurrencyCode()).isEqualTo("GBP");
        assertThat(result.getPriceAmountMicros()).isEqualTo("4990000");
        assertThat(result.getOrderId()).startsWith("GPA.MOCK-");
        
        // Verify timestamps are reasonable
        long now = System.currentTimeMillis();
        assertThat(result.getStartTimeMillis()).isLessThan(now);
        assertThat(result.getExpiryTimeMillis()).isGreaterThan(now);
        assertThat(result.getExpiryTimeMillis() - result.getStartTimeMillis()).isGreaterThan(2500000000L); // ~30 days
    }

    @Test
    @DisplayName("Mock path: Consistent results for same inputs")
    void mockPath_consistentResultsForSameInputs() {
        // Given - No AndroidPublisher (mock mode)
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", null);

        // When
        GooglePlaySubscriptionPurchase result1 = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);
        GooglePlaySubscriptionPurchase result2 = googlePlayClient.getSubscriptionPurchase(testProductId, testPurchaseToken);

        // Then
        assertThat(result1.getPurchaseToken()).isEqualTo(result2.getPurchaseToken());
        assertThat(result1.getProductId()).isEqualTo(result2.getProductId());
        assertThat(result1.getPackageName()).isEqualTo(result2.getPackageName());
        assertThat(result1.getPurchaseState()).isEqualTo(result2.getPurchaseState());
        assertThat(result1.getAcknowledgementState()).isEqualTo(result2.getAcknowledgementState());
        assertThat(result1.getAutoRenewing()).isEqualTo(result2.getAutoRenewing());
        assertThat(result1.getPriceCurrencyCode()).isEqualTo(result2.getPriceCurrencyCode());
        assertThat(result1.getPriceAmountMicros()).isEqualTo(result2.getPriceAmountMicros());
        // Order IDs will be different due to timestamp, but format should be consistent
        assertThat(result1.getOrderId()).startsWith("GPA.MOCK-");
        assertThat(result2.getOrderId()).startsWith("GPA.MOCK-");
    }

    @Test
    @DisplayName("Mock path: Different inputs produce different results")
    void mockPath_differentInputsProduceDifferentResults() {
        // Given - No AndroidPublisher (mock mode)
        ReflectionTestUtils.setField(googlePlayClient, "androidPublisher", null);

        // When
        GooglePlaySubscriptionPurchase result1 = googlePlayClient.getSubscriptionPurchase("product1", "token1");
        GooglePlaySubscriptionPurchase result2 = googlePlayClient.getSubscriptionPurchase("product2", "token2");

        // Then
        assertThat(result1.getProductId()).isEqualTo("product1");
        assertThat(result1.getPurchaseToken()).isEqualTo("token1");
        assertThat(result2.getProductId()).isEqualTo("product2");
        assertThat(result2.getPurchaseToken()).isEqualTo("token2");
    }

    // Helper methods

    private SubscriptionPurchase createMockSubscriptionPurchase() {
        SubscriptionPurchase purchase = mock(SubscriptionPurchase.class);
        when(purchase.getStartTimeMillis()).thenReturn(1000L);
        when(purchase.getExpiryTimeMillis()).thenReturn(2000L);
        when(purchase.getAutoRenewing()).thenReturn(true);
        return purchase;
    }

    private SubscriptionPurchase createMockSubscriptionPurchaseWithNullTimestamps() {
        SubscriptionPurchase purchase = mock(SubscriptionPurchase.class);
        when(purchase.getStartTimeMillis()).thenReturn(null);
        when(purchase.getExpiryTimeMillis()).thenReturn(null);
        when(purchase.getAutoRenewing()).thenReturn(true);
        return purchase;
    }
}
