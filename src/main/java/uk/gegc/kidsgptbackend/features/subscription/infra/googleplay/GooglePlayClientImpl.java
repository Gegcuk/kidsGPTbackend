package uk.gegc.kidsgptbackend.features.subscription.infra.googleplay;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
@ConditionalOnProperty(value = "app.subscriptions.mock-google-play", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class GooglePlayClientImpl implements GooglePlayClient {

    @Value("${google.play.service-account-key:}")
    private String serviceAccountKey;

    @Value("${google.play.credentials-file:}")
    private String credentialsFile;

    @Value("${google.play.package-name:}")
    private String packageName;

    @Value("${google.play.application-name:KidsGPT}")
    private String applicationName;

    private AndroidPublisher androidPublisher;

    @PostConstruct
    public void initializeAndroidPublisher() {
        try {
            GoogleCredentials credentials = null;

            // Try credentials file first (if provided)
            if (StringUtils.hasText(credentialsFile)) {
                Path credentialsPath = Paths.get(credentialsFile);
                if (Files.isRegularFile(credentialsPath) && Files.isReadable(credentialsPath)) {
                    log.info("Loading Google Play credentials from file: {}", credentialsFile);
                    try (InputStream inputStream = Files.newInputStream(credentialsPath)) {
                        credentials = GoogleCredentials
                                .fromStream(inputStream)
                                .createScoped(Collections.singleton("https://www.googleapis.com/auth/androidpublisher"));
                    } catch (IOException e) {
                        log.warn("Failed to read Google Play credentials file {}. Falling back to service account key if present.",
                                credentialsFile, e);
                    }
                } else {
                    log.warn("Google Play credentials file not found or not readable: {}", credentialsFile);
                }
            }

            if (credentials == null && StringUtils.hasText(serviceAccountKey)) {
                // Fall back to service account key from env var
                log.info("Loading Google Play credentials from service account key");
                try (InputStream inputStream = new ByteArrayInputStream(serviceAccountKey.getBytes(StandardCharsets.UTF_8))) {
                    credentials = GoogleCredentials
                            .fromStream(inputStream)
                            .createScoped(Collections.singleton("https://www.googleapis.com/auth/androidpublisher"));
                }
            }

            if (credentials == null) {
                log.warn("Google Play service account key or credentials file not configured. Using mock implementation.");
                return;
            }

            // Build Android Publisher service
            this.androidPublisher = new AndroidPublisher.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(applicationName)
                    .build();

            log.info("Google Play Android Publisher API initialized successfully");
        } catch (GeneralSecurityException | IOException e) {
            log.error("Failed to initialize Google Play Android Publisher API", e);
            throw new RuntimeException("Failed to initialize Google Play API", e);
        }
    }

    @Override
    public GooglePlaySubscriptionPurchase getSubscriptionPurchase(String productId, String purchaseToken) {
        if (androidPublisher == null) {
            log.warn("Android Publisher not initialized, returning mock data");
            return createMockPurchase(productId, purchaseToken);
        }

        try {
            log.info("Getting subscription purchase for product {} with token ****", productId);

            SubscriptionPurchase purchase = androidPublisher.purchases()
                    .subscriptions()
                    .get(packageName, productId, purchaseToken)
                    .execute();

            return mapToGooglePlaySubscriptionPurchase(purchase, productId, purchaseToken);

        } catch (IOException e) {
            log.error("Failed to get subscription purchase for product {} with token ****", productId, e);
            throw new RuntimeException("Failed to get subscription purchase from Google Play", e);
        }
    }

    @Override
    public boolean verifyPurchaseToken(String productId, String purchaseToken) {
        try {
            GooglePlaySubscriptionPurchase purchase = getSubscriptionPurchase(productId, purchaseToken);
            boolean isValid = purchase != null && purchase.isPurchased() && !purchase.isExpired();
            
            log.info("Purchase token verification for product {} with token ****: {}", 
                    productId, isValid ? "VALID" : "INVALID");
            
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying purchase token **** for product {}", productId, e);
            return false;
        }
    }

    @Override
    public void acknowledgeSubscription(String productId, String purchaseToken, String developerPayload) {
        if (androidPublisher == null) {
            log.warn("Android Publisher not initialized, skipping acknowledgment");
            return;
        }

        try {
            log.info("Acknowledging subscription for product {} with token ****", productId);

            com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest acknowledgeRequest = 
                    new com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest()
                            .setDeveloperPayload(developerPayload);

            androidPublisher.purchases()
                    .subscriptions()
                    .acknowledge(packageName, productId, purchaseToken, acknowledgeRequest)
                    .execute();

            log.info("Successfully acknowledged subscription purchase for product {} with token ****", 
                    productId);

        } catch (IOException e) {
            log.error("Failed to acknowledge subscription for product {} with token ****", 
                    productId, e);
            throw new RuntimeException("Failed to acknowledge subscription", e);
        }
    }

    private GooglePlaySubscriptionPurchase mapToGooglePlaySubscriptionPurchase(
            SubscriptionPurchase purchase, String productId, String purchaseToken) {
        
        GooglePlaySubscriptionPurchase result = new GooglePlaySubscriptionPurchase();
        result.setPurchaseToken(purchaseToken);
        result.setProductId(productId);
        result.setPackageName(packageName);
        
        if (purchase.getStartTimeMillis() != null) {
            result.setStartTimeMillis(purchase.getStartTimeMillis());
        }
        
        if (purchase.getExpiryTimeMillis() != null) {
            result.setExpiryTimeMillis(purchase.getExpiryTimeMillis());
        }
        
        result.setAutoRenewing(purchase.getAutoRenewing());
        
        // Map purchase state - using safe method access
        Integer purchaseState = getMethodSafely(purchase, "getPurchaseState", Integer.class);
        if (purchaseState != null) {
            switch (purchaseState) {
                case 0:
                    result.setPurchaseState("PURCHASED");
                    break;
                case 1:
                    result.setPurchaseState("CANCELED");
                    break;
                default:
                    result.setPurchaseState("UNKNOWN");
                    break;
            }
        }
        
        // Map acknowledgment state - using safe method access
        Integer acknowledgmentState = getMethodSafely(purchase, "getAcknowledgementState", Integer.class);
        if (acknowledgmentState != null) {
            switch (acknowledgmentState) {
                case 0:
                    result.setAcknowledgementState("NOT_ACKNOWLEDGED");
                    break;
                case 1:
                    result.setAcknowledgementState("ACKNOWLEDGED");
                    break;
                default:
                    result.setAcknowledgementState("UNKNOWN");
                    break;
            }
        }
        
        // Set other fields - using safe method access
        result.setKind(getMethodSafely(purchase, "getKind", String.class));
        result.setRegionCode(getMethodSafely(purchase, "getRegionCode", String.class));
        result.setSubscriptionId(getMethodSafely(purchase, "getLinkedPurchaseToken", String.class));
        result.setLinkedPurchaseToken(getMethodSafely(purchase, "getLinkedPurchaseToken", String.class));
        
        // Handle price amount micros safely
        Object priceAmountMicros = getMethodSafely(purchase, "getPriceAmountMicros", Object.class);
        result.setPriceAmountMicros(priceAmountMicros != null ? priceAmountMicros.toString() : null);
        
        result.setPriceCurrencyCode(getMethodSafely(purchase, "getPriceCurrencyCode", String.class));
        result.setCountryCode(getMethodSafely(purchase, "getCountryCode", String.class));
        result.setDeveloperPayload(getMethodSafely(purchase, "getDeveloperPayload", String.class));
        result.setOrderId(getMethodSafely(purchase, "getOrderId", String.class));
        
        return result;
    }

    private GooglePlaySubscriptionPurchase createMockPurchase(String productId, String purchaseToken) {
        log.warn("Creating mock purchase data for product {} with token ****", productId);
        
        GooglePlaySubscriptionPurchase purchase = new GooglePlaySubscriptionPurchase();
        purchase.setPurchaseToken(purchaseToken);
        purchase.setProductId(productId);
        purchase.setStartTimeMillis(System.currentTimeMillis() - 86400000); // 1 day ago
        purchase.setExpiryTimeMillis(System.currentTimeMillis() + 2592000000L); // 30 days from now
        purchase.setAutoRenewing(true);
        purchase.setPurchaseState("PURCHASED");
        purchase.setAcknowledgementState("ACKNOWLEDGED");
        purchase.setPackageName(packageName);
        // Generate deterministic order ID based on productId and purchaseToken
        String orderIdInput = productId + ":" + purchaseToken;
        int hashCode = Math.abs(orderIdInput.hashCode());
        purchase.setOrderId("GPA.MOCK-" + hashCode);
        purchase.setPriceCurrencyCode("GBP");
        purchase.setPriceAmountMicros("4990000"); // £4.99 in micros
        
        return purchase;
    }
    
    /**
     * Safely invoke a method on an object using reflection.
     * Returns null if the method doesn't exist or throws an exception.
     */
    @SuppressWarnings("java:S3011") // Reflection needed for Google API compatibility
    private <T> T getMethodSafely(Object obj, String methodName, Class<T> returnType) {
        try {
            Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            return returnType.cast(result);
        } catch (Exception e) {
            log.debug("Method {} not available or failed: {}", methodName, e.getMessage());
            return null;
        }
    }
}
