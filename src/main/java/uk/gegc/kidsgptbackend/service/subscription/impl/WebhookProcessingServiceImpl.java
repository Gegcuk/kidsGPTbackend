package uk.gegc.kidsgptbackend.service.subscription.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.repository.subscription.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;
import uk.gegc.kidsgptbackend.service.subscription.WebhookProcessingService;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookProcessingServiceImpl implements WebhookProcessingService {

    private final ObjectMapper objectMapper;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final GooglePlayClient googlePlayClient;

    @Value("${google.play.webhook.audience:}")
    private String googlePlayAudience;
    
    @Value("${google.play.package-name:}")
    private String packageName;
    
    @Value("${google.play.service-account-email:}")
    private String expectedServiceAccountEmail;

    // Cache for Google's public keys with expiry
    private final Map<String, RSAPublicKey> publicKeyCache = new ConcurrentHashMap<>();
    private volatile Instant cacheExpiryTime = Instant.EPOCH;
    private static final long DEFAULT_CACHE_DURATION_SECONDS = 3600; // 1 hour default
    private final HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
    
    // Google's public key endpoints
    private static final String GOOGLE_CERTS_V1 = "https://www.googleapis.com/oauth2/v1/certs";
    private static final String GOOGLE_CLOUD_CERTS_URL = "https://www.googleapis.com/robot/v1/metadata/x509/cloud-iam@system.gserviceaccount.com";
    
    @PostConstruct
    public void initializePublicKeys() {
        try {
            refreshPublicKeys();
            log.info("Google public keys initialized successfully");
        } catch (Exception e) {
            log.warn("Failed to initialize Google public keys, JWT verification may fail: {}", e.getMessage());
        }
    }

    // Google Play webhook processing
    @Override
    public boolean verifyGooglePlaySignature(String authorization, String payload) {
        log.info("Verifying Google Play webhook signature");
        
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.warn("Invalid authorization header: missing Bearer token");
            return false;
        }
        
        String token = authorization.substring(7); // Remove "Bearer " prefix
        
        try {
            // Decode JWT without verification to get header
            DecodedJWT jwt = JWT.decode(token);
            String keyId = jwt.getKeyId();
            
            if (keyId == null) {
                log.warn("JWT token missing key ID (kid)");
                return false;
            }
            
            // Get the public key for verification
            RSAPublicKey publicKey = getPublicKey(keyId);
            if (publicKey == null) {
                log.warn("Could not find public key for key ID: {}", keyId);
                return false;
            }
            
            // Verify the JWT signature
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer("https://accounts.google.com", "accounts.google.com")
                    .build();
            
            DecodedJWT verifiedJWT = verifier.verify(token);
            
            // Additional validation
            if (!validateJWTClaims(verifiedJWT)) {
                log.warn("JWT claims validation failed");
                return false;
            }
            
            log.info("Google Play webhook signature verified successfully");
            return true;
            
        } catch (JWTVerificationException e) {
            log.error("JWT signature verification failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error verifying Google Play webhook signature", e);
            return false;
        }
    }
    
    private boolean validateJWTClaims(DecodedJWT jwt) {
        try {
            // Check expiration
            if (jwt.getExpiresAt() == null || jwt.getExpiresAt().before(java.util.Date.from(Instant.now()))) {
                log.warn("JWT token is expired");
                return false;
            }
            
            // Check issued at time (not too far in the future)
            if (jwt.getIssuedAt() != null && jwt.getIssuedAt().after(java.util.Date.from(Instant.now().plusSeconds(300)))) {
                log.warn("JWT token issued too far in the future");
                return false;
            }
            
            // Validate audience if configured (optional for local testing)
            boolean hasAudCfg = org.springframework.util.StringUtils.hasText(googlePlayAudience);
            if (hasAudCfg && (jwt.getAudience() == null || !jwt.getAudience().contains(googlePlayAudience))) {
                log.warn("Invalid aud: {}", jwt.getAudience());
                return false;
            }
            
            // Validate subject (should be a service account email)
            String subject = jwt.getSubject();
            if (subject == null || !subject.contains("@") || !subject.contains("gserviceaccount.com")) {
                log.warn("Invalid JWT subject: {}", subject);
                return false;
            }
            
            // Optionally validate email claim against expected service account
            if (org.springframework.util.StringUtils.hasText(expectedServiceAccountEmail)) {
                String emailClaim = jwt.getClaim("email") != null ? jwt.getClaim("email").asString() : null;
                if (!expectedServiceAccountEmail.equals(emailClaim)) {
                    log.warn("JWT email claim mismatch - expected: {}, got: {}", expectedServiceAccountEmail, emailClaim);
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error validating JWT claims", e);
            return false;
        }
    }
    
    private RSAPublicKey getPublicKey(String keyId) {
        // Check if cache is expired
        if (Instant.now().isAfter(cacheExpiryTime)) {
            log.debug("Certificate cache expired, refreshing");
            try {
                refreshPublicKeys();
            } catch (Exception e) {
                log.error("Failed to refresh expired public keys", e);
            }
        }
        
        // Check cache for key
        RSAPublicKey cachedKey = publicKeyCache.get(keyId);
        if (cachedKey != null) {
            return cachedKey;
        }
        
        // Key not found, try refreshing once more
        try {
            refreshPublicKeys();
            return publicKeyCache.get(keyId);
        } catch (Exception e) {
            log.error("Failed to refresh public keys", e);
            return null;
        }
    }
    
    private void refreshPublicKeys() throws IOException {
        log.debug("Refreshing Google public keys");
        
        // Try both Google certificate endpoints
        Map<String, String> certificates = new HashMap<>();
        
        try {
            Map<String, String> googleCerts = fetchCertificates(GOOGLE_CERTS_V1);
            certificates.putAll(googleCerts);
        } catch (Exception e) {
            log.debug("Failed to fetch from Google certs v1 URL: {}", e.getMessage());
        }
        
        try {
            Map<String, String> cloudCerts = fetchCertificates(GOOGLE_CLOUD_CERTS_URL);
            certificates.putAll(cloudCerts);
        } catch (Exception e) {
            log.debug("Failed to fetch from Google Cloud certs URL: {}", e.getMessage());
        }
        
        if (certificates.isEmpty()) {
            throw new IOException("Could not fetch any certificates from Google");
        }
        
        // Convert certificates to RSA public keys atomically
        Map<String, RSAPublicKey> freshKeys = new HashMap<>();
        for (Map.Entry<String, String> entry : certificates.entrySet()) {
            try {
                RSAPublicKey publicKey = parsePublicKey(entry.getValue());
                freshKeys.put(entry.getKey(), publicKey);
            } catch (Exception e) {
                log.warn("Failed to parse public key for keyId {}: {}", entry.getKey(), e.getMessage());
            }
        }
        
        // Replace cache atomically to avoid mixed old/new keys
        publicKeyCache.clear();
        publicKeyCache.putAll(freshKeys);
        
        log.debug("Refreshed {} public keys", publicKeyCache.size());
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, String> fetchCertificates(String url) throws IOException {
        HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(url));
        // Add timeout for robustness
        request.setConnectTimeout(10000); // 10 seconds
        request.setReadTimeout(10000); // 10 seconds
        
        com.google.api.client.http.HttpResponse response = request.execute();
        String responseBody = response.parseAsString();
        
        // Try to parse Cache-Control header for expiry
        String cacheControl = response.getHeaders().getCacheControl();
        updateCacheExpiry(cacheControl);
        
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        Map<String, String> certificates = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : responseMap.entrySet()) {
            if (entry.getValue() instanceof String) {
                certificates.put(entry.getKey(), (String) entry.getValue());
            }
        }
        
        return certificates;
    }
    
    private void updateCacheExpiry(String cacheControl) {
        long cacheDuration = DEFAULT_CACHE_DURATION_SECONDS;
        
        if (cacheControl != null) {
            try {
                // Parse max-age from Cache-Control header
                String[] parts = cacheControl.split(",");
                for (String part : parts) {
                    part = part.trim();
                    if (part.startsWith("max-age=")) {
                        cacheDuration = Long.parseLong(part.substring(8));
                        break;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse Cache-Control header: {}", cacheControl);
            }
        }
        
        cacheExpiryTime = Instant.now().plusSeconds(cacheDuration);
        log.debug("Certificate cache will expire at: {}", cacheExpiryTime);
    }
    
    private RSAPublicKey parsePublicKey(String certificateString) 
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        
        // Remove certificate headers and footers
        String publicKeyPEM = certificateString
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        
        // Decode the certificate
        byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
        
        try {
            // Try parsing as X.509 certificate first
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) cf.generateCertificate(
                    new java.io.ByteArrayInputStream(decoded));
            return (RSAPublicKey) cert.getPublicKey();
        } catch (Exception e) {
            // If that fails, try parsing as public key directly
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(spec);
        }
    }

    @Override
    public String extractGooglePlayEventId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode message = root.get("message");
            if (message != null) {
                return message.get("messageId").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting Google Play event ID", e);
            return null;
        }
    }

    @Override
    public String extractGooglePlayEventType(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode message = root.get("message");
            if (message != null && message.has("data")) {
                String data = message.get("data").asText();
                byte[] decodedData = Base64.getDecoder().decode(data);
                JsonNode dataNode = objectMapper.readTree(decodedData);
                
                // Check for subscription notification
                if (dataNode.has("subscriptionNotification")) {
                    JsonNode notification = dataNode.get("subscriptionNotification");
                    int notificationType = notification.get("notificationType").asInt();
                    
                    return switch (notificationType) {
                        case 1 -> "SUBSCRIPTION_RECOVERED";
                        case 2 -> "SUBSCRIPTION_RENEWED";
                        case 3 -> "SUBSCRIPTION_CANCELED";
                        case 4 -> "SUBSCRIPTION_PURCHASED";
                        case 5 -> "SUBSCRIPTION_ON_HOLD";
                        case 6 -> "SUBSCRIPTION_IN_GRACE_PERIOD";
                        case 7 -> "SUBSCRIPTION_RESTARTED";
                        case 8 -> "SUBSCRIPTION_PRICE_CHANGE_CONFIRMED";
                        case 9 -> "SUBSCRIPTION_DEFERRED";
                        case 10 -> "SUBSCRIPTION_PAUSED";
                        case 11 -> "SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED";
                        case 12 -> "SUBSCRIPTION_REVOKED";
                        case 13 -> "SUBSCRIPTION_EXPIRED";
                        default -> "UNKNOWN_NOTIFICATION_TYPE_" + notificationType;
                    };
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error extracting Google Play event type", e);
            return null;
        }
    }

    @Override
    // Remove @Transactional from orchestration method - keep it only on the update method
    public void processGooglePlayWebhook(String eventId, String eventType, String payload) {
        try {
            log.info("Processing Google Play webhook: {} - {}", eventId, eventType);
            
            JsonNode root = objectMapper.readTree(payload);
            JsonNode message = root.get("message");
            if (message != null && message.has("data")) {
                String data = message.get("data").asText();
                byte[] decodedData = Base64.getDecoder().decode(data);
                JsonNode dataNode = objectMapper.readTree(decodedData);
                
                if (dataNode.has("subscriptionNotification")) {
                    // Validate package name to prevent cross-app mixups
                    String eventPackageName = dataNode.path("packageName").asText();
                    if (!packageName.equals(eventPackageName)) {
                        log.warn("Package name mismatch - expected: {}, got: {}, ignoring event", 
                                packageName, eventPackageName);
                        return;
                    }
                    processGooglePlaySubscriptionNotification(dataNode.get("subscriptionNotification"), eventType);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Google Play webhook: {}", eventId, e);
            throw new RuntimeException("Failed to process Google Play webhook", e);
        }
    }

    private void processGooglePlaySubscriptionNotification(JsonNode notification, String eventType) {
        String productId = notification.get("subscriptionId").asText(); // This is actually the product ID
        String purchaseToken = notification.get("purchaseToken").asText();
        
        // Fetch authoritative state from Google Play API BEFORE starting transaction
        GooglePlaySubscriptionPurchase googlePurchase = null;
        try {
            googlePurchase = googlePlayClient.getSubscriptionPurchase(productId, purchaseToken);
        } catch (Exception e) {
            log.error("Failed to fetch Google Play subscription data for token: ****", e);
        }
        
        // Now update the database in a short transaction
        updateSubscriptionFromGoogleData(purchaseToken, eventType, googlePurchase);
    }

    @Transactional
    void updateSubscriptionFromGoogleData(String purchaseToken, String eventType, GooglePlaySubscriptionPurchase googlePurchase) {
        // Find subscription by purchaseToken (the unique identifier)
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByPaymentProviderAndExternalSubscriptionId(
                        UserSubscription.PaymentProvider.GOOGLE_PLAY, purchaseToken);
        
        if (subscriptionOpt.isEmpty()) {
            log.warn("Subscription not found for Google Play purchase token: ****");
            return;
        }
        
        UserSubscription subscription = subscriptionOpt.get();
        
        if (googlePurchase != null) {
            // Update subscription with fresh data from Google
            subscription.setStatus(mapGooglePlayStatus(googlePurchase));
            
            // Guard against nullable timestamps
            Long startMs = googlePurchase.getStartTimeMillis();
            Long endMs = googlePurchase.getExpiryTimeMillis();
            
            if (startMs != null) {
                subscription.setCurrentPeriodStart(Instant.ofEpochMilli(startMs));
            }
            if (endMs != null) {
                Instant end = Instant.ofEpochMilli(endMs);
                subscription.setCurrentPeriodEnd(end);
                subscription.setNextBillingDate(end);
            }
            
            subscription.setAutoRenew(Boolean.TRUE.equals(googlePurchase.getAutoRenewing()));
            subscription.setProviderStatusRaw(googlePurchase.getPurchaseState());
            
            log.info("Updated subscription {} for Google Play event: {} with fresh API data", 
                    subscription.getId(), eventType);
        } else {
            // Still update the raw event type for debugging
            subscription.setProviderStatusRaw(eventType);
            log.info("Updated subscription {} for Google Play event: {} (API call failed)", 
                    subscription.getId(), eventType);
        }
        
        // Handle specific event types
        switch (eventType) {
            case "SUBSCRIPTION_CANCELED" -> {
                subscription.setCancelledAt(Instant.now());
            }
            case "SUBSCRIPTION_IN_GRACE_PERIOD" -> {
                subscription.setGracePeriodEnd(Instant.now().plusSeconds(3 * 24 * 60 * 60)); // 3 days
            }
            case "SUBSCRIPTION_PAUSED" -> {
                subscription.setPausedAt(Instant.now());
            }
            case "SUBSCRIPTION_REVOKED" -> {
                subscription.setCancelledAt(Instant.now());
            }
        }
        
        userSubscriptionRepository.save(subscription);
    }
    
    private UserSubscription.SubscriptionStatus mapGooglePlayStatus(GooglePlaySubscriptionPurchase purchase) {
        if (purchase.isPurchased() && !purchase.isExpired()) {
            return UserSubscription.SubscriptionStatus.ACTIVE;
        } else if (purchase.isCanceled()) {
            return UserSubscription.SubscriptionStatus.CANCELLED;
        } else if (purchase.isExpired()) {
            return UserSubscription.SubscriptionStatus.EXPIRED;
        } else {
            return UserSubscription.SubscriptionStatus.INCOMPLETE;
        }
    }

}
